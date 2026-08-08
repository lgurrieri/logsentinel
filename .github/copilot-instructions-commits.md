---
applyTo: "**"
---

# Instrucciones: Mensajes de Commit — LogSentinel

## Estándar: Conventional Commits 1.0.0
https://www.conventionalcommits.org/en/v1.0.0/

## Formato obligatorio

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

## Tipos permitidos

| Tipo | Cuándo usarlo |
|------|---------------|
| `feat` | Nueva funcionalidad (→ MINOR en SemVer) |
| `fix` | Corrección de bug (→ PATCH en SemVer) |
| `docs` | Cambios únicamente en documentación |
| `test` | Agregar o corregir tests sin modificar código de producción |
| `refactor` | Cambio que no agrega feature ni corrige bug |
| `perf` | Mejora de rendimiento sin cambio de comportamiento |
| `chore` | Mantenimiento sin impacto en producción (actualizaciones de deps, configs menores) |
| `build` | Cambios en el sistema de build o dependencias externas (`pom.xml`, `package.json`) |
| `ci` | Cambios en archivos y scripts de CI/CD (`.github/workflows/`) |
| `revert` | Revertir un commit anterior |

## Scopes válidos para LogSentinel

```
backend   → código Java en backend/src/
frontend  → código React en frontend/src/
db        → migraciones Flyway en backend/src/main/resources/db/migration/
iac       → Dockerfile, docker-compose*, IaC/
ci        → .github/workflows/
e2e       → frontend/e2e/ y playwright.config.ts
docs      → docs/, agents.md, README.md
```

## Reglas obligatorias

- Subject line: **máximo 72 caracteres**
- Type siempre en **minúsculas**
- Description en **imperativo** ("add", "fix", "remove" — no "added", "fixed", "removes")
- **Sin punto final** en la description
- Body separado de la subject por **una línea en blanco**
- Body explica el **por qué** del cambio, no el qué (el qué lo dice el diff)

## Breaking Changes

Se indican con `!` y/o footer `BREAKING CHANGE:`:

```
feat(backend)!: remove rawLog field from IncidentResponse

BREAKING CHANGE: rawLog was removed from the API response. Clients must use diagnosticSummary instead.
```

O solo con `!` cuando la description es suficientemente clara:

```
feat(backend)!: rename /incidents endpoint to /api/v1/incidents
```

`BREAKING CHANGE` en el footer **DEBE** estar en mayúsculas. Correlaciona con MAJOR en SemVer.

## Ejemplos del dominio LogSentinel

```
feat(backend): add IncidentController with POST /api/v1/incidents
fix(backend): close SseEmitter in finally block to prevent thread leak
feat(db): add runbook_chunks table with pgvector embedding column
feat(frontend): implement LogTerminal component with SSE streaming
test(backend): add unit test for LogParserService with BeanOutputConverter
refactor(backend): extract EmbeddingService from AgentOrchestrator
ci: pin all third-party Actions to SHA digest
chore(backend): upgrade Spring Boot to 4.1.0
docs: update agents.md with RAG pipeline quick reference
fix(frontend): add EventSource cleanup in useSSEStream return
build(backend): remove explicit Testcontainers version managed by BOM
revert: let us never again speak of the noodle incident

Refs: 676104e, a215868
```

## Separación de convenciones — commit messages vs PR titles

Los títulos de Pull Request usan el formato `[scope] descripción` (definido en `agents.md`).
Los mensajes de commit usan Conventional Commits. Son convenciones **independientes** para canales distintos:

- **Commit messages** → historial de Git, base para CHANGELOG automatizado, SemVer
- **PR titles** → contexto de revisión de código en GitHub

No mezclar los formatos entre sí.
