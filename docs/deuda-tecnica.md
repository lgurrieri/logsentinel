# Registro de Deuda Técnica

Inventario vivo de gaps de diseño o compromisos técnicos detectados durante la
implementación de tickets (típicamente vía `ESCALATION_NOTE` de los subagentes
`logsentinel-backend-implementer` / `logsentinel-frontend-implementer` / `logsentinel-devsecops`),
que **no bloquean** la aprobación del ticket que los originó pero que conviene resolver
más adelante con un ticket dedicado.

No reemplaza `docs/tickets/tickets.md`: mientras un ítem viva acá es un candidato a
ticket, no un ticket en sí. Cuando se decide resolverlo, se crea el ticket
correspondiente (convención `LOG-{US}-{TIPO}-{NN}` o `LOG-CORE-INFRA-NN` si es
transversal) y este registro se actualiza con el estado `Convertido a ticket` +
referencia.

## Cómo agregar un ítem

Cada ítem nuevo va con ID incremental `DEBT-NNN` (3 dígitos, no reutilizar números de
ítems cerrados/convertidos) y esta plantilla:

```markdown
### `DEBT-NNN`: Título breve

* **Origen:** ticket/US donde se detectó (ej. `LOG-US3-FE-03`)
* **Descripción:** qué gap o compromiso existe.
* **Impacto:** por qué importa, qué riesgo o limitación introduce.
* **Sugerencia de resolución:** posible enfoque para cerrarlo.
* **Estado:** Abierto | Convertido a ticket (`LOG-...`) | Cerrado
* **Detectado:** fecha (YYYY-MM-DD)
```

---

### `DEBT-001`: Backend no emite señal SSE explícita de cierre del stream

* **Origen:** `LOG-US3-BE-01` / `LOG-US3-FE-03`
* **Descripción:** El endpoint `GET /incidents/{id}/diagnostic/stream` no emite un
  evento SSE explícito de finalización (`event: complete` / `event: error`) — solo
  cierra la conexión (`emitter.complete()` / `emitter.completeWithError()`). Por spec
  WHATWG, el callback `onerror` del `EventSource` del cliente es indistinguible entre
  un cierre exitoso y una falla de red real.
* **Impacto:** El frontend (`useDiagnosticStreamConnection.ts`) tuvo que resolverlo con
  una heurística: si ya se recibió al menos un chunk antes de `onerror` ⇒ se asume
  `COMPLETED` (sin reintento); si no se recibió ningún chunk ⇒ se asume falla real,
  con backoff exponencial 1s/2s/4s y máximo 3 intentos antes de `STREAM_FAILED`. Es
  funcional pero frágil: un fallo de red que ocurre después del primer chunk (pero
  antes de que el diagnóstico esté realmente completo) se malinterpretaría como éxito.
* **Sugerencia de resolución:** agregar un evento SSE explícito de protocolo
  (`event: complete` al finalizar `execute()` con éxito, `event: error` con el mensaje
  ante excepción) antes de `emitter.complete()`/`completeWithError()`, y actualizar el
  cliente para dejar de depender de la heurística de "¿hubo chunks antes de onerror?".
* **Estado:** Abierto
* **Detectado:** 2026-08-11
