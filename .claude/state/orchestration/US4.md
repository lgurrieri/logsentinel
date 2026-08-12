# Orchestration Ledger: US4

## Pre-flight (PASO 1): 2026-08-11

Todos los checks pasan, sin gaps de infraestructura:
- `backend/Dockerfile` existe.
- `docker-compose.yml` existe.
- `jacoco-maven-plugin` presente en `backend/pom.xml`.
- `ci.yml` tiene `permissions:` raíz (línea 9).
- `timeout-minutes:` presente en 3 jobs (líneas 16, 29, 66).
- 0 Actions de terceros sin pinear a SHA de 40 hex.
- Job de frontend presente (línea 78).
- `docker info` exitoso (Docker disponible — requerido para US4).

## Decisiones registradas

- **Datos sintéticos:** NO aplica generación de datos sintéticos para RAG en US4.
  `LOG-US4-E2E-04` siembra directo la precondición "existe un análisis guardado con un
  script de solución sugerido" (Gherkin de la user-story) vía un fixture fijo (incidente +
  diagnóstico + script mock, ej. `echo 'success'`, tal como especifica el criterio
  Testable de la evaluación INVEST) — no requiere corpus de runbooks ni pasar por el
  pipeline real de RAG (US2)/LLM (US3), que ya tienen su propia cobertura de tests y
  cuya no-determinismo sería indeseable en un E2E. `LOG-US4-TEST-03` usa una matriz fija
  de 5 vectores de inyección Bash (`|`, `&&`, `$(...)`, `>`, backticks) — fixture
  determinística, no generación sintética.

- **PASO 2.5 (docs-analyst) — excepción ya aceptada (no re-preguntada):** el gap del
  estado intermedio `EXECUTING` faltante en el enum `RemediationAction.executionStatus`
  del contrato + la falta de un endpoint de consulta/streaming de progreso, ya estaba
  documentado como `KNOWN ISSUE` cruzado entre `docs/openapi: 3.0.yml` (línea ~187) y
  la nota "issue documental abierto" de `LOG-US4-BE-02` (misma referencia de tickets en
  ambos lados) — no se reabrió como drift nuevo.

- **PASO 2.5 (docs-analyst) — drift nuevo detectado y resuelto (2026-08-11):**

  | # | Hallazgo | Decisión humana | Aplicado |
  |---|---|---|---|
  | 1 | Nombre de tabla: `remediation_audits` (ticket) vs `remediation_actions` (user-story, consistente con schema `RemediationAction` del contrato) | Canónica: `remediation_actions` | `docs/tickets/tickets.md` LOG-US4-BE-02 actualizado |
  | 2 | Enum `executionStatus`: contrato `[SUCCESS, FAILED, DRY_RUN]`; ticket usa `EXECUTING/SUCCESS/FAILED` (sin `DRY_RUN`); user-story usaba `DRY_RUN/SUCCESS` (sin `FAILED`) | Set final: `[SUCCESS, FAILED, DRY_RUN, EXECUTING]` — `DRY_RUN` se mantiene reservado para modos futuros fuera de alcance de este ticket; `EXECUTING` se agrega al contrato al implementar `LOG-US4-BE-02` (ya anticipado por el KNOWN ISSUE existente, sin ticket aparte) | Gherkin de la user-story corregido para reflejar flujo real `EXECUTING→SUCCESS/FAILED` |
  | 3 | Diseño transaccional: ticket exige 2 transacciones `Propagation.REQUIRES_NEW` (A: commit inmediato `EXECUTING`; B: commit de cierre); user-story describía 1 transacción atómica `@Transactional` | Gana el diseño del ticket (2 transacciones `REQUIRES_NEW`) — es el único que sobrevive a una caída catastrófica durante la ejecución del script, no bloquea el pool de conexiones con I/O externa lenta dentro de una transacción, y es compatible con el polling/SSE ya aprobado en `LOG-US4-FE-03` | Sección "Transaccionalidad" de la user-story reescrita para reflejar el diseño de dos fases |
  | 4 | User-story decía que el incidente pasa a `RESOLVED` o `FAILED`, pero `Incident.status` del contrato es `[OPEN, IN_PROGRESS, RESOLVED, CLOSED]` (sin `FAILED`) | Error de redacción en la user-story — `FAILED` corresponde al `executionStatus` de `RemediationAction`, no al `status` del incidente. No se toca el contrato | Sección INVEST "Small" de la user-story corregida |
  | 5 (menor, no bloqueante) | Ningún documento define el campo de identidad del autorizador ("firma de auditoría") pese a que la narrativa de la épica lo promete | Registrar como deuda técnica, no bloquea implementación (ningún criterio técnico de ticket actual lo exige) | `DEBT-002` agregado a `docs/deuda-tecnica.md` |

  **Nota de proceso:** el subagente `logsentinel-docs-analyst` correctamente rechazó
  aplicar los diffs de los hallazgos 1/3/4 cuando se le reportó la aprobación humana vía
  un mensaje relayado del orquestador (no verificable independientemente por el
  subagente) — mismo patrón ya observado en US3. El orquestador aplicó los diffs
  directamente, dado que la aprobación humana fue obtenida de forma genuina y verificable
  vía `AskUserQuestion`/mensajes directos del humano en esta misma conversación.

