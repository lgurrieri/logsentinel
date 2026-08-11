
## 🏗️ ÉPICA CORE: Infraestructura y Arquitectura Transversal

### `LOG-CORE-INFRA-00`: Configuración de Infraestructura, Dockerización y CI/CD Base

* **Descripción:** Configurar el entorno base del proyecto utilizando contenedores Docker para garantizar la paridad absoluta de entornos (Local/CI/Production).
* **Criterios de Aceptación Técnicos:**
* Crear un `Dockerfile` multi-stage para el backend (Spring Boot/Java) garantizando que el contenedor corra bajo un usuario **non-root** por motivos de seguridad.
* Configurar un archivo `docker-compose.yml` local que levante una base de datos PostgreSQL con soporte nativo para la extensión vectorial (`pgvector`).
* Diseñar el pipeline base de CI/CD (GitHub Actions) que ejecute lints y valide la compilación en cada Pull Request.



### `LOG-CORE-BE-00`: Inicialización del Proyecto Backend y Clientes de IA

* **Descripción:** Proveer el esqueleto del backend con las dependencias core y la integración inicial con Spring AI (`ChatClient`/`EmbeddingModel`), sin acoplarse a ningún SDK nativo de proveedor.
* **Criterios de Aceptación Técnicos:**
* Inicializar el proyecto Spring Boot con dependencias para Web, JPA, Validation y PostgreSQL.
* Configurar Spring AI inyectando de forma segura las credenciales de proveedor (API keys, base URLs) utilizando variables de entorno de sistema.
* **Automatización del DoD:** Configurar **JaCoCo** en el archivo de construcción del proyecto (`pom.xml` o `build.gradle`) parametrizado de manera estricta para **romper el build si la cobertura general baja del 95%**.



### `LOG-CORE-INFRA-01`: Proveedor de IA Local por Defecto (Ollama) con OpenAI como Perfil Opcional

* **Descripción:** Hardening de infraestructura sobre `LOG-CORE-BE-00`: evitar que el desarrollo local y el CI dependan de una API key de OpenAI real, introduciendo **Ollama** como proveedor de IA local por defecto (chat + embeddings) y preservando OpenAI como perfil opcional para despliegues cloud (prod/staging).
* **Criterios de Aceptación Técnicos:**
* Agregar `spring-ai-starter-model-ollama` al `pom.xml`, gestionado por el `spring-ai-bom` ya presente, sin eliminar `spring-ai-starter-model-openai`.
* Separar la configuración de IA en perfiles Spring Boot ortogonales al entorno (`application-ollama.yml` / `application-openai.yml`), seleccionando el bean activo vía `spring.ai.model.chat` / `spring.ai.model.embedding` para evitar `NoUniqueBeanDefinitionException` al convivir ambos starters en el classpath.
* `docker-compose.yml` levanta un servicio `ollama` local (perfil `ollama` activo por defecto en `dev`) con healthcheck y volumen persistente para el modelo descargado.
* Ningún flujo de desarrollo local ni de CI debe requerir `SPRING_AI_OPENAI_API_KEY` para arrancar o testear el backend.



---

## 🛑 US1: Declaración e Ingesta de Incidentes Críticos

### Narrativa

> **Como** Ingeniero de Confiabilidad del Sitio (SRE)
> **Quiero** registrar un nuevo incidente mediante un payload estructurado que incluya el volcado de logs y su urgencia
> **Para** que el motor RAG e IA dispongan del contexto inicial necesario para generar un diagnóstico accionable en menos de 60 segundos, minimizando el MTTR operativo.

### Tickets de Trabajo (US1)

#### `LOG-US1-DB-01`: Modelo de Persistencia Relacional y Restricciones Defensivas

* **Descripción:** Crear la tabla física `incidents` en PostgreSQL implementando restricciones duras a nivel de esquema para evitar datos corruptos.
* **Criterios de Aceptación Técnicos:**
* Generar un script de migración (Flyway/Liquibase) para la tabla `incidents` (id, system_name, urgency, raw_logs, status, created_at).
* Incluir **`CHECK CONSTRAINTS`** en la base de datos para asegurar que la columna `urgency` solo acepte valores válidos (`'LOW'`, `'MEDIUM'`, `'HIGH'`, `'CRITICAL'`).



#### `LOG-US1-BE-02`: API Rest de Ingesta y Controlador Global de Excepciones

