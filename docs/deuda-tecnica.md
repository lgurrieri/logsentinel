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

---

### `DEBT-006`: Demo de Azure sin TLS — Basic Auth como única barrera pública

* **Origen:** Plan de despliegue en Azure (VM de demo)
* **Descripción:** `IaC/nginx/nginx.conf` sirve todo el tráfico público en texto
  plano por el puerto 80 (`listen 80`, sin bloque `443 ssl`), protegido solo por
  `auth_basic`. Credenciales y todo el tráfico (incluido el streaming SSE del
  diagnóstico) viajan sin cifrar entre el navegador de la audiencia y la VM.
* **Impacto:** Cualquiera en la misma red que un cliente de la demo podría
  interceptar las credenciales de Basic Auth o el contenido de los incidentes.
  Aceptable para una demo acotada a una franja horaria (10:30–16:15 ART) con
  audiencia conocida, pero no debe reusarse tal cual para un despliegue con
  usuarios reales.
* **Sugerencia de resolución:** agregar Let's Encrypt (Certbot con el plugin de
  Nginx, o un sidecar `caddy` que automatice HTTP-01) una vez que el DNS label
  de la VM sea estable, o terminar TLS en un Azure Application Gateway /
  Front Door delante de la VM.
* **Estado:** Abierto
* **Detectado:** 2026-08-12

---

### `DEBT-007`: Password de Postgres de la demo Azure sin Key Vault/Managed Identity

* **Origen:** `IaC/scripts/provision-vm.sh` / `IaC/scripts/cloud-init.yaml`
* **Descripción:** `POSTGRES_PASSWORD` se genera fuera de banda (a mano, por
  quien corre `provision-vm.sh`) y se inyecta en texto plano vía cloud-init
  (`--custom-data`) a dos archivos con permisos `600`
  (`/opt/logsentinel/backend/.env`, `/opt/logsentinel/.env`). El mismo valor se
  guarda además como GitHub Secret para que `cd.yml` lo reescriba en cada
  deploy. No hay rotación ni un origen único de verdad para el secreto.
* **Impacto:** El secreto vive duplicado en dos sistemas (GitHub Secrets +
  archivos en la VM) sin rotación automática; cualquiera con acceso de lectura
  a la VM (o a las variables de Automation Account/logs de run-command) puede
  verlo en texto plano. Aceptable para una demo de un día con un password
  random no reusado de dev, pero no escala a un entorno persistente.
* **Sugerencia de resolución:** mover el secreto a Azure Key Vault, habilitar
  una identidad administrada en la VM con permiso de lectura sobre ese Key
  Vault, y que el arranque del backend lo resuelva en runtime (via
  `azure-keyvault` Spring Cloud starter o un script de arranque que exporte la
  variable desde `az keyvault secret show`) en vez de bakearlo en un archivo.
* **Estado:** Abierto
* **Detectado:** 2026-08-12

---

### `DEBT-008`: Provisioning de Azure imperativo (az CLI), sin IaC reproducible

* **Origen:** `IaC/scripts/provision-vm.sh`
* **Descripción:** Todo el aprovisionamiento (resource group, budget, red, NSG,
  IP pública, VM, Automation Account, RBAC) es una secuencia de comandos
  `az ...` en un script bash, no una plantilla declarativa (Bicep/Terraform).
  Es idempotente por convención (nombres de recursos fijos, Azure actualiza en
  vez de duplicar) pero no hay `plan`/`diff` previo a aplicar cambios, ni
  estado versionado del despliegue.
* **Impacto:** Un cambio de infraestructura requiere leer y entender el script
  completo para saber qué va a pasar; no hay forma de previsualizar un diff
  antes de aplicarlo, ni de detectar drift entre lo declarado y lo real en el
  portal. Elegido deliberadamente para el día del deploy por menor riesgo de
  debug de sintaxis bajo presión de tiempo (ver el plan de despliegue de esta
  sesión) — no por preferencia a largo plazo.