## PASO 3 — Tickets ya satisfechos: ninguno

Verificado 2026-08-11: sin código de sandbox/remediation en `backend/` ni `frontend/`.
Único commit relacionado (`3ae531c`) es el propio commit de alineación documental
(menciona `LOG-US4-FE-03` en el cuerpo, pero es doc-only). Los 5 tickets arrancan de cero.

## Plan aprobado: 2026-08-11

Rama: `feature/us4-remediacion-scripts` (creada desde `main` en commit `e5cd2f3`, que
incluye el commit `docs(us4): resolver drift documental...` con las 3 correcciones del
PASO 2.5).

## Tickets

| Ticket | Agente | Estado | Ronda | SHA commit | Aprobado por | Timestamp |
|---|---|---|---|---|---|---|
| LOG-US4-BE-01 | logsentinel-backend-implementer | completed | 1 | cdf2daa | humano | 2026-08-11 |
| LOG-US4-BE-02 | logsentinel-backend-implementer | completed | 2 | a52d465 | humano | 2026-08-11 |
| LOG-US4-TEST-03 | logsentinel-backend-implementer | completed | 1 | b68e9bc | humano | 2026-08-11 |
| LOG-US4-BE-02B | logsentinel-backend-implementer | completed | 1 | 13c99c0 | humano | 2026-08-11 |
| LOG-US4-FE-03 | logsentinel-frontend-implementer | completed (PARTIAL, ver DEBT-003) | 1 | 9390216 | humano | 2026-08-11 |
| LOG-US4-BE-03 | logsentinel-backend-implementer | completed (PARTIAL, ver DEBT-004) | 1 | 4a2bd79 | humano | 2026-08-11 |
| LOG-US4-FE-04 | logsentinel-frontend-implementer | completed | 1 | 873d1c7 | humano | 2026-08-11 |
| LOG-US4-E2E-04 | logsentinel-frontend-implementer | pending | 0 | — | — | — |

## RESUELTO (2026-08-11): DEBT-003 bloquea LOG-US4-E2E-04 — tickets nuevos creados

Al preparar el dispatch de `LOG-US4-E2E-04`, lectura de la user-story confirmó que su
Gherkin Happy Path exige "el SRE presiona el botón 'Ejecutar Remediación' en la interfaz
web" — inejecutable en un E2E real dado `DEBT-003` (`RemediationPanel` no montado en
ninguna página, `GET /incidents/{id}` inexistente en el backend).

