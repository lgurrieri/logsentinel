---
name: orchestrate-user-story
description: >
  Orquesta la implementación end-to-end de una user story de LogSentinel (US1–US4),
  invocando en secuencia los subagentes backend/frontend/devsecops con checkpoints
  humanos por ticket. Usar cuando: "implementar US1", "orquestar historia US2",
  "ejecutar todos los tickets de US3", "implementar user story completa".
---

# Skill: orchestrate-user-story

## Propósito

Planificar y coordinar la implementación secuencial de todos los tickets de una user story,
delegando cada ticket al subagente especializado correspondiente (`logsentinel-backend-implementer`,
`logsentinel-frontend-implementer`, o `logsentinel-devsecops`), con un checkpoint humano
obligatorio entre cada ticket.

## No-objetivos explícitos

- NO crear Pull Requests ni hacer `git push`
- NO cambiar de rama por su cuenta
- NO hacer `git commit` sin preguntar aparte en cada ticket
- NO modificar nada dentro de `.github/`
- NO ejecutar scripts de remediación reales (US4) sin aprobación humana explícita

---

## INPUT

Argumento obligatorio: **ID de user story** (`US1`, `US2`, `US3`, o `US4`).

Si el argumento no corresponde a ninguna sección en `docs/tickets/tickets.md`,
usar `AskUserQuestion` para aclarar cuál user story implementar.

---

## PASO 0 — Recuperar y reconciliar estado previo

1. Verificar `git branch --show-current` — nunca cambiar de rama por cuenta propia.
2. Buscar el archivo `.claude/state/orchestration/{US_ID}.md`.
3. **Si existe** (sesión previa):
   - Leer el ledger.
   - Reconciliar contra estado real del repo:
     - Para cada ticket marcado `pending` en el ledger, comprobar si ya fue resuelto fuera de banda:
       `git log --oneline --grep={TICKET_ID}` + existencia de archivos esperados por convención.
     - Marcar como "posiblemente ya hecho" cualquier ticket con evidencia de existencia.
   - Mostrar resumen reconciliado al humano.
   - `AskUserQuestion` con opciones:
     - "Retomar desde el primer ticket pendiente"
     - "Reiniciar el plan completo desde cero"
     - "Cancelar"
4. **Si no existe**: crearlo en blanco, todos los tickets `pending`.

---

## PASO 1 — Pre-flight de épica core

Verificar prerequisitos de la infraestructura base antes de arrancar la historia:

### Checks obligatorios

| Check | Cómo verificar | Aplica si |
|---|---|---|
| `backend/Dockerfile` existe | Glob | Siempre |
| `docker-compose.yml` existe | Glob | Siempre |
| JaCoCo configurado en `backend/pom.xml` | Grep `jacoco-maven-plugin` | Siempre |
| `ci.yml` tiene `permissions:` raíz | Grep en `.github/workflows/ci.yml` | Siempre |
| `ci.yml` tiene `timeout-minutes:` por job | Grep | Siempre |
| Actions pineadas a SHA (40 hex) | Grep `uses:.*@[a-f0-9]{40}` | Siempre |
| Job de frontend en CI | Grep `npm test\|npm run build` en ci.yml | Siempre |
| Docker disponible | Bash: `docker info` | Solo US2, US4 |

### Decisión en caso de gaps

Si hay checks fallidos, `AskUserQuestion`:
- "Cerrar los gaps ahora (invocar logsentinel-devsecops con sub-objetivo harden-ci)"
- "Continuar aceptando ruido conocido de verify-clean-arch (checks 11–12 van a reportar FAIL)"
- "Cancelar"

Si se elige cerrar gaps:
1. Invocar Agent(subagent_type: logsentinel-devsecops, prompt con sub-objetivo `harden-ci`)
2. Mostrar resultado + checkpoint humano (aprobar/corregir)
3. Retomar el flujo del pre-flight (volver a verificar)

---

## PASO 2 — Armar el plan de tickets

1. Leer la sección de la US en `docs/tickets/tickets.md`.
2. Leer la user story completa en `docs/user-stories/`.
3. Mapear cada ticket a un agente con esta prioridad de reglas:

### Prioridad 1: Tabla explícita de mapeos conocidos

