# LogSentinel

**LogSentinel** es un agente autónomo de IA para SRE/DevOps que reduce el MTTR (Mean Time To Resolution) de horas a segundos: ingiere logs y stacktraces sin estructurar, los interpreta semánticamente mediante RAG (Retrieval-Augmented Generation), los relaciona con el runbook óptimo y permite ejecutar la remediación sugerida de forma controlada y auditada — todo con streaming en tiempo real.

## Índice

- [¿Qué problema resuelve?](#qué-problema-resuelve)
- [Flujo del producto](#flujo-del-producto)
- [Arquitectura](#arquitectura)
- [Stack tecnológico](#stack-tecnológico)
- [Estructura del repositorio](#estructura-del-repositorio)
- [Puesta en marcha local](#puesta-en-marcha-local)
- [Testing](#testing)
- [Contrato de API](#contrato-de-api)
- [Despliegue](#despliegue)
- [Documentación adicional](#documentación-adicional)

## ¿Qué problema resuelve?

Cuando un sistema en producción falla, un ingeniero de guardia suele perder los primeros minutos (u horas) críticos buscando manualmente en runbooks y logs para entender qué pasó y qué hacer. LogSentinel automatiza ese triage:

1. El operador reporta el incidente pegando el log/stacktrace crudo.
2. El sistema busca semánticamente el runbook más relevante (embeddings + pgvector).
3. Un LLM genera un diagnóstico de causa raíz en streaming, token a token, citando el runbook usado.
4. Si el diagnóstico incluye un script de remediación, el operador puede ejecutarlo con doble confirmación, en un sandbox restringido (allowlist de comandos, usuario no-root, timeout), quedando todo auditado.

## Flujo del producto

```
Log crudo
   │
   ▼
LogParserService  ──────────► ParsedLog (structured output, BeanOutputConverter)
   │
   ▼
EmbeddingService  ──────────► vector (Ollama nomic-embed-text 768d / OpenAI text-embedding-3-small 1536d)
   │
   ▼
Búsqueda semántica  ────────► runbook_chunks (pgvector, operador <=>, Top-K configurable, fallback full-text)
   │
   ▼
AgentOrchestrator  ─────────► prompt + contexto del runbook
   │
   ▼
ChatClient (streaming)  ────► SseEmitter ───► EventSource (frontend, token a token)
   │
   ▼
Diagnóstico + script sugerido
   │
   ▼
RemediationService  ────────► SecuritySandbox (allowlist, no-root, timeout 30s) ───► auditoría
```

Las cuatro user stories que estructuran el producto están documentadas en [`docs/user-stories/`](docs/user-stories/):

| US | Título |
|----|--------|
| US1 | Declaración e Ingesta de Incidentes Críticos |
| US2 | Búsqueda Semántica de Runbooks Mediante IA (Embeddings) |
| US3 | Diagnóstico en Tiempo Real y Streaming de Logs |
| US4 | Ejecución Segura de Scripts de Remediación y Auditoría |

## Arquitectura

**Backend**: arquitectura hexagonal (Clean Architecture) estricta.

```
domain          → sin imports de framework (entidades, puertos)
   ▲
application     → casos de uso; depende solo de domain
   ▲
infrastructure  → adaptadores (REST, JPA, Spring AI); depende de domain y application, nunca al revés
```

- Los controladores REST nunca exponen entidades JPA, solo DTOs (`record` de Java, sin Lombok).
- La búsqueda vectorial no usa el `VectorStore` autoconfigurado de Spring AI: se implementa con Flyway (schema propio) + query nativa JPA sobre `pgvector`.

**Frontend**: arquitectura feature-driven en React, con streaming SSE consumido vía `EventSource` y sanitización de Markdown (dompurify + marked) para el output del LLM.

## Stack tecnológico

### Backend

| Componente | Tecnología |
|---|---|
| Lenguaje / Runtime | Java 25 |
| Framework | Spring Boot 4.1.0 |
| IA | Spring AI 2.0.0 — `ChatClient` / `EmbeddingModel` (nunca SDKs nativos) |
| Proveedor IA por defecto | Ollama (`llama3.1` chat + `nomic-embed-text` embeddings, local, sin API key) |
| Proveedor IA opcional | OpenAI (`gpt-4o` + `text-embedding-3-small`, perfil `openai`) |
| Base de datos | PostgreSQL 16 + extensión `pgvector` |
| Migraciones | Flyway |
| Tests de integración | Testcontainers |
| Cobertura | JaCoCo — build falla si la cobertura de líneas cae por debajo del 95% |

### Frontend

| Componente | Tecnología |
|---|---|
| Framework | React 19.2.8 |
| Lenguaje | TypeScript ~6.0.2 |
| Build tool | Vite 8.2.0 |
| Estilos | Tailwind CSS 4.3.3 |
| Routing | react-router-dom 7.18.2 |
| Sanitización Markdown | dompurify + marked |
| Tests unitarios/integración | Vitest 4.1.10 + Testing Library + MSW |
| Tests E2E | Playwright 1.62.1 |
| Linter | oxlint |

## Estructura del repositorio

```
logsentinel/
├── backend/                # Spring Boot — arquitectura hexagonal
│   ├── src/main/java/...   # domain / application / infrastructure
│   └── src/test/java/...
├── frontend/                # React + Vite — feature-driven
│   ├── src/features/...
│   └── e2e/                 # Playwright E2E local (Docker Compose bootstrap)
├── docs/
│   ├── user-stories/        # Una historia de usuario por archivo
│   ├── tickets/tickets.md   # Backlog técnico por épica/ticket (LOG-{US}-{TIPO}-{NN})
│   ├── deuda-tecnica.md     # Registro vivo de deuda técnica (DEBT-NNN)
│   ├── demo-runbook.md      # Guía paso a paso para demos en vivo (local y Azure)
│   └── openapi: 3.0.yml     # Contrato de API — fuente de verdad
├── IaC/
│   ├── nginx/                # Config de Nginx (reverse proxy + Basic Auth) de la demo Azure
│   └── scripts/              # Provisioning de la VM (az CLI) y setup de OIDC
├── docker-compose.yml        # Stack local: db + ollama + backend
├── docker-compose.prod.yml   # Overlay de producción: agrega Nginx, usa imagen pre-construida
└── agents.md                 # Convenciones de arquitectura y desarrollo para agentes de IA
```

## Puesta en marcha local

### Prerequisitos

- Java 25 (Temurin) y Maven 3.9+
- Node.js (ver `frontend/package.json` para la versión requerida)
- Docker Desktop

### 1. Levantar la infraestructura (PostgreSQL + Ollama + backend)

```bash
docker compose up -d
```

Esto levanta `db` (PostgreSQL con `pgvector`), `ollama` (con los modelos `llama3.1` y `nomic-embed-text`) y `backend` (compilado desde `./backend/Dockerfile`), con healthchecks entre ellos.

Alternativamente, para desarrollar el backend con hot-reload fuera de Docker:

```bash
cd backend
cp .env.example .env   # completar si hace falta (valores por defecto ya apuntan a localhost)
mvn spring-boot:run
```

### 2. Levantar el frontend

```bash
cd frontend
npm install
npm run dev
```

La app queda disponible en `http://localhost:5173` (proxy hacia el backend en `http://localhost:8080`).

## Testing

### Backend

```bash
cd backend
mvn test
```

Incluye tests de integración con Testcontainers (PostgreSQL real con `pgvector`). El build falla si la cobertura de líneas (JaCoCo) cae por debajo del 95%.

### Frontend

```bash
cd frontend
npm test              # unitarios/integración (Vitest + Testing Library + MSW)
npm run test:e2e      # E2E local (Playwright, bootstrap automático de Docker Compose)
```

La suite E2E local detecta si hay una instancia nativa de Ollama en el host (Plan A) o levanta el stack completo vía Docker Compose (Plan B), con fallback automático (`test.skip`) si ninguno está disponible.

## Contrato de API

El contrato OpenAPI en [`docs/openapi: 3.0.yml`](docs/openapi:%203.0.yml) es la fuente de verdad para paths, DTOs/schemas, enums y códigos de estado — tiene precedencia sobre el texto descriptivo de tickets y user stories en caso de discrepancia.

## Despliegue

La demo de producción corre en una única VM de Azure (Docker Compose: `db` + `ollama` + `backend`, ninguno con puertos públicos) detrás de un único Nginx público (puerto 80, con Basic Auth). El despliegue continuo (`cd.yml`) se autentica vía OIDC y ejecuta comandos en la VM mediante `az vm run-command` — **nunca por SSH**, sin puertos de administración abiertos al público.

> **Limitaciones conocidas de esta demo** (documentadas y con seguimiento activo en [`docs/deuda-tecnica.md`](docs/deuda-tecnica.md)): sin TLS (Basic Auth como única barrera pública — `DEBT-006`), password de PostgreSQL sin Key Vault/Managed Identity (`DEBT-007`), provisioning imperativo vía `az` CLI en vez de IaC reproducible (`DEBT-008`).

Las credenciales de Basic Auth y cualquier secreto de despliegue (claves SSH, `.azure-deploy.env`) **nunca se commitean** — se gestionan fuera del repositorio y se excluyen localmente vía `.git/info/exclude`.

## Documentación adicional

| Documento | Contenido |
|---|---|
| [`agents.md`](agents.md) | Convenciones de arquitectura, stack y desarrollo — pensado como guía para agentes de IA que contribuyen al código |
| [`docs/user-stories/`](docs/user-stories/) | Las 4 historias de usuario completas, con criterios de aceptación Gherkin |
| [`docs/tickets/tickets.md`](docs/tickets/tickets.md) | Backlog técnico completo, organizado por épica y ticket |
| [`docs/deuda-tecnica.md`](docs/deuda-tecnica.md) | Registro vivo de deuda técnica (`DEBT-NNN`) |
| [`docs/demo-runbook.md`](docs/demo-runbook.md) | Guía paso a paso para demostrar el flujo completo en vivo (local y Azure) |
| [`docs/openapi: 3.0.yml`](docs/openapi:%203.0.yml) | Contrato de API (OpenAPI 3.0.3) |