**Decisión humana: "Resolver DEBT-003 primero"**. Dos tickets nuevos creados (split
backend/frontend, mismo patrón que `BE-02B`/`FE-03`), texto completo en
`docs/tickets/tickets.md`:
- `LOG-US4-BE-03`: implementa `GET /incidents/{id}` (`IncidentController` + caso de uso
  de lectura + mapeo a `IncidentDetail`, incluyendo `analyses[].suggestedScript`).
- `LOG-US4-FE-04`: monta `RemediationPanel` en `IncidentDashboardPage.tsx` vía fetch a
  ese endpoint. Depende de `LOG-US4-BE-03`.

Orden de ejecución obligatorio: `LOG-US4-BE-03` → `LOG-US4-FE-04` → (recién entonces)
`LOG-US4-E2E-04`.

**Actualización (2026-08-11): ambos tickets completados.** `LOG-US4-BE-03` (commit
`4a2bd79`, STATUS: PARTIAL — ver `DEBT-004`, gaps preexistentes de `tokensUsed`/`updatedAt`
no bloqueantes). `LOG-US4-FE-04` (commit `873d1c7`, STATUS: GREEN, 109 tests, sin drift).
`DEBT-003` marcado **Cerrado** en `docs/deuda-tecnica.md`. `RemediationPanel` ahora
alcanzable y clickeable en `IncidentDashboardPage.tsx` vía `useIncidentDetail` →
`LOG-US4-E2E-04` queda **desbloqueado**.

## RESUELTO (2026-08-11): CONTRACT_GATE: DRIFT_DETECTED de LOG-US4-FE-03 (stdout/stderr)

Al planificar el dispatch de `LOG-US4-FE-03`, detección proactiva (grep de `executionLog`
en el backend, antes de dispatchar el ticket) de un segundo drift genuino: el ticket exige
diferenciar visualmente líneas `stdout` (gris) de `stderr` (rojo, prefijo `[ERROR]`), pero
`RemediationAction.executionLog` (contrato + `RemediationActionJpaEntity`) es un único
string combinado — no existe la separación de buffers que el ticket da por sentada.

**Decisión humana: "Alinear el contrato al ticket (nuevo ticket)"** — mismo patrón que la
resolución de `LOG-US3-DB-02B`/`LOG-US4-BE-02`. Ticket nuevo creado: `LOG-US4-BE-02B`
("Captura Diferenciada de stdout/stderr en el Registro de Auditoría"), encapsulando el
refactor sobre `LOG-US4-BE-01`/`LOG-US4-BE-02` (ambos ya completados/commiteados) en vez
de editarlos silenciosamente. Texto completo en `docs/tickets/tickets.md`. `LOG-US4-FE-03`
enmendado (no ticket nuevo) con nota de dependencia: consumirá `stdoutLog`/`stderrLog` en
vez de `executionLog`.

Orden de ejecución obligatorio: `LOG-US4-BE-02B` debe completarse (commiteado) antes de
reanudar el dispatch de `LOG-US4-FE-03`.

Pendiente de aplicar tras el checkpoint de `LOG-US4-BE-02B` (fuera del scope `backend/`
del subagente, lo aplica el orquestador): diff en `docs/openapi: 3.0.yml` — quitar
`executionLog` del schema `RemediationAction`, agregar `stdoutLog`/`stderrLog` (string,
nullable).

## RESUELTO (2026-08-11): CONTRACT_GATE: DRIFT_DETECTED de LOG-US4-BE-02

**Decisión final: Opción B**, implementada de forma estructural (no parseo frágil en el
momento de la remediación): el backend deriva y persiste el `generatedScript` en el
instante de la generación del diagnóstico (US3), no en el instante de la ejecución
(US4). Esto requirió encapsular un refactor sobre `LOG-US3-DB-02` (ya completado) en un
ticket nuevo — ver `.claude/state/orchestration/US3.md` — en vez de editar
silenciosamente código ya aprobado.

- Ticket nuevo creado: `LOG-US3-DB-02B` (agrega `suggested_script` a
  `incident_diagnostics` + extractor de bloque de código Markdown + wiring en
  `StreamDiagnosticService.persistDiagnostic`). Texto completo en `docs/tickets/tickets.md`.
