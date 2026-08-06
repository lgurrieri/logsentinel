---
applyTo: ".github/workflows/**, **/Dockerfile*, **/docker-compose*.yml, .github/dependabot.yml, .github/CODEOWNERS, .github/pull_request_template.md"
---

# Instrucciones: DevSecOps LogSentinel

## Stack exacto
GitHub Actions · Docker multi-stage (Java 25, eclipse-temurin:25-jre-noble) · Trivy · Gitleaks · OWASP Dependency-Check

---

## GitHub Actions — reglas no negociables

### Permisos mínimos (Principle of Least Privilege)
Todo workflow DEBE declarar `permissions:` en el nivel raíz con el mínimo necesario.
El default de GitHub es `write-all` si el repositorio no lo restringe — eso es inaceptable.

```yaml
# ✅ Correcto — permisos declarados explícitamente
permissions:
  contents: read

jobs:
  security-scan:
    permissions:
      contents: read
      security-events: write  # solo si se sube SARIF
```

```yaml
# ❌ Incorrecto — omitir permissions hereda write-all
jobs:
  build:
    runs-on: ubuntu-latest
    steps: ...
```

### Pinning de Actions a SHA (anti supply-chain attack)
NUNCA usar tags mutables (`@v4`, `@main`, `@latest`) en Actions de terceros.
El tag puede ser redirigido a código malicioso sin aviso.

```yaml
# ✅ Correcto — SHA inmutable
- uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683        # v4.2.2
- uses: actions/setup-java@3a4f6e1af504cf6a31d1d3c0f5e5e5e5e5e5e5e5       # v4.5.0
- uses: aquasecurity/trivy-action@915b19bbe73b92a6cf82a1bc12b087c9a19a5fe  # v0.28.0
- uses: gitleaks/gitleaks-action@cb7149a9b57195b609c63e8518d2c6ef8e1c5b44  # v2.3.4
```

```yaml
# ❌ Incorrecto — tag mutable
- uses: actions/checkout@v4
- uses: aquasecurity/trivy-action@master
```

Excepción única: Actions definidas dentro del mismo repositorio (`./`).

### Timeouts obligatorios
Todo job DEBE definir `timeout-minutes`. Sin esto, un runner bloqueado consume créditos indefinidamente.

```yaml
jobs:
  test:
    timeout-minutes: 15   # ajustar por job; nunca omitir
    runs-on: ubuntu-latest
```

### Orden de ejecución — security-first
Los scans de seguridad SIEMPRE van en el primer job (`needs:` vacío).
El build y deploy dependen de que el security job haya pasado.

```yaml
jobs:
  security-scan:       # sin needs — corre primero
    ...
  test:
    needs: security-scan
  build-image:
    needs: test
  deploy:
    needs: build-image
```

### Inyección de secretos — solo en runtime, nunca en build
Los secretos NUNCA se pasan como `--build-arg` de Docker (quedan en el layer cache y en `docker history`).

```yaml
# ✅ Correcto — secreto inyectado en runtime
- run: docker run -e API_KEY="${{ secrets.OPENAI_API_KEY }}" logsentinel-backend

# ❌ Incorrecto — secreto en layer history
- run: docker build --build-arg API_KEY="${{ secrets.OPENAI_API_KEY }}" .
```

### Prohibiciones absolutas en workflows
- `curl ... | bash` o `wget ... | sh` — ejecución de código arbitrario desde red
- `run: echo "${{ secrets.* }}"` — exposición de secreto en logs
- `continue-on-error: true` en steps de security scan — silencia fallos críticos
- `if: always()` en steps que corren código — bypass de security gates
- `ACTIONS_RUNNER_DEBUG: true` en workflows de producción — expone variables de entorno
- `--privileged` en cualquier `docker run`

---

## Docker — reglas no negociables

### Multi-stage OBLIGATORIO — imagen final solo con JRE

```dockerfile
# ── Etapa 1: Build ──────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Etapa 2: Production ─────────────────────────────────────────────
FROM eclipse-temurin:25-jre-noble AS production
WORKDIR /app
# Usuario non-root con UID fijo para reproducibilidad
RUN useradd --system --no-create-home --uid 1001 logsentineluser
USER logsentineluser
COPY --from=build --chown=logsentineluser:logsentineluser /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# ── Etapa 3: Dev (herramientas de diagnóstico — nunca en prod) ───────
FROM production AS dev
USER root
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
USER logsentineluser
```

### Prohibiciones en Dockerfile

