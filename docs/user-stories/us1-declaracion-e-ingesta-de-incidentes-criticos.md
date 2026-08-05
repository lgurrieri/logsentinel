## US1: Declaración e Ingesta de Incidentes Críticos

### Narrativa

> **Como** Ingeniero de Confiabilidad del Sitio (SRE)
> **Quiero** registrar un nuevo incidente ingresando el volcado de logs y su nivel de urgencia
> **Para** que el sistema inicialice el ciclo de vida de remediación y habilite el análisis contextual.



### Evaluación INVEST

* **Independent:** Sí. Guarda el estado inicial del incidente de forma autónoma sin depender del motor RAG ni de la IA.
* **Negotiable:** Sí. La UI puede simplificarse a texto plano o selectores básicos de prioridad en esta etapa.
* **Valuable:** Alta. Proporciona el punto de entrada de datos persistente indispensable para todo el flujo.
* **Estimable:** Sí. Es un flujo CRUD transaccional clásico de Spring Boot con JPA.
* **Small:** Enfocado estrictamente en la creación y el cambio de estado inicial (`OPEN`).
* **Testable:** Sí. Validable mediante pruebas automatizadas de integración HTTP en el controlador.

### Criterios de Aceptación (Gherkin)

```gherkin
Escenario: Creación exitosa de un incidente crítico

  Dado que el endpoint "/api/v1/incidents" recibe una petición POST válida
  Y el payload contiene systemName="payment-gateway", priority="P1" y un rawLogSnapshot con errores de timeout

  Cuando el backend procesa la solicitud con éxito

  Entonces el sistema debe responder con HTTP 201 Created
  Y el payload de respuesta debe incluir un "id" en formato UUID, el estado "OPEN" y las marcas de tiempo correspondientes.


Escenario: Rechazo de ingesta por datos incompletos

  Dado que el endpoint recibe una petición POST donde el campo "rawLogSnapshot" está vacío

  Cuando se ejecuta la validación en el controlador

  Entonces el sistema debe responder con HTTP 400 Bad Request
  Y el cuerpo de la respuesta debe listar el error de validación explícito.

```

### Especificación Técnica de Implementación

* **Capa Web (Java):** Controlador REST que valida el DTO de entrada mediante anotaciones `@Valid`, `@NotNull` y `@Size(min=10)`.
* **Capa de Persistencia:** Mapeo directo a la entidad `@Entity` de JPA correspondiente a la tabla `incidents`. Generación automática de UUID v4 del lado de la base de datos a través de Hibernate (`@GeneratedValue`).
* **Respuesta:** Retorno de un objeto `IncidentResponseDTO` aislando la entidad física de la API externa.