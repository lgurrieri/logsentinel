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

---

### `DEBT-002`: Ningún documento define el campo de identidad del autorizador de una remediación

* **Origen:** `LOG-US4-FE-03` / narrativa de épica US4
* **Descripción:** La narrativa de la épica US4 promete un "registro inmutable de qué se
  alteró y **quién lo autorizó**", y el modal de doble confirmación de `LOG-US4-FE-03`
  dice textualmente *"Esta acción quedará registrada bajo tu firma de auditoría"*. Sin
  embargo, ningún documento (contrato OpenAPI, ticket `LOG-US4-BE-02`, ni la user-story)
  define un campo concreto de usuario/aprobador en el schema `RemediationAction` ni en
  la tabla `remediation_actions` — la respuesta del contrato solo tiene `id`,
  `generatedScript`, `executionStatus`, `executedAt`, `stdoutLog`, `stderrLog`
  (campos actualizados por `LOG-US4-BE-02B`).
* **Impacto:** Tal como está especificado hoy, ningún ticket implementa realmente captura
  de identidad del aprobador — la "firma de auditoría" prometida en la UI no tiene
  contraparte de persistencia. Si se implementa `LOG-US4-BE-02`/`FE-03` tal cual están
  hoy, el registro de auditoría no podrá responder "quién" autorizó la ejecución, solo
  "qué" y "cuándo".
* **Sugerencia de resolución:** agregar un campo (ej. `authorizedBy`/`executedBy`) al
  schema `RemediationAction` del contrato y a la tabla `remediation_actions`, poblado
  desde la sesión/autenticación del usuario que confirma el modal — vía un ticket
  dedicado (convención `LOG-CORE-INFRA-NN` o un nuevo `LOG-US4-BE-03`), dado que ningún
  ticket actual de US4 lo cubre y requeriría además definir el mecanismo de autenticación
  de usuarios (no resuelto en ninguna US anterior).
* **Estado:** Abierto
* **Detectado:** 2026-08-11

---

### `DEBT-003`: `GET /incidents/{id}` está en el contrato pero no implementado — `RemediationPanel` no está montado en ninguna página

* **Origen:** `LOG-US4-FE-03`
* **Descripción:** `docs/openapi: 3.0.yml` define `GET /incidents/{id}` (respuesta
  `IncidentDetail`), pero `IncidentController.java` solo implementa
  `@PostMapping` — no existe el `@GetMapping` correspondiente. El componente
  `RemediationPanel` (caja de código + modal de doble aprobación + terminal de
  stdout/stderr) quedó completamente implementado, testeado (51 tests) y exportado
  en el barrel de `frontend/src/features/remediations/`, pero **no se montó** en
  `IncidentDashboardPage.tsx` porque hacerlo requeriría un fetch contra un endpoint
  que no existe en el backend.
* **Impacto:** La feature de remediación construida en `LOG-US4-FE-03` es
  inalcanzable para el usuario final hasta que se resuelva este gap — existe como
  componente aislado y probado, pero no forma parte de ningún flujo navegable.
* **Sugerencia de resolución:** ticket de seguimiento (ej. `LOG-US4-BE-03` o
  `LOG-US4-FE-04`) que (a) implemente `GET /incidents/{id}` en el backend
  (`IncidentController` + caso de uso + mapeo a `IncidentDetail`, incluyendo el
  análisis/diagnóstico asociado con su `suggestedScript`), y (b) agregue el fetch +
  wiring de `RemediationPanel` en `IncidentDashboardPage.tsx` una vez ese endpoint
  exista.
* **Estado:** Cerrado (`LOG-US4-BE-03` implementó el endpoint; `LOG-US4-FE-04` montó
  `RemediationPanel` en `IncidentDashboardPage.tsx` vía `useIncidentDetail`)
* **Detectado:** 2026-08-11

---

### `DEBT-004`: `IncidentAnalysis.tokensUsed` y `Incident.updatedAt` sin fuente de datos real

* **Origen:** `LOG-US4-BE-03`
* **Descripción:** El contrato OpenAPI exige `IncidentAnalysis.tokensUsed` (integer,
  no-nullable) e `Incident.updatedAt`, pero ningún componente del backend los captura ni
  persiste. `GET /incidents/{id}` devuelve `tokensUsed` con un placeholder hardcodeado
  (`0`, documentado en Javadoc) y omite `updatedAt` del DTO de respuesta. El gap de
  `updatedAt` es preexistente (heredado de `LOG-US1-BE-02B`, donde tampoco se persistía),
  no introducido por este ticket.
