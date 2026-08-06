---
name: logsentinel-devsecops
description: >
  Bootstraps y mantiene la infraestructura DevSecOps de LogSentinel: CI/CD workflows,
  Dependabot, CODEOWNERS, PR template, Dockerfile, docker-compose.
  Usar cuando: "configurar CI/CD", "crear workflows", "setup DevSecOps",
  "crear Dockerfile", "configurar Dependabot", "preparar entorno {dev|staging|prod}",
  "bootstrap infra", "ejecutar ticket LOG-CORE-INFRA-00".
---

# Agent: logsentinel-devsecops

## Misión
Materializar y mantener toda la infraestructura DevSecOps de LogSentinel.
Leer las skills de conocimiento y generar los artefactos ejecutables reales.
Entregar infraestructura que pase los checks de `verify-clean-arch` sección DevSecOps.

---

## Proceso de ejecución (en orden estricto)

### Paso 1: Leer contexto
- Leer `.github/copilot-instructions-devsecops.md` — reglas no negociables de seguridad
- Leer skill `provision-logsentinel-env` — templates de entorno y matriz de secretos
- Ejecutar `git status` para ver qué artefactos ya existen vs qué hay que crear

### Paso 2: Identificar el objetivo del task

| Subobjetivo | Artefactos a crear |
|---|---|
| `bootstrap-ci` | `.github/workflows/ci.yml`, `deploy-staging.yml`, `deploy-prod.yml` |
| `setup-docker` | `backend/Dockerfile`, `docker-compose.yml`, `docker-compose.dev.yml`, `docker-compose.staging.yml` |
| `setup-dependabot` | `.github/dependabot.yml` |
| `setup-pr-hygiene` | `.github/pull_request_template.md`, `.github/CODEOWNERS` |
| `full-bootstrap` | Todos los anteriores |

### Paso 3: Generar artefactos

#### Para cada workflow — checklist antes de escribir el archivo:
- [ ] `permissions:` declarado en nivel raíz con mínimo necesario
- [ ] `timeout-minutes:` en cada job
- [ ] Todas las Actions de terceros pineadas a SHA (no @v4, no @main)
- [ ] Security scans con `needs:` vacío (corren primero)
- [ ] Build y deploy dependen de que security job haya pasado
- [ ] Sin `continue-on-error: true` en steps de seguridad
- [ ] Sin `run: echo "${{ secrets.*}}"` en ningún step
- [ ] Secretos solo via `${{ secrets.NAME }}`, nunca hardcodeados

#### Para Dockerfile — checklist antes de escribir:
- [ ] Multi-stage: `build` + `production` como mínimo
- [ ] `USER logsentineluser` (uid 1001) en etapa production — nunca `USER root`
- [ ] Sin `ENV` con valores secretos (solo nombres de variable: `ENV PORT=8080` es válido)
- [ ] `COPY --from=build --chown=logsentineluser:logsentineluser`
- [ ] Base image con tag semántico, no `:latest`
- [ ] `apt-get` + `rm -rf /var/lib/apt/lists/*` en el mismo `RUN`

#### Para docker-compose — checklist:
- [ ] `security_opt: [no-new-privileges:true]` en cada servicio
- [ ] `cap_drop: [ALL]` con re-adición explícita de caps necesarias
- [ ] Healthchecks en todos los servicios
- [ ] Puerto de BD solo expuesto en `docker-compose.dev.yml`
- [ ] Variables via `${ENV_VAR}`, sin valores literales de secretos

### Paso 4: Templates de referencia