| Ticket | Agente | Nota |
|---|---|---|
| `LOG-CORE-INFRA-00` | logsentinel-devsecops | Dockerfile, compose, CI base |
| `LOG-CORE-BE-00` | logsentinel-backend-implementer | Skeleton + JaCoCo |
| `LOG-US1-DB-01` | logsentinel-backend-implementer | Override TDD: test integración Testcontainers |
| `LOG-US1-BE-02` | logsentinel-backend-implementer | |
| `LOG-US1-BE-02B` | logsentinel-backend-implementer | |
| `LOG-US1-FE-03` | logsentinel-frontend-implementer | |
| `LOG-US2-DB-01` | logsentinel-backend-implementer | Override TDD: test integración Testcontainers |
| `LOG-US2-BE-02` | logsentinel-backend-implementer | |
| `LOG-US2-TEST-03` | logsentinel-backend-implementer | |
| `LOG-US3-BE-01` | logsentinel-backend-implementer | |
| `LOG-US3-DB-02` | logsentinel-backend-implementer | Override TDD: test integración Testcontainers |
| `LOG-US3-FE-03` | logsentinel-frontend-implementer | Usa implement-logterm-sse.prompt.md |
| `LOG-US4-BE-01` | logsentinel-backend-implementer | |
| `LOG-US4-BE-02` | logsentinel-backend-implementer | |
| `LOG-US4-TEST-03` | logsentinel-backend-implementer | |
| `LOG-US4-FE-03` | logsentinel-frontend-implementer | |
| `LOG-US4-E2E-04` | logsentinel-frontend-implementer | Escala a devsecops solo si ESCALATION_NOTE lo indica |

### Prioridad 2: Regla de sufijo (para tickets futuros no listados arriba)

- `-DB-` / `-BE-` / `-TEST-` → logsentinel-backend-implementer
- `-FE-` → logsentinel-frontend-implementer
- `-INFRA-` / `-CI-` / `-DEVOPS-` → logsentinel-devsecops
- `-E2E-` → logsentinel-frontend-implementer (con protocolo de escalamiento)

### Prioridad 3: Pregunta obligatoria

Si el sufijo no matchea ninguna regla, o el texto del ticket mezcla señales claras de
ambos lados (menciona endpoint/`@RestController` Y componente/React en el mismo ticket):
**no adivinar** — `AskUserQuestion` obligatoria preguntando a qué agente asignarlo.
Registrar la respuesta en el ledger para no volver a preguntar en el futuro.

---

## PASO 3 — Detectar tickets posiblemente ya satisfechos

Para cada ticket del plan:
1. Glob: buscar archivos que coincidan con la convención de nombres esperada
   (ej. para `LOG-US1-DB-01`: `backend/src/main/resources/db/migration/*incidents*`)
2. `git log --oneline --grep={TICKET_ID}` — buscar commits que referencien el ticket
3. Si hay evidencia → marcar "posiblemente ya hecho — verificar" (NO excluir automáticamente)

---

## PASO 4 — Mostrar plan y pedir aprobación humana

Mostrar tabla:

```
| # | Ticket          | Agente                         | Regla     | Estado detectado       |
|---|-----------------|--------------------------------|-----------|------------------------|
| 1 | LOG-US1-DB-01   | logsentinel-backend-implementer| tabla     | nuevo                  |
| 2 | LOG-US1-BE-02   | logsentinel-backend-implementer| tabla     | nuevo                  |
| 3 | LOG-US1-BE-02B  | logsentinel-backend-implementer| tabla     | nuevo                  |
| 4 | LOG-US1-FE-03   | logsentinel-frontend-implementer| tabla    | nuevo                  |
```

`AskUserQuestion`:
- "Aprobar el plan tal cual"
- "Editar el mapeo de algún ticket (indicar cuál y a qué agente)"
- "Cancelar"

---

## PASO 5 — Materializar el plan

1. Crear un Task por ticket con `TaskCreate`, encadenados con `addBlockedBy` en el orden del backlog.
   Los tickets confirmados como "ya hechos" se crean directamente en estado `completed`.
2. Escribir/actualizar el ledger en `.claude/state/orchestration/{US_ID}.md` con formato:

```markdown
# Orchestration Ledger: {US_ID}

## Plan aprobado: {fecha}

| Ticket | Agente | Estado | Ronda | SHA commit | Aprobado por | Timestamp |
|---|---|---|---|---|---|---|
| LOG-US1-DB-01 | backend-implementer | pending | 0 | — | — | — |
| LOG-US1-BE-02 | backend-implementer | pending | 0 | — | — | — |
...
```

