# Orchestration Ledger: US3

## Plan aprobado: 2026-08-10

Rama: `feature/us3-streaming-diagnostico-sse`

## Decisiones registradas

- **Datos sintéticos (pares log-runbook):** NO aplica a US3. Los 3 tickets de US3 son
  transporte SSE + persistencia + terminal UI del diagnóstico ya generado; no tocan
  `runbook_chunks` ni requieren corpus de incidentes. La única mención de seed data en
  el repo (`AI4Devs-finalproject/.../descripción-general-del-producto.md:64`) es un
  concern de US2 (ya implementado, PR #2 mergeado), sin ticket propio en `tickets.md`.
  Queda como gap de backlog a considerar aparte (no bloquea US3).
- **PASO 2.5 (docs-analyst) — drift detectado:** user-story US3 usaba path viejo
  `/api/v1/incidents/{id}/stream` y tabla vieja `incident_analyses`, mientras
  `tickets.md` (LOG-US3-BE-01/DB-02) y el contrato OpenAPI (línea 77) ya usaban
  `/incidents/{id}/diagnostic/stream` / `incident_diagnostics`, sin `KNOWN ISSUE`
  cruzado que lo excusara. **Decisión humana: "Alinear la user-story al contrato"**.
  Aplicado directamente por el orquestador (aprobación ya obtenida vía AskUserQuestion)
  dado que el subagente docs-analyst rechazó correctamente aplicar el diff en base a
  una instrucción relayed sin poder verificar consentimiento humano de forma
  independiente. Commit: `7314e17`.
- **Rama:** se creó `feature/us3-streaming-diagnostico-sse` desde `main` (decisión
  humana explícita, no se cambió de rama por cuenta propia).

## Tickets

| Ticket | Agente | Estado | Ronda | SHA commit | Aprobado por | Timestamp |
|---|---|---|---|---|---|---|
| LOG-US3-BE-01 | logsentinel-backend-implementer | completed | 1 | c34a432 | humano | 2026-08-11 |
| LOG-US3-DB-02 | logsentinel-backend-implementer | completed | 1 | e771e28 | humano | 2026-08-11 |
| LOG-US3-FE-03 | logsentinel-frontend-implementer | paused | 1 | — | — | — |

## Nota de pausa (2026-08-11, resuelta)

`LOG-US3-DB-02` quedó GREEN y sin commitear tras una pausa; el humano pidió commitearlo
en un turno posterior → commit `e771e28`. Sin acción pendiente.

## Nota de pausa (2026-08-11, activa)

`LOG-US3-FE-03` quedó GREEN (49 tests, 0 failed) y sin commitear tras el checkpoint;
el humano eligió "Pausar aquí". `git diff --stat`: 141 inserciones / 1 deletion en 6
archivos modificados (`package.json`, `package-lock.json`, `IncidentDashboardPage.tsx`,
`index.ts`, `index.css`, `test/setup.ts`) + archivos nuevos (`DiagnosticTerminal.tsx`
+ test, `context/DiagnosticStreamContext.tsx` + test, `hooks/useDiagnosticStream.ts` +
test, `hooks/useDiagnosticStreamConnection.ts` + test, `utils/sanitizeMarkdown.ts` +
test, `types/diagnosticStream.types.ts`). Sin regresiones sobre BE-01/DB-02.

Notas no bloqueantes registradas como deuda técnica en `docs/deuda-tecnica.md`:
- `DEBT-001`: backend no emite señal SSE explícita de cierre (`event: complete`/`error`);
  frontend usa heurística (chunk recibido antes de `onerror` ⇒ completado; cero chunks
  ⇒ fallo real con backoff exponencial 1s/2s/4s, máx. 3 intentos ⇒ `STREAM_FAILED`).

Nota menor no registrada como deuda (decisión de estilo, no un gap funcional):
- Paleta Tailwind del proyecto (`zinc-950`/`green-400`/`zinc-700`) sustituyó los hex
  literales del ticket (`#0d1117`/`#39ff14`) por la restricción de no usar valores
  arbitrarios en Tailwind.

Para retomar: invocar de nuevo `orchestrate-user-story US3` (PASO 0 reconcilia este
estado) y decidir si commitear tal cual o pedir correcciones antes de continuar a
PASO 7 (validación final).