* **Sugerencia de resolución:** migrar `provision-vm.sh` a un módulo Bicep (o
  Terraform, si el equipo ya lo usa en otro lado) versionado, con un pipeline
  de CI que corra `az deployment group what-if` en PRs que toquen `IaC/`.
* **Estado:** Abierto
* **Detectado:** 2026-08-12

---

### `DEBT-009`: `cd.yml` no podía llegar a la VM por SSH desde el runner de GitHub Actions

* **Origen:** `IaC/scripts/provision-vm.sh` (regla NSG `AllowSSHFromMyIP`,
  `source-address-prefixes` acotado a la IP personal de quien corrió el script).
* **Descripción:** El trigger automático de `cd.yml` en cada push a `main` reveló que
  el runner hosted de GitHub Actions (IP dinámica, no perteneciente al `/32` de la regla)
  no podía alcanzar el puerto 22 de la VM. Como primera reacción se amplió
  `source-address-prefixes` de `AllowSSHFromMyIP` a `*` para desbloquear el job
  `deploy` — **decisión revertida en el mismo día**, antes de llegar a producción, por
  exponer innecesariamente el puerto 22 a Internet (screening/fuerza bruta desde
  cualquier origen). La regla NSG volvió a `190.120.245.19/32`.
* **Impacto:** Mientras no se implemente el mecanismo de reemplazo, el job `deploy` de
  `cd.yml` no puede completar el despliegue automático (el paso "Configure SSH" vuelve
  a fallar contra la NSG restringida) — el pipeline queda efectivamente bloqueado hasta
  resolver este ítem.
* **Resolución:** migrado el job `deploy` a autenticar contra Azure vía OIDC federado
  (GitHub → Azure AD App Registration `logsentinel-cd-oidc` con Federated Identity
  Credential, sin secret de larga vida) y ejecutar los pasos con
  `az vm run-command invoke` en vez de SSH/SCP directo — la NSG permanece restringida a
  `190.120.245.19/32` sin bloquear el pipeline, porque el control plane de Azure (no la
  red de la VM) es el canal de ejecución. Ver `.github/workflows/cd.yml` (job `deploy`)
  e `IaC/scripts/setup-oidc.sh`.
* **Bugs encontrados y corregidos durante la verificación end-to-end:**
  1. El script remoto se abortaba en silencio: `az vm run-command invoke` ejecuta el
     script recibido por `sh` (dash en esta VM Ubuntu 24.04), que no soporta
     `set -o pipefail` y aborta el script entero al toparse con esa opción inválida
     (semántica POSIX de special builtins). Fix: shebang `#!/usr/bin/env bash` como
     primera línea literal del heredoc, para que `run-command` re-ejecute bajo bash.
  2. `az vm run-command invoke` reporta `ProvisioningState/succeeded` y exit code 0
     **aunque el script que ejecuta falle** (verificado con un `exit 1` deliberado a
     mitad de script) — la extensión solo confirma que pudo lanzar el script, no que
     terminó bien. Fix: sentinel `echo "DEPLOY_OK"` como última línea del script remoto
     + `grep` de ese sentinel en el step de Actions, que falla explícitamente
     (`exit 1` + `::error::`) si no aparece.
* **Verificación:** commit `0ac57bc`, CD run `31643808992` completó en verde. Confirmado
  de forma independiente en la VM (no solo confiando en el verde de CI): `.env` con el
  `BACKEND_IMAGE` actualizado al SHA correcto, los 4 servicios `up`/`healthy` en
  `docker compose ps`, `/actuator/health` del backend con `status: UP`, y la URL pública
  (`http://logsentinel-demo-53e60d.brazilsouth.cloudapp.azure.com/`) respondiendo
  `401` sin credenciales (Basic Auth de Nginx activo y alcanzable desde Internet).