| Patrón prohibido | Razón | Alternativa |
|---|---|---|
| `USER root` en etapa production | Privilegio excesivo | `USER logsentineluser` (uid 1001) |
| `ENV API_KEY=valor_real` | Queda en `docker history` | Inyectar en runtime via `docker run -e` |
| `FROM ...:latest` | No reproducible, puede cambiar | Tag semántico: `eclipse-temurin:25-jre-noble` |
| `ADD https://...` | Descarga arbitraria sin verificación | `COPY` + `RUN curl` con checksum |
| `RUN apt-get install` sin `rm -rf /var/lib/apt/lists/*` | Aumenta superficie de ataque | Limpiar siempre en el mismo `RUN` |
| `COPY . .` antes de `dependency:go-offline` | Invalida cache de deps | `COPY pom.xml .` → go-offline → `COPY src ./src` |

### Healthcheck obligatorio en todos los servicios de compose

```yaml
# backend
healthcheck:
  test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s

# db
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
  interval: 10s
  retries: 5
  start_period: 30s
```

---

## docker-compose — reglas de seguridad

```yaml
services:
  backend:
    security_opt:
      - no-new-privileges:true   # previene escalada de privilegios via setuid
    cap_drop:
      - ALL                      # eliminar todas las capabilities de Linux
    cap_add:
      - NET_BIND_SERVICE         # solo re-agregar las estrictamente necesarias
    # read_only: true es el objetivo — documentar si no es posible aún

  db:
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    # Puerto 5432 NUNCA expuesto en staging/prod
    # Solo en docker-compose.dev.yml añadir: ports: ["5432:5432"]
```

---

## Herramientas de seguridad — uso obligatorio en CI

### Gitleaks (secretos en código) — corre PRIMERO, antes de build
```yaml
- name: Scan for secrets (Gitleaks)
  uses: gitleaks/gitleaks-action@cb7149a9b57195b609c63e8518d2c6ef8e1c5b44  # v2.3.4
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### OWASP Dependency-Check (CVEs en dependencias Maven)
```yaml
- name: OWASP Dependency Check
  run: |
    cd backend
    mvn dependency-check:check \
      -Dnvd.api.key=${{ secrets.NVD_API_KEY }} \
      -DfailBuildOnCVSS=7 \
      -DsuppressionFile=owasp-suppressions.xml \
      -q
```
`failBuildOnCVSS=7` bloquea en HIGH (7.0+) y CRITICAL (9.0+). Nunca bajar este umbral.

### Trivy (vulnerabilidades en imagen Docker)
```yaml
- name: Scan Docker image (Trivy)
  uses: aquasecurity/trivy-action@915b19bbe73b92a6cf82a1bc12b087c9a19a5fe  # v0.28.0
  with:
    image-ref: logsentinel-backend:${{ github.sha }}
    severity: 'CRITICAL,HIGH'
    exit-code: '1'         # OBLIGATORIO — sin esto el step nunca falla
    ignore-unfixed: true   # ignorar CVEs sin parche disponible
    format: 'sarif'
    output: 'trivy-results.sarif'
```

### npm audit (CVEs en dependencias frontend)
```yaml
- name: npm audit
  run: |
    cd frontend
    npm audit --audit-level=high
```

---

## Matriz de secretos — LogSentinel

Ver skill `provision-logsentinel-env` para la tabla completa por entorno.

**Regla de oro de verificación automática:**
Si cualquier valor literal aparece en archivos de este directorio fuera de
`${VARIABLE_NAME}` (shell) o `${{ secrets.NAME }}` (Actions), es una violación bloqueante:

```bash
# Verificar que no hay secretos hardcodeados en workflows y compose
grep -rE "(password|api.key|secret|token)\s*[:=]\s*['\"][^$\{]" \
  .github/workflows/ docker-compose*.yml 2>/dev/null
# Resultado esperado: 0 matches
```

---

## Modelo de ramas y entornos

```
feature/*  →  CI: Gitleaks + OWASP + unit tests
develop    →  CI completo + build imagen + Trivy + deploy automático a staging
main       →  CI completo + build imagen + Trivy + deploy a prod CON aprobación manual (GitHub Environment)
```

Los environments `staging` y `production` en GitHub DEBEN tener:
- Reviewers requeridos (mínimo 1 para staging, 2 para prod)
- Branch restriction: solo `develop` puede deployar a staging, solo `main` a prod
- Wait timer: 0min para staging, 5min para prod (ventana de cancelación)
