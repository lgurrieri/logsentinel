---
name: verify-openapi-contract
description: >
  Gate de cumplimiento del contrato OpenAPI para LogSentinel. Verifica que un ticket
  de backend o frontend respeta a rajatabla `docs/openapi: 3.0.yml` antes de escribir
  cualquier test o código. Usar cuando: "reconciliar ticket con el contrato",
  "verificar contrato OpenAPI", "check contrato antes de implementar".
---

# Skill: verify-openapi-contract

## Propósito

`docs/openapi: 3.0.yml` es la fuente de verdad de paths, schemas, enums y status codes
de la API de LogSentinel — por encima de lo que diga un ticket o una user story. Este
skill se ejecuta **antes** de generar cualquier test o código de un ticket que toque un
endpoint, para detectar discrepancias entre el ticket y el contrato y, si existen,
detenerse a pedir aprobación humana en vez de implementar en base a la fuente
equivocada.

Usar cuando: "implementar ticket LOG-*-BE-*/FE-*", "reconciliar contrato", antes del
Paso 2 (TDD RED) de `logsentinel-backend-implementer` / `logsentinel-frontend-implementer`.

## Cómo localizar el contrato

- El archivo es `docs/openapi: 3.0.yml` — el nombre incluye un espacio y `:` literales;
  citarlo entre comillas al referenciarlo o buscarlo (`grep -n "..." "docs/openapi: 3.0.yml"`).
- El bloque `servers:` ya antepone `/api/v1` a todos los paths. Un ticket que dice
  `POST /api/v1/incidents` contra un contrato que define `/incidents` **NO es drift**.
- Los schemas están en `components/schemas/*`. Localizar el schema referenciado por el
  path relevante (`$ref: '#/components/schemas/{Nombre}'`) antes de comparar campos.

## Checklist de comparación

Para el path/endpoint del ticket, comparar contra el contrato:

| Aspecto | Cómo verificar |
|---|---|
| Método HTTP | GET/POST/etc. coincide con el definido en el path del contrato |
| Path | Coincide ignorando el prefijo `/api/v1` de `servers:` |
| Nombre del schema de request/response | El ticket usa el mismo nombre que `$ref` en el contrato (ej. `IncidentCreate`, no un sufijo mecánico inventado como `CreateIncidentRequest`) |
| Campos requeridos vs opcionales | `required:` del schema coincide con las validaciones que el ticket pide |
| Tipos y formatos | `type`/`format` (ej. `uuid`, `date-time`) coinciden |
| Valores de enum | Los valores listados en el ticket son un subconjunto exacto del `enum:` del contrato |
| Status codes | Los códigos de respuesta (`200`, `201`, `404`, etc.) que el ticket espera están en `responses:` del contrato |

## Qué cuenta como discrepancia

- Path o método HTTP distinto al del contrato (descontando el prefijo `/api/v1`).
- Campos de request/response faltantes, sobrantes o renombrados respecto al schema.
- Valores de enum que el ticket menciona pero el contrato no define, o viceversa.
- Status codes que el ticket espera pero el contrato no documenta.
- El ticket exige un comportamiento (endpoint, campo, tabla) que el contrato no define en absoluto.

## Excepciones ya documentadas — no volver a preguntar

El repo ya tiene un patrón real de excepción aprobada: un comentario `KNOWN ISSUE` en el
contrato, cruzado con una nota idéntica en el ticket correspondiente. Ejemplo vigente:
`RemediationAction.executionStatus` en `docs/openapi: 3.0.yml` documenta que falta el
estado intermedio `EXECUTING`, con nota cruzada en el ticket `LOG-US4-BE-02` en
`docs/tickets/tickets.md`.

Si la discrepancia detectada ya está documentada en **ambos lados** (contrato + ticket)
referenciando el mismo ticket ID → **no es un gate bloqueante**: continuar la
implementación y mencionar la excepción en el reporte final
(`Contrato OpenAPI: Excepción documentada ({ref})`).

Si la discrepancia NO está documentada en ambos lados → es un gate bloqueante (ver
Protocolo de escalamiento).

## Protocolo de escalamiento

1. Producir el relevamiento en formato tabla:

   | Aspecto | Dice el ticket | Dice el contrato | Recomendación |
   |---|---|---|---|
   | (ej. nombre del DTO) | `CreateIncidentRequest` | `IncidentCreate` | Alinear el ticket al contrato |

2. **Nunca elegir unilateralmente** "gana el ticket" o "gana el contrato" — la decisión
   es siempre humana.
3. Si el agente corre como subagente dispatchado (Task/Agent): terminar con
   `STATUS: BLOCKED`, incluir el relevamiento completo en `ESCALATION_NOTE` del bloque
   `---OUTPUT---`, y detenerse sin generar código del endpoint en discrepancia.
4. Si el agente corre como agente principal interactivo: usar `AskUserQuestion`
   directamente con las opciones:
   - "Alinear el ticket al contrato"
   - "Alinear el contrato al ticket (nuevo ticket aparte, ver convención `LOG-CORE-INFRA-01`)"
   - "Aprobar excepción documentada (registrar `KNOWN ISSUE` cruzado, como `LOG-US4-BE-02`)"
   - "Pausar sin decidir"

## Racionalizaciones comunes

| Racionalización | Realidad |
|----------------|----------|
| "El ticket es más reciente, seguro está actualizado" | El contrato es la fuente de verdad explícita del proyecto. Un ticket desactualizado no invalida el contrato — hay que detenerse y preguntar, no asumir. |
| "Es solo un nombre de DTO, no importa" | Ya causó drift real: `CreateIncidentRequest`/`IncidentResponse` se implementaron y propagaron hasta el frontend en vez de `IncidentCreate`/`Incident` del contrato. |
| "`/api/v1` en el ticket es una discrepancia" | Falso positivo — `servers:` ya antepone ese prefijo en el contrato. No reportar como drift. |
| "Ya lo resuelvo después, avanzo con el ticket" | El gate existe precisamente porque "después" nunca llegó — el drift se detectó solo por auditoría manual, no por proceso. |

## Red Flags (DETENER el trabajo inmediatamente)

- Un DTO de request/response nombrado con sufijo mecánico (`{Name}Request`/`{Name}Response`)
  cuando el contrato define un nombre de schema distinto.
- Un path o método HTTP que no aparece en `docs/openapi: 3.0.yml` bajo ningún alias.
- Un enum con valores que el contrato no lista.
- Una discrepancia detectada sin relevamiento tabla producido antes de escribir código.

## Verificación (checklist de salida)

- [ ] Path/método del ticket localizado en el contrato (descontando prefijo `/api/v1`)
- [ ] Schema de request/response comparado campo a campo contra el `$ref` del contrato
- [ ] Enums y status codes comparados
- [ ] Si hay discrepancia: ¿ya está documentada como excepción cruzada (`KNOWN ISSUE` + ticket)? Si sí, continuar; si no, escalar
- [ ] Si se escaló: relevamiento en formato tabla producido, aprobación humana obtenida antes de generar código

**RESULTADO**: `OK` (sin discrepancias o excepción ya documentada) o `DRIFT_DETECTED` (relevamiento pendiente de aprobación humana).