- `LOG-US4-BE-02` **enmendado** (no ticket nuevo): su controller consumirá
  `IncidentDiagnostic.suggestedScript` en vez de un `requestBody`; si es `null` o no
  existe diagnóstico persistido, responde `409 Conflict`. Bullet agregado al ticket en
  `docs/tickets/tickets.md`.
- Orden de ejecución obligatorio: `LOG-US3-DB-02B` debe completarse (commiteado) antes
  de reanudar `LOG-US4-BE-02`.
- Pendiente sin cambios (no bloqueante, aplicar junto con la reanudación de BE-02):
  diff ya pre-aprobado en `docs/openapi: 3.0.yml` — agregar `EXECUTING` al enum
  `executionStatus` (línea ~194) y eliminar el comentario `KNOWN ISSUE` (líneas 187-191).

**Actualización (2026-08-11): `LOG-US3-DB-02B` completado** (STATUS: PARTIAL, 149 tests
GREEN, ARCH_GATE: PASS, DEVSECOPS_GATE: PASS). `CONTRACT_GATE: DRIFT_DETECTED` reportado
era el propio diff de `suggestedScript` en `IncidentAnalysis` que el ticket pedía —
aplicado por el orquestador tras checkpoint humano ("Aprobar y continuar"). Sin commit
todavía (pendiente decisión de commit aparte). `LOG-US4-BE-02` queda **desbloqueado**:
su controller ya puede leer `IncidentDiagnostic.suggestedScript`.

## Histórico — PAUSADO en LOG-US4-BE-02 (2026-08-11) — CONTRACT_GATE: DRIFT_DETECTED sin resolver

El núcleo transaccional (dominio, `RemediationStateMachine` con las 2 transacciones
`REQUIRES_NEW`, persistencia, migración `V6__create_remediation_actions_table.sql`,
135 tests, `ARCH_GATE: PASS`, `DEVSECOPS_GATE: PASS`) está completo, commiteado en el
working tree pero **sin commitear a git** (a la espera de resolver el drift y cerrar
el ticket). Sin regresiones: `git diff --stat` sobre archivos preexistentes muestra
solo 4 archivos modificados (+33/-0), ninguno de BE-01.

**Drift nuevo detectado (no confundir con la excepción ya aceptada del enum `EXECUTING`):**
`POST /incidents/{id}/remediations` no define `requestBody` ni ningún otro mecanismo
para indicar qué `generatedScript` ejecutar — ni el contrato ni el modelo de datos
(`IncidentAnalysis`) persisten el "script sugerido" que el Gherkin de la user story da
por existente. El agente no construyó `RemediationController` ni ningún DTO de request
a la espera de esta decisión.

Opciones candidatas presentadas al humano (2026-08-11):
- **Opción A:** agregar `requestBody: { generatedScript: string }` al POST — lo envía
  el frontend (`LOG-US4-FE-03`) tras que el SRE revise/edite el script en pantalla.
- **Opción B:** derivar el script server-side parseando un bloque de código del
  `diagnosticText` ya persistido en `incident_diagnostics` (US3), sin intervención
  del cliente.

**Decisión humana:** "Pausar aquí" — sin resolver todavía. Retomar invocando el skill
de nuevo sobre `US4` (PASO 0 recupera este ledger) una vez que se decida A/B, o se
aporte una tercera opción.

**Pendiente además (no bloqueante, aplicar junto con la resolución del drift):** aplicar
a `docs/openapi: 3.0.yml` el diff ya pre-aprobado (excepción `KNOWN ISSUE` del enum,
no es este drift nuevo) — agregar `EXECUTING` a `executionStatus` (línea ~194) y
eliminar el comentario `KNOWN ISSUE` (líneas 187-191). El subagente no lo aplicó él
mismo por estar fuera de `backend/` (restricción absoluta de su scope).