#### `.github/workflows/ci.yml`
```yaml
name: CI — Security + Tests

on:
  push:
    branches: ['**']
  pull_request:
    branches: [main, develop]

permissions:
  contents: read

jobs:
  security-scan:
    name: Security Scan
    runs-on: ubuntu-latest
    timeout-minutes: 10
    permissions:
      contents: read
      security-events: write
    steps:
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683  # v4.2.2
        with:
          fetch-depth: 0  # gitleaks necesita historial completo

      - name: Scan for secrets (Gitleaks)
        uses: gitleaks/gitleaks-action@cb7149a9b57195b609c63e8518d2c6ef8e1c5b44  # v2.3.4
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - uses: actions/setup-java@3a4f6e1af504cf6a31d1d3c0f5e5e5e5e5e5e5e  # v4.5.0
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: 'maven'

      - name: OWASP Dependency Check (backend)
        run: |
          cd backend
          mvn dependency-check:check \
            -Dnvd.api.key=${{ secrets.NVD_API_KEY }} \
            -DfailBuildOnCVSS=7 \
            -q

      - name: npm audit (frontend)
        run: |
          cd frontend
          npm audit --audit-level=high

  test:
    name: Tests
    needs: security-scan
    runs-on: ubuntu-latest
    timeout-minutes: 15
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683  # v4.2.2

      - uses: actions/setup-java@3a4f6e1af504cf6a31d1d3c0f5e5e5e5e5e5e5e  # v4.5.0
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: 'maven'

      - name: Unit tests (sin Docker)
        run: mvn test -f backend/pom.xml -Dtest='!*IntegrationTest' -q

      - name: Integration tests (Testcontainers)
        run: mvn test -f backend/pom.xml -Dtest='*IntegrationTest' -q

      - name: Frontend build + tests
        run: |
          cd frontend
          npm ci
          npm run build
          npm test -- --run

  build-and-scan-image:
    name: Build + Scan Docker Image
    needs: test
    runs-on: ubuntu-latest
    timeout-minutes: 20
    if: github.ref == 'refs/heads/develop' || github.ref == 'refs/heads/main'
    permissions:
      contents: read
      security-events: write
    steps:
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683  # v4.2.2

      - name: Build Docker image (production target)
        run: |
          docker build \
            --target production \
            -t logsentinel-backend:${{ github.sha }} \
            backend/

      - name: Scan image for vulnerabilities (Trivy)
        uses: aquasecurity/trivy-action@915b19bbe73b92a6cf82a1bc12b087c9a19a5fe  # v0.28.0
        with:
          image-ref: logsentinel-backend:${{ github.sha }}
          severity: 'CRITICAL,HIGH'
          exit-code: '1'
          ignore-unfixed: true
          format: 'sarif'
          output: 'trivy-results.sarif'

      - name: Upload Trivy results to GitHub Security tab
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: 'trivy-results.sarif'
```

#### `.github/workflows/deploy-staging.yml`
```yaml
name: Deploy → Staging

on:
  push:
    branches: [develop]

permissions:
  contents: read

jobs:
  deploy:
    name: Deploy to Staging
    runs-on: ubuntu-latest
    timeout-minutes: 10
    environment: staging
    permissions:
      contents: read
    steps:
      - name: Trigger deploy (Render webhook)
        run: |
          curl -s -f -X POST "${{ secrets.RENDER_STAGING_WEBHOOK_URL }}"
```

#### `.github/workflows/deploy-prod.yml`
```yaml
name: Deploy → Production

on:
  push:
    branches: [main]

permissions:
  contents: read

jobs:
  deploy:
    name: Deploy to Production
    runs-on: ubuntu-latest
    timeout-minutes: 10
    environment: production  # requiere aprobación manual configurada en GitHub
    permissions:
      contents: read
    steps:
      - name: Trigger deploy (Render webhook)
        run: |
          curl -s -f -X POST "${{ secrets.RENDER_PROD_WEBHOOK_URL }}"
```

#### `.github/dependabot.yml`
```yaml
version: 2
updates:
  # Dependencias Maven (backend)
  - package-ecosystem: "maven"
    directory: "/backend"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "09:00"
    open-pull-requests-limit: 5
    labels: ["dependencies", "backend"]
    ignore:
      # Spring Boot BOM gestiona estas versiones — no actualizar individualmente
      - dependency-name: "org.springframework.boot:*"
        update-types: ["version-update:semver-major"]

  # Dependencias npm (frontend)
  - package-ecosystem: "npm"
    directory: "/frontend"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "09:00"
    open-pull-requests-limit: 5
    labels: ["dependencies", "frontend"]

  # GitHub Actions
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "09:00"
    labels: ["dependencies", "ci/cd"]
```

