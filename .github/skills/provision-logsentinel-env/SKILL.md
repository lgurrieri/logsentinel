---
name: provision-logsentinel-env
description: >
  Provisions LogSentinel infrastructure for dev, staging, and prod following DevSecOps.
  Use when: "configurar entorno", "crear docker-compose", "preparar staging",
  "aprovisionar infraestructura", "crear CI/CD pipeline", "setup entorno nuevo".
---

# Skill: provision-logsentinel-env

## Propósito
Provisiona la infraestructura de LogSentinel para dev, staging y prod siguiendo DevSecOps.
Usar cuando: "configurar entorno X", "crear docker-compose", "preparar staging",
"aprovisionar infraestructura", "crear CI/CD pipeline", "setup entorno nuevo".

## Modelo de entornos

```
dev      → docker-compose.yml + docker-compose.dev.yml     + backend/.env.dev (gitignored)
staging  → docker-compose.yml + docker-compose.staging.yml + GitHub Secrets (env: staging)
prod     → docker-compose.yml + docker-compose.prod.yml    + Render Env Vars
```

## Archivos a generar

### `docker-compose.yml` (base — nunca usar solo)
Define la estructura de servicios sin valores concretos.

```yaml
services:
  db:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 10s
      retries: 5
      start_period: 30s

  ollama:
    image: ollama/ollama:latest
    volumes:
      - ollama_data:/root/.ollama
    healthcheck:
      test: ["CMD-SHELL", "ollama list || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

volumes:
  postgres_data:
  ollama_data:
```

### `docker-compose.dev.yml` (override desarrollo local)
```yaml
services:
  db:
    ports:
      - "5432:5432"    # expuesto solo en dev
  ollama:
    ports:
      - "11434:11434"  # expuesto solo en dev, para debug
  backend:
    build:
      context: ./backend
      target: dev      # stage con herramientas de debug del Dockerfile
    ports:
      - "8080:8080"
    env_file:
      - ./backend/.env.dev
    environment:
      SPRING_PROFILES_ACTIVE: dev,ollama   # Ollama por defecto — sin API key requerida
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${POSTGRES_DB}
      SPRING_AI_OLLAMA_BASE_URL: http://ollama:11434
    depends_on:
      db:
        condition: service_healthy
      ollama:
        condition: service_healthy
```

### `docker-compose.staging.yml`
```yaml
services:
  backend:
    build:
      context: ./backend
      target: production    # imagen final multi-stage
    environment:
      # AI_PROVIDER_PROFILE: "openai" (cloud, requiere SPRING_AI_OPENAI_API_KEY)
      #                    o "ollama" (self-hosted, sin secreto)
      SPRING_PROFILES_ACTIVE: staging,${AI_PROVIDER_PROFILE:-openai}
      SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL}
      SPRING_AI_OPENAI_API_KEY: ${SPRING_AI_OPENAI_API_KEY}
      SPRING_AI_OLLAMA_BASE_URL: ${SPRING_AI_OLLAMA_BASE_URL}
    restart: unless-stopped
```

### `backend/Dockerfile` (multi-stage, Java 25, non-root)
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
# Principio de privilegio mínimo — nunca root
RUN useradd --system --no-create-home --uid 1001 logsentineluser
USER logsentineluser
COPY --from=build --chown=logsentineluser:logsentineluser /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# ── Etapa 3: Dev (herramientas de diagnóstico) ───────────────────────
FROM production AS dev
USER root
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
USER logsentineluser
```

### `backend/src/main/resources/application-dev.yml`
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/logsentinel}
  jpa:
    show-sql: true
  # Proveedor de IA: activar junto al perfil de entorno, ej. SPRING_PROFILES_ACTIVE=dev,ollama
  # Config de ai.* vive en application-ollama.yml (default, sin secreto) / application-openai.yml (opcional)
logging:
  level:
    com.logsentinel: DEBUG
    org.springframework.ai: DEBUG
    org.hibernate.SQL: DEBUG
```