* **Estado:** Cerrado
* **Detectado:** 2026-08-12
* **Cerrado:** 2026-08-12

---

### `DEBT-010`: El prompt del diagnóstico no le pide al LLM un bloque de código Markdown — `suggestedScript` queda `null` en la práctica

* **Origen:** Verificación end-to-end en producción de `LOG-US3-BE-04` (creación de 3
  incidentes reales vía `az vm run-command invoke`, sin SSH, contra el contenedor
  `backend` directo).
* **Descripción:** `StreamDiagnosticService.buildSystemPrompt`/`buildUserPrompt` piden
  un diagnóstico grounded en los runbooks recuperados, pero **no instruyen en ningún
  lado** al LLM a emitir el comando de remediación dentro de un bloque de código
  Markdown (```` ``` ````). `SuggestedScriptExtractor.extract` (regex
  ` ```[^\n\r]*\r?\n(.*?)``` `) es permisivo (acepta con o sin tag de lenguaje, toma
  el primer fence), pero si el LLM nunca genera un fence —como ocurre consistentemente
  con el runbook seedeado de `auth-service`, cuyo propio texto usa comillas invertidas
  simples inline ("ejecutando: `echo '...'`")— la extracción no tiene nada que
  parsear y devuelve `null` siempre, sin importar cuán correcto sea el diagnóstico.
* **Impacto:** Verificado en vivo: 3/3 generaciones reales contra Ollama
  (llama3.1) en la VM de demo devolvieron `diagnosticOutput` correcto y persistido
  (confirmando que `LOG-US3-BE-04` funciona), pero `suggestedScript: null` en los 3
  casos — el LLM parafraseó el runbook con comillas invertidas simples en vez de un
  fence. Esto bloquea completamente el flujo feliz de US4 (`POST
  /incidents/{id}/remediations` devuelve 409 sin `suggestedScript`): no se puede
  ejercitar la ejecución de remediación en la demo con el runbook actual tal como
  está seedeado, aunque el pipeline de diagnóstico (US2/US3) funcione end-to-end.
* **Sugerencia de resolución:** agregar una instrucción explícita al `systemPrompt`
  (ej. "si recomendás un comando, envolvelo siempre en un bloque \`\`\`bash \`\`\`
  al final de tu respuesta") y/o reescribir el contenido semilla de
  `runbook_chunks` para que el propio runbook modele el formato esperado (few-shot
  implícito) — el LLM tiende a imitar el estilo del contexto recuperado. Requiere
  ticket dedicado con TDD (test de `SuggestedScriptExtractor`/`StreamDiagnosticService`
  fijando el prompt esperado) dado que es un cambio de comportamiento, no solo de
  documentación.
* **Resolución:** `LOG-US3-BE-05` (PR #6, commit `5fb3030`, merge `1b822e5`) extendió
  `StreamDiagnosticService.buildSystemPrompt` con la instrucción de formato descrita
  arriba, sin tocar el seed de `runbook_chunks` (no existe ningún script de seed
  versionado en el repo — el `/tmp/seed-runbook.sh` usado en la verificación era
  efímero, no comiteado).
* **Verificación:** confirmado en producción tras el deploy automático de `cd.yml`
  (incidente `f0b24e91-a767-45d7-9d04-d35c2d521a81`, imagen
  `ghcr.io/lgurrieri/logsentinel-backend:1b822e5e12410e27ac22c9a956b16b8254701116`):
  el mismo runbook de `auth-service` que antes producía `suggestedScript: null` ahora
  devuelve `"suggestedScript":"echo 'auth-service pool recycled'"` — el LLM envolvió el
  comando en un bloque \`\`\`bash tal como se le instruyó.
* **Estado:** Cerrado
* **Detectado:** 2026-08-12
* **Cerrado:** 2026-08-13

---

### `DEBT-011`: `useIncidentDetail` no re-consulta tras completarse el streaming SSE — el panel de remediación queda bloqueado en el flujo feliz real

* **Origen:** Grabación de un video de demo de un caso OK contra la VM de producción:
  crear un incidente nuevo y navegar de inmediato a `/incidents/{id}/dashboard` (el
  camino real de un usuario, no uno con el diagnóstico ya generado de antes).
* **Descripción:** `useIncidentDetail.ts` (líneas 31-57) dispara `GET
  /api/v1/incidents/{id}` **una única vez** al montar, con `[incidentId]` como única
  dependencia del `useEffect`. `DiagnosticTerminal`/`useDiagnosticStreamConnection`
  (el streaming SSE) es un componente completamente independiente que nunca invalida
  ni retrigguea este hook cuando el diagnóstico termina de generarse (~60-90s vía
  Ollama en la VM de demo). Como el `GET` corre antes de que exista el análisis,
  `analyses` llega vacío y `suggestedScript` queda `null` en el estado de React para
  siempre, aunque el backend sí termine y persista el diagnóstico correctamente
  (confirmado con `curl` directo: `suggestedScript: "echo 'auth-service pool
  recycled'"` ya persistido mientras la UI seguía mostrando "No hay ningún script de
  remediación disponible").
* **Impacto:** Bloquea el flujo feliz real de principio a fin (crear incidente → ver
  su dashboard) — no un caso extremo. `RemediationPanel` solo funciona hoy si el
  usuario recarga la página manualmente después de que el diagnóstico ya terminó, o
  si navega a un incidente cuyo diagnóstico ya estaba persistido de antes. Reproducido
  y verificado end-to-end contra producción (incidente `e4274228-f172-4b0f-85d4-
  f28fc0f28aeb`): el botón "Ejecutar Script de Remediación" nunca se habilitó en 4
  minutos de espera en la primera carga; sí se habilitó de inmediato al recargar la
  página una vez el diagnóstico ya estaba persistido.
* **Sugerencia de resolución:** hacer que la finalización del stream SSE
  (`onComplete` en `useDiagnosticStreamConnection`/`DiagnosticStreamContext`)
  dispare un refetch de `useIncidentDetail`, en vez de que este último dependa solo
  de `incidentId`. Requiere ticket dedicado con TDD (RED: test que falle mostrando
  que `suggestedScript` sigue `null` tras completarse el stream mockeado; GREEN:
  wiring del refetch).
* **Resolución:** `LOG-US3-FE-06` extendió `useIncidentDetail` con un segundo
  parámetro `diagnosticSettled: boolean` incluido en el array de dependencias de su
  `useEffect`, y levantó `DiagnosticStreamProvider` en `IncidentDashboardPage.tsx`
  para envolver toda la página (antes solo envolvía `DiagnosticTerminal`), derivando
  `diagnosticSettled` de `useDiagnosticStream().state.status` (`'COMPLETED'` o
  `'STREAM_FAILED'`). `DiagnosticStreamContext`, `useDiagnosticStreamConnection` y
  `DiagnosticTerminal` no se modificaron. Ciclo TDD completo: RED #1
  (`useIncidentDetail.test.ts`, un `rerender` con `settled: true` que probaba que el
  hook nunca reconsultaba), GREEN #1 (el cambio de firma/deps del hook), RED #2
  (`IncidentDashboardPage.test.tsx`, test de integración con `EventSource` mockeado
  reproduciendo el bug end-to-end), GREEN #2 (la restructuración del componente en
  `IncidentDashboardPage` + `IncidentDashboardContent`), REFACTOR (se extrajo el
  mock de `EventSource` duplicado en 3 test files a
  `frontend/src/features/incidents/testUtils/mockEventSource.ts`).
* **Verificación:** suite completa de frontend en verde (`npx vitest run`: 20 test
  files, 111 tests) y `npm run build` sin errores de tipos, tras el refactor.
* **Estado:** Cerrado
* **Detectado:** 2026-08-13
* **Cerrado:** 2026-08-13
