# Orchestration Ledger: US2

## PASO 2.5 — Análisis de consistencia documental (2026-08-10)

Agente invocado: `logsentinel-docs-analyst`. Resultado inicial: `STATUS: BLOCKED` /
`CONTRACT_GATE: DRIFT_DETECTED`, 3 hallazgos.

### Decisión arquitectónica: Opción B

Migración Flyway custom + query nativa JPA con operador `<=>` de pgvector, en un único
adaptador/repositorio que también aloja el fallback Full-Text (`tsvector`) obligatorio de
`LOG-US2-BE-02`. **NO** se usa el `VectorStore`/`PgVectorStore` autoconfigurado de Spring AI.

Motivos: el fallback Full-Text es obligatorio y fuerza código a medida de todas formas;
fidelidad al criterio Gherkin de ordenar por distancia de coseno; un solo adaptador
cohesivo en vez de dos vías de acceso a datos divergentes sobre la misma tabla;
performance equivalente; irrelevancia de US3/US4 para esta decisión puntual.

### Hallazgos y resoluciones

| # | Hallazgo | Resolución | Estado |
|---|---|---|---|
| 1 | `LOG-US2-DB-01` mencionaba `runbooks` como tabla de cabecera/metadato, la user story solo define `runbook_chunks` | Alinear el ticket a la user story: modelo de **tabla única** (`runbook_chunks`) | ✅ Aplicado por `logsentinel-docs-analyst` en `docs/tickets/tickets.md` |
| 2 | Ambigüedad arquitectónica: VectorStore autoconfigurado de Spring AI vs. Flyway+JPA nativo | **Opción B** (Flyway custom + JPA nativo) | ✅ Confirmado — sin cambios adicionales de texto requeridos en tickets.md (ya describía "query nativa JPA") |
| 3 | Top K hardcodeado (`LIMIT 3`) en la user story, mientras INVEST decía "parametrizable" | Parametrizar Top K (`logsentinel.rag.top-k`, default 3) | ✅ Aplicado por `logsentinel-docs-analyst` en el Gherkin de la user story + nota en la SQL de referencia |

### Limpieza adicional derivada de Opción B (fuera del scope del docs-analyst, aplicada directamente)

- `agents.md`: árbol de arquitectura (quitada línea `vectorstore/` de Spring AI) y referencia rápida del flujo RAG actualizados.
- `backend/pom.xml`: quitada dependencia `spring-ai-starter-vector-store-pgvector` (vestigial).
- `backend/src/main/resources/application.yml`: agregada propiedad `logsentinel.rag.top-k: 3`.
- `application-ollama.yml`, `application-openai.yml`, `application-test.yml`: quitado el bloque `spring.ai.vectorstore.pgvector.*` (vestigial, nunca iba a usarse bajo Opción B).
- `.github/skills/rag-pipeline-implementation/SKILL.md`: reescrito Paso 3 (repositorio JPA nativo + fallback Full-Text en vez de `PgVectorStore.similaritySearch()`), diagrama, racionalizaciones, Red Flags y checklist.
- `.github/skills/debug-springai-sse/SKILL.md`: reescrito "Modo de falla 4" para diagnóstico sobre JPA nativo + Flyway (se quitó referencia a `SearchRequest.withSimilarityThreshold`/`initialize-schema` de Spring AI VectorStore).

**Resultado final de PASO 2.5**: `CONTRACT_GATE: OK` (confirmado por `logsentinel-docs-analyst`
tras aplicar los diffs). Sin hallazgos pendientes. Habilitado el avance a PASO 3/4.

---

## Plan de tickets

Plan aprobado por el humano: 2026-08-10.

| Ticket | Agente | Estado | Ronda | SHA commit | Aprobado por | Timestamp |
|---|---|---|---|---|---|---|
| LOG-US2-DB-01 | logsentinel-backend-implementer | completed | 1 | 23b605b | humano | 2026-08-10 |
| LOG-US2-BE-02 | logsentinel-backend-implementer | completed | 1 | ee9320a | humano | 2026-08-10 |
| LOG-US2-TEST-03 | logsentinel-backend-implementer | completed | 1 | 57e0ca4 | humano | 2026-08-10 |