### `backend/src/main/resources/application-staging.yml`
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}    # obligatorio — falla si no está definida
  jpa:
    show-sql: false
  # Proveedor de IA: activar junto al perfil de entorno, ej. SPRING_PROFILES_ACTIVE=staging,openai
  # (cloud, requiere SPRING_AI_OPENAI_API_KEY) o SPRING_PROFILES_ACTIVE=staging,ollama (self-hosted)
logging:
  level:
    com.logsentinel: INFO
```

### `backend/src/main/resources/application-prod.yml`
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate    # NUNCA update/create en prod
  # Proveedor de IA: activar junto al perfil de entorno, ej. SPRING_PROFILES_ACTIVE=prod,openai
  # (cloud, requiere SPRING_AI_OPENAI_API_KEY) o SPRING_PROFILES_ACTIVE=prod,ollama (self-hosted)
logging:
  level:
    com.logsentinel: WARN
```

## GitHub Actions — Pipeline DevSecOps

### `.github/workflows/ci.yml` (todos los branches)
```yaml
name: CI — Security + Tests

on:
  push:
    branches: ['**']
  pull_request:
    branches: [main, develop]

jobs:
  security-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '25', distribution: 'temurin', cache: 'maven' }

      - name: OWASP Dependency Check (CVE en dependencias)
        run: mvn dependency-check:check -f backend/pom.xml -q
        # Falla si hay CVEs CRÍTICOS

      - name: Detect secrets in code (Gitleaks)
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

  test:
    needs: security-scan
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '25', distribution: 'temurin', cache: 'maven' }

      - name: Tests unitarios (sin Docker)
        run: mvn test -f backend/pom.xml -Dtest="!*IntegrationTest" -q

      - name: Tests de integración (Testcontainers — Docker requerido)
        run: mvn test -f backend/pom.xml -Dtest="*IntegrationTest" -q

  build-and-scan-image:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/develop' || github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4

      - name: Build imagen Docker (multi-stage)
        run: |
          docker build -t logsentinel-backend:${{ github.sha }} \
                       --target production backend/

      - name: Scan imagen Docker (Trivy — CVEs en OS + libs)
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: logsentinel-backend:${{ github.sha }}
          severity: 'CRITICAL,HIGH'
          exit-code: '1'    # falla si encuentra CVEs altos
```

### `.github/workflows/deploy-staging.yml` (rama develop)
```yaml
name: Deploy → Staging

on:
  push:
    branches: [develop]

jobs:
  deploy:
    needs: [security-scan, test, build-and-scan-image]
    runs-on: ubuntu-latest
    environment: staging    # env de GitHub con sus propios secrets
    steps:
      - name: Trigger deploy staging (Render webhook)
        run: curl -s -f -X POST "${{ secrets.RENDER_STAGING_WEBHOOK_URL }}"
```

### `.github/workflows/deploy-prod.yml` (rama main)
```yaml
name: Deploy → Production

on:
  push:
    branches: [main]

jobs:
  deploy:
    needs: [security-scan, test, build-and-scan-image]
    runs-on: ubuntu-latest
    environment: production    # requiere aprobación manual en GitHub
    steps:
      - name: Trigger deploy producción (Render webhook)
        run: curl -s -f -X POST "${{ secrets.RENDER_PROD_WEBHOOK_URL }}"
```

## Rama Git por entorno
```
feature/* → CI (security scan + unit tests)
develop   → CI completo + deploy automático a staging
main      → CI completo + deploy a prod CON aprobación manual
```

