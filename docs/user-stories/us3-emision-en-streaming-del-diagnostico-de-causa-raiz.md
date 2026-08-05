## US3: Emisión en Streaming del Diagnóstico de Causa Raíz (IA)

### Narrativa

> **Como** Ingeniero SRE
> **Quiero** visualizar en tiempo real y de forma progresiva el diagnóstico redactado por la IA
> **Para** comprender la raíz del problema inmediatamente sin esperar a que finalice la generación completa del texto.

### Evaluación INVEST

* **Independent:** Requiere el contexto recuperado en US2, pero la gestión de la conexión y el búfer web es una funcionalidad de red aislada.
* **Negotiable:** El formato del streaming es texto plano estructurado en Markdown.
* **Valuable:** Altísima para la experiencia de usuario (UX) en situaciones de alta tensión.
* **Estimable:** Complejidad media debido al manejo asíncrono e hilos en Spring Boot.
* **Small:** Se enfoca puramente en el transporte reactivo token a token desde el LLM hacia la interfaz del usuario.
* **Testable:** Sí. Pruebas de integración verificando la cabecera `text/event-stream` y la persistencia final del texto consolidado.

### Criterios de Aceptación (Gherkin)

```gherkin
Escenario: Consumo interactivo del diagnóstico vía Server-Sent Events
  Dado que un cliente Frontend establece una conexión GET al endpoint "/api/v1/incidents/{id}/stream"
  Cuando el orquestador de Spring Boot comienza a recibir tokens desde la API del LLM
  Entonces el servidor debe mantener la conexión abierta enviando eventos con la cabecera "Content-Type: text/event-stream"
  Y al finalizar la transmisión, el texto completo del diagnóstico debe quedar persistido en "incident_analyses".

```

### Especificación Técnica de Implementación

* **Backend (Spring Boot Controller):** Retorno de un objeto de tipo `SseEmitter`. La lógica del servicio se ejecuta en un hilo asíncrono (`@Async`) consumiendo la API del LLM configurando la propiedad `stream = true`.
* **Manejo de Memoria:** Al terminar el ciclo de tokens, se ejecuta `emitter.complete()` dentro de un bloque `try-catch-finally` para evitar hilos huérfanos o fugas de memoria en el servidor web Tomcat.
* **Frontend (React):** Instanciación de un objeto nativo del navegador `EventSource` apuntando a la URL del stream, actualizando secuencialmente el estado local (`setDiagnostic`) conforme ingresan los fragmentos de datos.