#### `.github/CODEOWNERS`
```
# Owners por defecto para todo el repo
*                                   @lgurrieri

# Backend — revisión obligatoria en cambios de dominio y seguridad
/backend/src/main/java/com/logsentinel/domain/      @lgurrieri
/backend/src/main/java/com/logsentinel/application/ @lgurrieri
/.github/workflows/                                 @lgurrieri
/backend/Dockerfile                                 @lgurrieri
```

#### `.github/pull_request_template.md`
```markdown
## Descripción
<!-- Qué cambio hace este PR y por qué -->

## Ticket relacionado
<!-- LOG-US1-BE-02, LOG-CORE-INFRA-00, etc. -->

## Checklist DevSecOps (REQUERIDO)

### Arquitectura
- [ ] `verify-clean-arch` ejecutado — 0 violaciones (Checks 1–7)
- [ ] No hay imports de `infrastructure/*` en capas `domain/` o `application/`
- [ ] DTOs en `web/dto/` son `record`, no `class`

### Seguridad de código
- [ ] Sin credenciales hardcodeadas — todas via `${ENV_VAR}`
- [ ] Sin `dangerouslySetInnerHTML` en componentes React
- [ ] `ProcessBuilder` (si aplica): input validado antes de ejecutar
- [ ] `SseEmitter.complete()` en bloque `finally` (si aplica)

### Infraestructura (si el PR toca Docker, compose o workflows)
- [ ] Dockerfile: usuario non-root en etapa `production`
- [ ] docker-compose: `no-new-privileges:true` y `cap_drop: ALL`
- [ ] Workflows: Actions pineadas a SHA, `permissions:` declarado, `timeout-minutes:` presente
- [ ] Sin secretos en `ENV` layers del Dockerfile

### Tests
- [ ] `mvn test` pasa (backend) — incluye tests unitarios e integración
- [ ] `npm run build && npm test -- --run` pasa (frontend)
- [ ] Nuevas features tienen tests que cubren el comportamiento principal

### Variables de entorno
- [ ] Variables nuevas documentadas en la Matriz de Secretos (`provision-logsentinel-env`)
- [ ] `.env.example` actualizado si aplica
```

### Paso 5: Verificar ausencia de secretos en artefactos generados

```bash
# Ningún secreto hardcodeado en workflows o compose
grep -rE "(password|api.key|secret|token)\s*[:=]\s*['\"][^$\{]" \
  .github/workflows/ docker-compose*.yml 2>/dev/null
# Resultado esperado: 0 matches

# Confirmar que no hay ENV con secretos en Dockerfile
grep -nE "^ENV.*(KEY|PASSWORD|SECRET|TOKEN)" backend/Dockerfile 2>/dev/null
# Resultado esperado: 0 matches

# Confirmar usuario non-root en Dockerfile
grep -n "^USER" backend/Dockerfile
# Resultado esperado: al menos una línea USER logsentineluser
```

### Paso 6: Reporte final

```
=== DevSecOps Bootstrap — LogSentinel ===

Artefactos creados:
  - .github/workflows/ci.yml
  - .github/workflows/deploy-staging.yml
  - .github/workflows/deploy-prod.yml
  - .github/dependabot.yml
  - .github/pull_request_template.md
  - .github/CODEOWNERS
  - backend/Dockerfile
  - docker-compose.yml
  - docker-compose.dev.yml
  - docker-compose.staging.yml

Secretos requeridos en GitHub Secrets (configurar manualmente — NO en código):
  - NVD_API_KEY          → OWASP Dependency-Check (https://nvd.nist.gov/developers/request-an-api-key)
  - RENDER_STAGING_WEBHOOK_URL  → env: staging
  - RENDER_PROD_WEBHOOK_URL     → env: production

GitHub Environments a crear:
  - staging    → Reviewers: 1, Branch: develop
  - production → Reviewers: 2, Branch: main, Wait timer: 5min

Rama Git → Entorno:
  feature/* → CI (Gitleaks + OWASP + unit tests)
  develop   → CI completo + build imagen + Trivy + deploy automático a staging
  main      → CI completo + build imagen + Trivy + deploy a prod CON aprobación manual
```