* **Descripción:** Desarrollar el endpoint REST `POST /api/v1/incidents` blindado contra payloads inválidos o maliciosos.
* **Criterios de Aceptación Técnicos:**
* Implementar validación de datos en el controlador mediante anotaciones **JSR-380** (`@NotNull`, `@NotBlank`, `@Size`).
* Desarrollar un `@ControllerAdvice` global para interceptar fallos de validación y transformarlos en respuestas limpias (HTTP 400 Bad Request).
* Diseñar algoritmos estrictamente lineales en el backend para mitigar vectores de ataques de denegación de servicio (DoS) al parsear logs masivos.



#### `LOG-US1-BE-02B`: Capa de Servicios Inmutables y DTOs de Desacoplamiento

* **Descripción:** Introducir una arquitectura limpia desacoplando por completo el frontend y el transporte de datos de las entidades de persistencia de la base de datos.
* **Criterios de Aceptación Técnicos:**
* Crear clases de transferencia de datos de entrada (`CreateIncidentRequest`) y salida (`IncidentResponse`) inmutables.
* Garantizar mediante pruebas unitarias que el controlador web nunca interactúe o exponga la entidad JPA pura de base de datos.



#### `LOG-US1-FE-03`: Formulario de Reporte de Incidentes de Alta Disponibilidad