## Matriz de secretos (nunca en código)
| Variable | dev | staging | prod |
|----------|-----|---------|------|
| `SPRING_AI_OPENAI_API_KEY` | no requerido (perfil `ollama` por defecto) | GitHub Secret (env: staging), solo si `AI_PROVIDER_PROFILE=openai` | Render Env Var, solo si perfil `openai` activo |
| `SPRING_AI_OLLAMA_BASE_URL` | opcional — default `http://ollama:11434` en compose | requerido solo si perfil `ollama` self-hosted | requerido solo si perfil `ollama` self-hosted |
| `SPRING_DATASOURCE_PASSWORD` | `backend/.env.dev` | GitHub Secret | Render Env Var |
| `POSTGRES_PASSWORD` | `backend/.env.dev` | GitHub Secret | Render Env Var |
| `RENDER_STAGING_WEBHOOK_URL` | — | GitHub Secret | — |
| `RENDER_PROD_WEBHOOK_URL` | — | — | GitHub Secret |

## `.gitignore` — entradas obligatorias
```
backend/.env.dev
backend/.env
*.env
!*.env.example
```

## Proceso de provisión (seguir en orden)

### Paso 1: Identificar el entorno target
Confirmar cuál de los 3 entornos se está configurando:
- `dev` → local, Docker Compose, `.env.dev`
- `staging` → GitHub Actions + Render, GitHub Secrets
- `prod` → GitHub Actions + Render, aprobación manual, Render Env Vars

### Paso 2: Generar los archivos del entorno
- Generar `docker-compose.yml` (base) si no existe
- Generar el override específico: `docker-compose.{env}.yml`
- Generar `application-{env}.yml` en `backend/src/main/resources/`

### Paso 3: Verificar que ningún secreto queda en código
```bash
grep -r "api-key\|password\|secret" backend/src/main/ --include="*.java" --include="*.yml"
# Resultado esperado: 0 matches con valores hardcodeados
# Los valores deben ser ${ENV_VAR}, nunca literales
```

### Paso 4: Verificar el entorno localmente (solo dev)
```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up db ollama -d
sleep 5
docker compose ps  # db y ollama deben estar "healthy"
cd backend && mvn spring-boot:run -Dspring.profiles.active=dev,ollama &
curl -s http://localhost:8080/actuator/health | jq .status
# Esperado: "UP"
```

### Paso 5: Verificar pipeline CI (staging/prod)
Confirmar que `.github/workflows/ci.yml` tiene los 3 jobs en orden:
`security-scan` → `test` → `build-and-scan-image`

## Racionalizaciones comunes

| Racionalización | Realidad |
|----------------|---------|
| "Puedo hardcodear la API key en dev, nadie va a ver el .env.dev" | `.env.dev` puede llegar al repo por accidente. `${ENV_VAR}` con default funciona igual y es seguro. |
| "El staging no necesita pipeline de seguridad, es solo testing" | El staging puede recibir datos reales de prueba. OWASP + Gitleaks son rápidos y gratuitos. |
| "La imagen Docker no necesita escaneo Trivy en cada push" | Una librería vulnerable comprometida en staging puede ser usada para atacar prod. |
| "Puedo usar ddl-auto=update en staging" | Flyway es la única fuente de verdad del esquema. `update` crea divergencias silenciosas entre entornos. |

## Red Flags

- `SPRING_AI_OPENAI_API_KEY=sk-...` en cualquier archivo que podría llegar al repo
- Exigir `SPRING_AI_OPENAI_API_KEY` para que `dev` arranque — el perfil `ollama` (default) no debe requerirla
- `spring.jpa.hibernate.ddl-auto: update` en staging o prod
- `docker-compose.yml` con valores hardcodeados en lugar de `${VARIABLE}`
- Pipeline de CI que omite el security-scan job
- La imagen Docker corre como `root` (verificar `USER logsentineluser` en Dockerfile)

## Verificación (checklist de salida)

- [ ] `docker compose ps` → `db` en estado `healthy`
- [ ] `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`
- [ ] `grep -r "sk-" backend/src/` → 0 matches (sin API keys en código)
- [ ] `application-prod.yml` tiene `ddl-auto: validate`
- [ ] Dockerfile tiene `USER logsentineluser` (usuario non-root)
- [ ] `.github/workflows/ci.yml` existe con jobs `security-scan`, `test`, `build-and-scan-image`