---

## PASO 6 — Loop principal por ticket

Para cada ticket con estado `pending` en el ledger, en orden:

### 6.1 Marcar inicio

- `TaskUpdate` → `in_progress`
- Actualizar ledger: estado `in_progress`

### 6.2 Armar el prompt del subagente

Construir el prompt incluyendo:
- La **Descripción** del ticket (verbatim de `docs/tickets/tickets.md`)
- Los **Criterios de Aceptación Técnicos** (verbatim)
- Puntero: "Lee la user story completa en `docs/user-stories/{archivo}.md`"
- Si es ticket `-DB-`: "IMPORTANTE: el TDD RED de este ticket es un test de integración
  con Testcontainers, no un test de caso de uso con mocks. Ver el override documentado
  en tu agente."
- Si es ticket `-E2E-`: "Nota: puedes invocar docker-compose para levantar backend+db,
  pero NO puedes editar archivos fuera de `frontend/`. Si necesitas hacerlo, reporta
  STATUS: BLOCKED con ESCALATION_NOTE."
- Si es ticket que involucra SSE (US3-FE-03): "Referencia adicional: lee
  `.github/prompts/implement-logterm-sse.prompt.md`"
- Cierre: "Al terminar, emite el bloque ---OUTPUT--- ... ---END OUTPUT--- documentado
  en tu contrato de salida."

### 6.3 Invocar el subagente

```
Agent(
  subagent_type: {agente mapeado},
  prompt: {prompt construido en 6.2},
  run_in_background: false
)
```

### 6.4 Procesar resultado — CHECKPOINT

Al recibir la respuesta del subagente:

1. Parsear el bloque `---OUTPUT---` ... `---END OUTPUT---`
2. Ejecutar (solo lectura): `git status --short` + `git diff --stat`
3. Mostrar al humano:
   - El reporte narrativo del subagente (resumido, no pegar logs crudos de mvn/npm)
   - STATUS del bloque estructurado
   - TESTS (passed/failed)
   - ARCH_GATE y DEVSECOPS_GATE
   - `git diff --stat` (solo estadísticas, no el diff completo)
   - SUGGESTED_COMMIT
4. Si `git diff --stat` muestra **deletions** en un archivo que pertenecía a un ticket ya
   aprobado previamente en esta misma US → señalar explícitamente como posible regresión.
5. Si DEVSECOPS_GATE es FAIL pero los checks que fallan son 11/12 (ya conocidos desde
   el pre-flight como "ruido aceptado") → aclarar que es ruido preexistente, no regresión.

### 6.5 Decisión del humano

**Si STATUS = BLOCKED:**
`AskUserQuestion` con opciones:
- "Pedir correcciones al agente"
- "Descartar cambios de este ticket y reintentar"
- "Pausar aquí"

**Si STATUS = GREEN o PARTIAL:**
`AskUserQuestion` con opciones:
- "Aprobar y continuar al siguiente ticket"
- "Pedir correcciones al agente"
- "Descartar cambios de este ticket y reintentar"
- "Pausar aquí"

### 6.6 Ramas de acción

#### → Pedir correcciones
- Pedir al humano que escriba su feedback/instrucciones de corrección.
- `SendMessage` al mismo agente (por su agentId) con el feedback textual.
- Esperar respuesta, volver a 6.4 (nuevo checkpoint).
- **Tope: máximo 3 rondas** de corrección por ticket.
  Al superar 3, ya NO ofrecer "Pedir correcciones" — solo:
  - "Descartar cambios y reintentar"
  - "Pausar aquí"
- Actualizar `Ronda` en el ledger.

#### → Descartar cambios y reintentar
- Confirmar explícitamente con `AskUserQuestion`: "Esto va a revertir los cambios de este
  ticket. ¿Confirmar?" (Sí / No)
- Si confirma: limpiar **solo** los paths listados en `FILES_CHANGED` del bloque de salida
  con `git restore` / `rm` para archivos nuevos. **NUNCA `git clean` amplio.**
- Volver a 6.2 (reintentar desde cero), reseteando el contador de rondas.
- Si el humano prefiere no reintentar → tratar como "Pausar aquí".