* **Descripción:** Desarrollar la interfaz gráfica reactiva para la ingesta de incidentes, garantizando el control de estados en el cliente y la validación estricta pre-envío.
* **Especificaciones de Componentes y UI:**
* **Componente Formulario:** Un contenedor semántico `<form>` que incluya:
* Un elemento `<select>` o desplegable para `systemName` (ej: `["payment-gateway", "auth-service", "inventory-api"]`).
* Un grupo de botones de opción (`Radio Group` o botones estilizados) para `urgency` (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`), utilizando un código de color visual descriptivo (ej: gris para `LOW`, rojo carmesí `#d32f2f` para `CRITICAL`).
* Un campo `<textarea>` para `rawLogs` con tipografía monoespaciada (`font-family: SFMono-Regular, Consolas, monospace`) y un contador dinámico de caracteres en la esquina inferior derecha.




* **Manejo de Estados del Cliente:**
* Definir un objeto de estado local inmutable `formData` con las propiedades requeridas.
* Definir un estado de ciclo de vida de UI: `uiState: 'IDLE' | 'SUBMITTING' | 'SUCCESS' | 'SERVER_ERROR'`.
* Definir un objeto de errores locales `formErrors: { systemName?, urgency?, rawLogs? }`.


* **Reglas de Validación Pre-envío (Client-Side):**
* `systemName`: No puede estar vacío.
* `urgency`: Debe seleccionarse obligatoriamente una opción.
* `rawLogs`: Obligatorio, longitud mínima de 20 caracteres y máxima de 100,000 caracteres. Si el usuario intenta pegar un log mayor, bloquear el teclado, truncar visualmente el texto y mostrar un texto de advertencia flotante.


* **Comportamiento UX y Control de Concurrencia:**
* **Prevención de Doble Envío (Double-Submit):** Mientras `uiState === 'SUBMITTING'`, todos los campos de entrada y el botón principal de envío deben recibir el atributo `disabled={true}`. El texto del botón de envío cambiará a un spinner de carga animado.
* **Manejo de Errores de Red/Servidor:** Si el backend responde con un HTTP 400 o 500, transicionar `uiState` a `'SERVER_ERROR'` y renderizar en la parte superior del formulario un banner informativo visible con `role="alert"`, mostrando textualmente el mensaje JSON de error devuelto por la API.
* **Flujo deÉxito:** Al recibir un HTTP 201 (Created), el cliente extraerá el `id` del incidente devuelto en la respuesta, limpiará el formulario y ejecutará una redirección por enrutador local hacia la pantalla de diagnóstico: `/incidents/{id}/dashboard`.



---

## 🔍 US2: Búsqueda Semántica de Runbooks Mediante IA (Embeddings)

### Narrativa

> **Como** Sistema Automatizado LogSentinel
> **Quiero** generar el embedding vectorial del log ingerido y consultar una base de datos de conocimiento
> **Para** recuperar de manera semántica los 3 runbooks históricos de remediación más relevantes para el problema actual.

### Tickets de Trabajo (US2)

#### `LOG-US2-DB-01`: Extensión Vectorial pgvector y Almacenamiento de Runbooks

* **Descripción:** Configurar el esquema para guardar vectores embebidos en PostgreSQL y habilitar búsquedas espaciales eficientes.
* **Criterios de Aceptación Técnicos:**
* Habilitar la extensión `CREATE EXTENSION IF NOT EXISTS vector;` en la base de datos a través de migraciones de código.
* Crear la tabla `runbook_chunks` (fragmentos vectorizados) agregando una columna de tipo `vector(N)`, donde N coincide con la dimensión del modelo de embeddings activo (768 por defecto con Ollama/`nomic-embed-text`; 1536 si el perfil `openai` está activo). Cambiar de proveedor luego de tener datos persistidos requiere backfill/re-embedding, no es un cambio de config en caliente.
* Crear un índice de tipo `HNSW` con métrica de distancia de coseno para optimizar las consultas a gran escala.



#### `LOG-US2-BE-02`: Búsqueda Semántica con Estrategia Fallback Full-Text

* **Descripción:** Implementar la lógica del servicio encargado de transformar texto en vectores y coordinar la estrategia resiliente de búsqueda.
* **Criterios de Aceptación Técnicos:**
* Desarrollar el servicio encargado de invocar el `EmbeddingModel` de Spring AI (Ollama local por defecto; OpenAI vía perfil opcional) para vectorizar el log del incidente.
* Diseñar la query nativa JPA que extraiga los runbooks con mayor proximidad vectorial utilizando operadores de similitud de coseno (`<=>`).
* Implementar un bloque defensivo `try-catch`: si la llamada remota de embeddings falla por timeout o cuotas, activar inmediatamente una búsqueda tradicional Full-Text (`tsvector`) en la base relacional para que el sistema nunca retorne un arreglo vacío.



#### `LOG-US2-TEST-03`: Suite de Pruebas de Integración Vectorial con Testcontainers

* **Descripción:** Validar que la base de datos vectorial y las queries nativas funcionen correctamente usando entornos de prueba reales descartables.
* **Criterios de Aceptación Técnicos:**
* **Automatización del DoD:** Queda estrictamente prohibido usar bases de datos embebidas falsas en memoria (como H2) para probar este componente.
* Configurar **Testcontainers** para levantar dinámicamente un contenedor Docker real de PostgreSQL enriquecido con `pgvector` antes de ejecutar la suite de integración.



---

## 📊 US3: Diagnóstico en Tiempo Real y Streaming de Logs

### Narrativa

> **Como** Ingeniero SRE
> **Quiero** ver el diagnóstico detallado y el análisis causa-raíz generado por la IA en flujo continuo en una terminal web interactiva
> **Para** comprender la falla de inmediato sin tener que esperar minutos a que termine de procesarse por completo la respuesta gigante del LLM.

### Tickets de Trabajo (US3)

#### `LOG-US3-BE-01`: Endpoint de Streaming de Diagnósticos vía Server-Sent Events (SSE)

* **Descripción:** Configurar un endpoint reactivo que consuma el streaming del `ChatClient` de Spring AI (Ollama local por defecto; OpenAI vía perfil opcional) y lo exponga en tiempo real hacia el navegador.
* **Criterios de Aceptación Técnicos:**
* Desarrollar el endpoint `GET /api/v1/incidents/{id}/diagnostic/stream` utilizando arquitecturas asíncronas no bloqueantes.
* Consumir el `ChatClient` de Spring AI configurando el flag `stream = true`, provider-agnostic respecto al perfil activo (`ollama`/`openai`).
* Canalizar secuencialmente los fragmentos de texto recibidos emitiendo eventos con el tipo de contenido estándar `text/event-stream`.



#### `LOG-US3-DB-02`: Persistencia de Históricos de Análisis de IA

* **Descripción:** Diseñar el esquema de base de datos necesario para congelar y auditar el diagnóstico completo consolidado de la IA una vez concluido el streaming.
* **Criterios de Aceptación Técnicos:**
* Crear la tabla `incident_diagnostics` enlazada uno a uno con `incidents`.
* Asegurar que el backend salve el texto completo unificado al cerrarse exitosamente el canal SSE.



#### `LOG-US3-DB-02B`: Captura Estructurada del Script de Remediación Sugerido

* **Descripción:** Extender la persistencia del diagnóstico congelado (`LOG-US3-DB-02`) para capturar, en el mismo instante de la generación (cierre exitoso del stream SSE), el bloque de código de remediación sugerido por la IA como un campo estructurado independiente — en vez de que `LOG-US4-BE-02` dependa de que el cliente reenvíe el script o de parsearlo en el momento de la ejecución. Decisión de diseño (Opción B, aprobada 2026-08-11): el backend deriva y persiste el script de forma autoritativa al generar el diagnóstico; el cliente nunca provee código ejecutable en el flujo de remediación.
* **Criterios de Aceptación Técnicos:**
* Agregar la columna `suggested_script` (`TEXT`, nullable) a la tabla `incident_diagnostics` vía migración Flyway (próxima versión disponible; coordinar numeración con la migración pendiente de `LOG-US4-BE-02`, `V6__create_remediation_actions_table.sql`, si esta se commitea primero).
* Extender el modelo de dominio `IncidentDiagnostic` (y su `IncidentDiagnosticJpaEntity` / `IncidentDiagnosticPersistenceAdapter`) con el campo `suggestedScript` (`String`, nullable).
* Implementar un componente de dominio puro (ej. `SuggestedScriptExtractor`) que reciba el `diagnosticText` consolidado y extraiga el contenido del primer bloque de código Markdown delimitado por triple backtick (con o sin hint de lenguaje, ej. ` ```bash `, ` ```yaml `, ` ``` ` a secas). Si no existe ningún bloque de código, o el bloque no está correctamente cerrado, el resultado es `null` — no adivinar ni concatenar texto ambiguo.
* Cobertura de tests unitarios obligatoria para el extractor cubriendo como mínimo: bloque con hint de lenguaje, bloque sin hint, múltiples bloques (documentar y testear la regla determinística elegida — se usa el primero), texto sin ningún bloque de código, y bloque sin cierre (backtick faltante).
* Integrar el extractor en `StreamDiagnosticService.persistDiagnostic(...)` para poblar `suggestedScript` antes de guardar, sin alterar el comportamiento de streaming ya existente hacia el cliente (el parseo ocurre una sola vez sobre el texto ya consolidado, nunca fragmento a fragmento).
* Actualizar el contrato OpenAPI: agregar `suggestedScript` (string, nullable) al schema `IncidentAnalysis`.
* Actualizar los tests existentes que referencian el constructor/factory actual de 4 argumentos de `IncidentDiagnostic` (`StreamDiagnosticServiceTest`, `IncidentDiagnosticPersistenceAdapterIntegrationTest` con Testcontainers, y cualquier otro que rompa), sin dejar ningún test roto.
* Ciclo TDD obligatorio (RED → GREEN → REFACTOR) para cada pieza nueva — en particular el extractor, que debe nacer de un test que falle primero por cada caso límite listado arriba.



#### `LOG-US3-FE-03`: Consola Terminal Interactiva en Frontend

* **Descripción:** Desarrollar el componente visual de terminal interactiva encargado de conectarse al stream de datos, procesar Markdown al vuelo y asegurar un renderizado eficiente libre de parpadeos o saltos de pantalla (Layout Shift).
* **Especificaciones de Componentes y UI:**
* **Componente Terminal Consola:** Un contenedor `<div>` rígido con un fondo negro profundo (`#0d1117`), texto en color verde fósforo (`#39ff14`) o blanco hueso, bordes redondeados y una barra de desplazamiento vertical interna (`overflow-y: auto`).
* En la posición final del texto en streaming, renderizar un cursor vertical parpadeante (`_` o `|`) controlado mediante animaciones CSS nativas (`@keyframes blink { 50% { opacity: 0; } }`) para dar la sensación real de una terminal Unix.


* **Gestión de la Conectividad Server-Sent Events (SSE):**
* Al inicializar el componente mediante el ciclo de vida nativo (ej: `useEffect` o `mounted`), instanciar el canal de escucha: `const eventSource = new EventSource('/api/v1/incidents/${incidentId}/diagnostic/stream');`.
* Capturar los tokens entrantes implementando el manejador `eventSource.onmessage`. Cada fragmento de texto debe concatenarse inmediatamente a una variable de estado reactivo `diagnosticContent`.
* **Control Defensivo de Desconexiones:** Escuchar `eventSource.onerror`. Si la conexión cae de manera prematura sin haber recibido la señal de cierre exitoso del servidor, el frontend debe congelar el texto actual, mostrar un mensaje discreto de *"Reconectando..."* e implementar una estrategia de reintento con respaldo exponencial (Backoff) limitado a 3 intentos antes de marcar el estado de la interfaz como `'STREAM_FAILED'`.


* **Procesamiento de Texto y Optimización de Layout (UX):**
* El string acumulado en `diagnosticContent` vendrá formateado en Markdown desde el LLM. El componente debe transformar este string a elementos HTML limpios utilizando una librería de parsing directo (ej: `marked`), sanitizando obligatoriamente la salida con `DOMPurify` para evitar inyecciones XSS.
* **Algoritmo de Auto-Scroll Inteligente:** La terminal debe empujar el scroll automáticamente hacia el fondo de la pantalla (`element.scrollTop = element.scrollHeight`) cada vez que se concatene un nuevo token, **únicamente si el usuario se mantiene al fondo de la terminal**. Si el SRE mueve manualmente la barra de scroll hacia arriba para inspeccionar una línea de código previa, el auto-scroll automático se desactivará de inmediato para no interrumpir la lectura, y aparecerá un botón flotante superpuesto que diga *"Nuevas líneas disponibles abajo ↓"*.
* Para prevenir el molesto fenómeno de **Cumulative Layout Shift (CLS)**, el contenedor del diagnóstico debe poseer un tamaño mínimo establecido y un contenedor de altura controlada de antemano. El texto fluirá dentro del viewport interno sin deformar el resto del layout de la página principal.



---

## ⚡ US4: Ejecución Segura de Scripts de Remediación y Auditoría

### Narrativa

> **Como** Ingeniero de Confiabilidad del Sitio (SRE)
> **Quiero** dar luz verde a la ejecución de un script de remediación recomendado por la IA de forma aislada y auditable
> **Para** mitigar el impacto del incidente de forma inmediata, garantizando que quede un registro inmutable de qué se alteró y quién lo autorizó.

### Tickets de Trabajo (US4)

#### `LOG-US4-BE-01`: Motor de Ejecución en Sandbox Seguro (`SecuritySandbox`)

* **Descripción:** Implementar el componente núcleo encargado de invocar subprocesos del sistema operativo bajo aislamiento estricto y control de tiempos.
* **Criterios de Aceptación Técnicos:**
* Desarrollar la interfaz `SecuritySandbox` con el método `executeInIsolation(String script, long timeout, TimeUnit unit)`.
* Configurar aislamiento: comandos prohibidos mediante Allowlist y ejecución bajo un usuario del sistema **non-root** restringido.
* **Control de Watchdog:** Acoplar obligatoriamente un hilo de control tipo Watchdog de fondo que destruya forzosamente el subproceso operativo (`process.destroyForcibly()`) si se excede el tiempo límite parametrizado (timeout).



#### `LOG-US4-BE-02`: Máquina de Estados Transaccional Separada para Auditoría

* **Descripción:** Asegurar que el registro de auditoría de los scripts sea inmune a caídas catastróficas del hilo principal del backend.
* **Criterios de Aceptación Técnicos:**
* Crear la tabla `remediation_actions` para capturar metadatos, comandos y respuestas.
* Diseñar la máquina de estados operando con transacciones independientes secuenciales configuradas mediante **`Propagation.REQUIRES_NEW`**.
* **Flujo Transaccional:** 1. Transacción A (Commit Inmediato de estado `EXECUTING`) $\rightarrow$ 2. Fase de ejecución aislada libre en Sandbox $\rightarrow$ 3. Transacción B (Commit de Cierre con buffers finales de `stdout`/`stderr` en estado `SUCCESS` o `FAILED`).
* **Nota (issue documental, parcialmente resuelto 2026-08-11):** el contrato OpenAPI (`RemediationAction.executionStatus`) ya incluye el estado intermedio `EXECUTING`. Sigue abierto: no existe todavía un endpoint de consulta/streaming del estado de auditoría en progreso que `LOG-US4-FE-03` requiere (polling/SSE) — resolver al implementar ese ticket.
* **Resolución del drift de contrato (2026-08-11, Opción B):** `POST /incidents/{id}/remediations` no recibe `requestBody`. El controller obtiene `generatedScript` leyendo `IncidentDiagnostic.suggestedScript` (`LOG-US3-DB-02B`) del diagnóstico persistido asociado al incidente (relación uno a uno vía `incident_id`). Si no existe diagnóstico persistido para el incidente, o `suggestedScript` es `null` (la IA no generó un bloque de código parseable), el endpoint responde `409 Conflict` sin crear ningún registro en `remediation_actions`. Depende de `LOG-US3-DB-02B` — no puede cerrarse antes.



#### `LOG-US4-TEST-03`: Automatización contra Matriz de 5 Vectores de Inyección Bash

* **Descripción:** Blindar el sistema contra atacantes que manipulen los scripts automatizando pruebas de penetración a nivel de código.
* **Criterios de Aceptación Técnicos:**
* **Automatización del DoD:** Escribir una suite dedicada de pruebas unitarias que someta al componente de sanitización a una **matriz estricta de 5 vectores comunes de inyección Bash** (`|`, `&&`, `$(...)`, `>`, y comillas traseras).



#### `LOG-US4-FE-03`: Panel de Autorización y Monitor de Ejecución de Scripts

* **Descripción:** Construir la interfaz de control de acciones críticas que visualice la propuesta de remediación de la IA, capture la firma de aprobación, gestione la petición HTTP no bloqueante y segregue los flujos de consola en tiempo real.
* **Especificaciones de Componentes y UI:**
* **Caja de Código Estática (Code Block Component):** Un contenedor que presente el script Bash sugerido por la IA en un formato de "Solo Lectura". Debe implementar resaltado de sintaxis sintáctico (Syntax Highlighting) y un botón interactivo superior de *"Copiar al portapapeles"* que cambie temporalmente a un check tipográfico `✓ Copiado` al ser presionado.
* **Botón de Acción Principal (CTA):** Un botón prominente de peligro/advertencia etiquetado como *"Ejecutar Script de Remediación"*.
* **Ventana de Confirmación Modal (Doble Aprobación):** Al hacer clic en el botón, no se dispara el script de forma inmediata; se debe abrir un cuadro de diálogo modal accesorio (`aria-modal="true"`, `role="dialog"`) que fuerce el foco del teclado y muestre una advertencia explícita: *"¿Confirmas la ejecución de este comando en el sistema de producción? Esta acción quedará registrada bajo tu firma de auditoría"*, junto con dos opciones claras: `[ Confirmar Ejecución ]` e `[ Cancelar ]`.
* **Terminal Secundaria de Salida (Output Monitor):** Un panel inferior de consola inicialmente vacío que aparecerá al arrancar el proceso.


* **Manejo de Ciclo de Vida y Estados en Frontend:**
* Definir un estado de control reactivo: `executionStatus: 'READY' | 'CONFIRMING' | 'EXECUTING' | 'EXECUTION_SUCCESS' | 'EXECUTION_FAILED'`.
* Mientras `executionStatus === 'EXECUTING'`, el botón principal de la interfaz y los controles de cierre de la pantalla deben quedar bloqueados por completo (`disabled={true}`) para imposibilitar solicitudes duplicadas o la interrupción del ciclo desde el cliente.


* **Integración con la API y Pintado Separado de Flujos:**
* Al hacer clic en confirmar dentro del modal, la interfaz cambiará inmediatamente `executionStatus` a `'EXECUTING'` y lanzará una petición `POST /api/v1/incidents/{id}/remediations`.
* Para mantener la terminal actualizada sin congelar el hilo principal de renderizado, el frontend activará una rutina asíncrona de consulta continua (Short Polling cada 800ms) o una subscripción SSE paralela al endpoint de logs de auditoría.
* **Formateo de Consola Defensivo:** El contenido mapeado dentro de la Terminal de Salida debe diferenciar drásticamente el tipo de buffer recibido:
* Las líneas capturadas desde la salida estándar estándar del backend (`stdout`) se renderizarán en tipografía gris claro o blanca ordinaria.
* Las líneas procedentes de la salida de error del sistema operativo (`stderr`) se interceptarán y pintarán al vuelo en un **color rojo brillante de alerta (`#ff3333`) antepuestas por la etiqueta rígida `[ERROR]**`, garantizando que el operador identifique fallos en el script de manera visual e inmediata.


* Al finalizar, el frontend cambiará `executionStatus` a `'EXECUTION_SUCCESS'` o `'EXECUTION_FAILED'` basándose en el código de salida HTTP o el código numérico de salida de proceso (`exitCode: 0` para éxito, mayor a 0 para error), liberando la UI y pintando un indicador visual definitivo de conclusión.



#### `LOG-US4-E2E-04`: Orquestación de Tests End-to-End con Playwright

* **Descripción:** Implementar el test de integración definitivo que emule el camino feliz completo (Happy Path) del usuario interactuando con toda la plataforma de manera automatizada.
* **Criterios de Aceptación Técnicos:**
* **Automatización del DoD:** Configurar un pipeline de tests E2E utilizando **Playwright**. El script debe encargarse de levantar y apagar automáticamente toda la infraestructura local de pruebas frontend, backend y base de datos relacional.