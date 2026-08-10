# Orchestration Ledger: US1

## Plan aprobado: 2026-08-08

| Ticket | Agente | Estado | Ronda | SHA commit | Aprobado por | Timestamp |
|---|---|---|---|---|---|---|
| LOG-US1-DB-01 | backend-implementer | completed | 1 | 399a487 | humano | 2026-08-08 |
| LOG-US1-BE-02 | backend-implementer | completed | 1 | 945c34b | humano (reconciliado fuera de banda) | 2026-08-09 |
| LOG-US1-BE-02B | backend-implementer | completed | 2 | 8e3111a | humano | 2026-08-09 |
| LOG-US1-FE-03 | frontend-implementer | completed | 1 | 5aa1d88 | humano | 2026-08-10 |

## Post-implementación: bugs encontrados y corregidos durante verificación manual de LOG-US1-FE-03
- CORS bloqueaba el POST del form contra `http://localhost:5173` → fix `WebConfig` (commit 1773b1d)
- `createdAt` volvía `null` tras `save()` → fix `@CreationTimestamp` (commit 1773b1d)
- Ruta `/incidents/:id/dashboard` no registrada (React Router "No routes matched") → placeholder `IncidentDashboardPage` + route (incluido en commit 5aa1d88)
- Terminología obsoleta (`priority`/`IncidentRequest`) en docs y OpenAPI → commit 3ae531c

## Pre-flight
- ci.yml hardened: commit daad582
- Docker: disponible
- JaCoCo: configurado
- Gaps cerrados: permissions, timeout-minutes, SHA-pinning, job frontend