#### → Aprobar y continuar
1. `AskUserQuestion`: "¿Generar el commit sugerido ahora? (nunca hace push)"
   - Sí → ejecutar `git add` de los archivos en FILES_CHANGED + `git commit -m "..."` con el
     SUGGESTED_COMMIT. Registrar el SHA en el ledger.
   - No → dejar los cambios sin commit (staged o unstaged según estén).
2. `TaskUpdate` → `completed`
3. Actualizar ledger: estado `completed`, SHA si aplica, "humano" como aprobador, timestamp.
4. Continuar al siguiente ticket en el loop.

#### → Pausar aquí
- Actualizar ledger: estado `paused` con nota.
- Mostrar resumen de progreso (`TaskList` + ledger).
- Cortar el loop. El humano puede retomar invocando el skill de nuevo (Paso 0 lo recupera).

### 6.7 Escalamiento (solo para tickets -E2E- con BLOCKED)

Si un ticket `-E2E-` reporta `STATUS: BLOCKED` con `ESCALATION_NOTE` indicando necesidad
de tocar infraestructura fuera de `frontend/`:

1. Mostrar la nota de escalamiento al humano.
2. `AskUserQuestion`: "¿Invocar logsentinel-devsecops para resolver la dependencia de infraestructura?"
   - Sí → invocar Agent(subagent_type: logsentinel-devsecops) con la nota como contexto → checkpoint → si aprobado, reintentar el ticket E2E con frontend-implementer.
   - No → pausar o descartar según prefiera el humano.

---

## PASO 7 — Validación final de la US

Cuando **todos** los tickets estén en estado `completed`:

1. Si hubo cambios en `backend/`:
   ```bash
   cd backend && mvn test -q
   ```
2. Si hubo cambios en `frontend/`:
   ```bash
   cd frontend && npm run build && npm test -- --run
   ```
3. Ejecutar chequeo de `verify-clean-arch` (Grep de las reglas de dependencia según
   `.github/skills/verify-clean-arch/SKILL.md`).
4. Si algo falla → reportar al humano con contexto, NO intentar arreglar automáticamente
   (ya se está fuera del scope de un ticket individual).

---

## PASO 8 — Reporte final

Emitir:

1. **Checklist de criterios Gherkin** de la user story, mapeando cada criterio al ticket
   que lo cubrió (checkbox ✓/✗).
2. **Título de PR sugerido** según convención del proyecto: `[backend|frontend] descripción breve`
   (sin crearlo).
3. **Resumen de commits** generados durante la orquestación (SHA + mensaje).
4. **Estado final del ledger** (tabla con todos los tickets en `completed`).

---

## CONTRATO DE SALIDA ESTRUCTURADO (referencia para los 3 subagentes)

Los tres subagentes (`logsentinel-backend-implementer`, `logsentinel-frontend-implementer`,
`logsentinel-devsecops`) deben terminar su respuesta con este bloque **exactamente** en este formato:

```
---OUTPUT---
STATUS: GREEN | PARTIAL | BLOCKED
FILES_CHANGED:
  - ruta/relativa/archivo1
  - ruta/relativa/archivo2
TESTS: N passed, M failed
ARCH_GATE: PASS | FAIL | N/A
DEVSECOPS_GATE: PASS | FAIL | PENDING
SUGGESTED_COMMIT: "tipo(scope): descripción"
ESCALATION_NOTE: <vacío si no aplica, o texto libre describiendo qué se necesita fuera de scope>
---END OUTPUT---
```

Campos:
- **STATUS**: `GREEN` = completado sin problemas; `PARTIAL` = funcional con warnings no bloqueantes; `BLOCKED` = imposible completar sin intervención externa.
- **FILES_CHANGED**: lista de paths relativos a la raíz del repo, uno por línea.
- **TESTS**: conteo de tests ejecutados. `N/A` si es infraestructura pura.
- **ARCH_GATE**: resultado de checks 1–7 de verify-clean-arch. `N/A` si no aplica.
- **DEVSECOPS_GATE**: resultado de checks 8–12. `PENDING` si los artefactos que verifican aún no existen.
- **SUGGESTED_COMMIT**: mensaje de commit listo para usar, formato Conventional Commits 1.0.0.
- **ESCALATION_NOTE**: solo rellenar si STATUS es BLOCKED — describir exactamente qué necesita de otro agente o del humano.