* **Impacto:** El frontend no puede mostrar consumo real de tokens ni fecha de última
  actualización del incidente; cualquier UI que dependa de esos valores mostraría datos
  falsos (`tokensUsed: 0` siempre) o incompletos (campo ausente).
* **Sugerencia de resolución:** (1) Extender `IncidentDiagnostic`/`incident_diagnostics`
  para persistir el `Usage` de Spring AI capturado en `DiagnosticChatPort`. (2) Agregar
  `updated_at` a `incidents` (migración Flyway + trigger o `@PreUpdate`) y propagarlo al
  constructor de `Incident` (impacta 6 archivos del proyecto).
* **Estado:** Abierto
* **Detectado:** 2026-08-11

---

### `DEBT-005`: Primer arranque de la suite E2E depende de una descarga lenta de imagen/modelos de Ollama (si no hay Ollama nativo en el host)

* **Origen:** `LOG-US4-E2E-04`
* **Descripción:** `global-setup.ts` levantaba originalmente `db`, `ollama` y
  `backend` vía Docker Compose antes de correr la suite Playwright. En un
  entorno sin la imagen `ollama/ollama:latest` ni los modelos
  `llama3.1`/`nomic-embed-text` ya cacheados localmente, el primer arranque
  observado en este ticket avanzó a ~1.8 MB/s de descarga solo para la imagen
  base de Ollama (varios GB), con un tiempo de bootstrap estimado en el orden
  de una hora en la primera corrida.
  **Mitigado (ronda 2):** `global-setup.ts` ahora detecta, antes de tocar
  Docker, si ya hay una instancia nativa de Ollama corriendo en el host
  (`http://localhost:11434`, `GET /api/tags`, timeout 2s) con los modelos ya
  descargados. Si la detecta (**Plan A**), levanta solo `db` y `backend` con
  `docker compose ... --no-deps` + un override
  (`frontend/e2e/docker-compose.e2e-native-ollama.yml`) que redirige
  `SPRING_AI_OLLAMA_BASE_URL` a `http://host.docker.internal:11434`, evitando
  por completo el contenedor `ollama` y su descarga. Verificado en este
  entorno: corrida real de `npm run test:e2e` contra la pila completa
  (`db`+`backend` dockerizados + Ollama nativo del host) — **GREEN, 1
  passed, ~24s** (incluyendo build de la imagen del backend). Si NO se
  detecta Ollama nativo (**Plan B**), la suite hace `test.skip(...)` con un
  mensaje explícito en vez de intentar el contenedor `ollama` lento o fallar
  contra infraestructura inexistente.
* **Impacto:** Con Ollama nativo disponible en el host (caso cubierto y
  verificado), el arranque de la suite E2E es rápido y confiable. El
  contenedor `ollama` de `docker-compose.yml` sigue existiendo sin cambios
  para quien no tenga Ollama nativo instalado, pero en ese caso la suite
  ahora se salta explícitamente (Plan B) en vez de colgarse — sigue sin
  resolverse el caso de un entorno 100% containerizado sin Ollama nativo
  disponible (ej. runner de CI efímero), donde correr esta suite implicaría
  el mismo costo de descarga original.
* **Sugerencia de resolución (para el caso 100% containerizado, ej. CI):**
  (a) Pre-calentar la imagen/modelos de Ollama como paso separado de
  "warm-up" de infraestructura (fuera del alcance de `frontend/`, ej. un job
  de CI que corra `docker compose pull` + `ollama pull` una vez y cachee el
  volumen entre corridas); o (b) evaluar un perfil Ollama liviano dedicado a
  E2E con un modelo mucho más pequeño; o (c) instalar Ollama nativo en el
  runner de CI (fuera de Docker) para que el mismo Plan A aplique ahí.
  Ninguna de estas opciones es responsabilidad de `frontend/` en solitario —
  requiere una decisión de infraestructura/CI transversal.
* **Estado:** Mitigado (Plan A verificado GREEN con Ollama nativo; Plan B
  evita el bloqueo pero no resuelve el caso 100% containerizado sin Ollama
  nativo)
* **Detectado:** 2026-08-11
