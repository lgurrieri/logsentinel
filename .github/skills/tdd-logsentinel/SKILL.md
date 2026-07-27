---
name: tdd-logsentinel
description: >
  Drives development with tests for LogSentinel. Enforces Red-Green-Refactor cycle
  adapted to Java 25 + Spring Boot 4.1.0 + JUnit 5 + Mockito.
  Use when implementing any logic, fixing any bug, or changing any behavior.
  Use BEFORE scaffold-hex-usecase — tests come first.
---

# Test-Driven Development — LogSentinel

## Overview

Escribe el test fallido ANTES de escribir el código que lo hace pasar. Para bugs,
reproduce el error con un test ANTES de intentar el fix. Un test que pasa sin implementación
no prueba nada. "Parece correcto" no es done.

## Cuándo usar

- Implementando cualquier caso de uso (US1, US2, US3, US4)
- Corrigiendo cualquier bug (Prove-It Pattern)
- Modificando comportamiento existente
- Antes de invocar `scaffold-hex-usecase` — el test define el contrato

**Cuándo NO usar:** Cambios de configuración pura (`application.yml`), migraciones
Flyway (solo esquema), cambios en `copilot-instructions.md`.

## Descubrir el stack primero

Antes de escribir el primer test, identificar cómo ejecutar tests en este proyecto:

```bash
# Tests unitarios (sin Docker — millisegundos)
cd backend && mvn test -Dtest="!*IntegrationTest" -q

# Test específico
cd backend && mvn test -Dtest={ClassName} -q

# Tests de integración (requiere Docker)
cd backend && mvn test -Dtest="*IntegrationTest" -q

# Suite completa
cd backend && mvn test -q
```

## El Ciclo TDD

```
    RED                  GREEN                REFACTOR
 Escribir test       Código mínimo        Limpiar sin
 que falla   ──→    para que pase  ──→  romper el test  ──→ (repetir)
     │                   │                    │
     ▼                   ▼                    ▼
 Test FALLA         Test PASA           Tests PASAN
```

### Paso 1: RED — Escribir el test que falla

El test DEBE fallar. Si compila y pasa sin código → está mal escrito.

```java
// RED: IncidentRepository no existe aún — ese es el punto
@ExtendWith(MockitoExtension.class)
class CreateIncidentUseCaseTest {

    @Mock
    private IncidentRepositoryPort repositoryPort;

    @InjectMocks
    private CreateIncidentUseCaseImpl useCase;

    @Test
    void should_create_incident_with_open_status_when_valid_request() {
        // Arrange
        var request = new CreateIncidentCommand("payment-gw", Priority.P1, "ERROR pool exhausted");
        given(repositoryPort.save(any())).willReturn(new IncidentId(UUID.randomUUID()));

        // Act
        var result = useCase.execute(request);

        // Assert
        assertThat(result.status()).isEqualTo(IncidentStatus.OPEN);
        verify(repositoryPort, times(1)).save(any());
    }
}
```

**Verificar que falla:**
```bash
mvn test -Dtest=CreateIncidentUseCaseTest -q
# Debe fallar con: ClassNotFoundException o CompilationException
```

### Paso 2: GREEN — Código mínimo que hace pasar el test

Mínima implementación. No over-engineer. Ahora invocar `scaffold-hex-usecase`
para generar el esqueleto, luego implementar solo lo necesario para el test verde.

```bash
mvn test -Dtest=CreateIncidentUseCaseTest -q
# Debe pasar: BUILD SUCCESS, Tests run: 1, Failures: 0
```

### Paso 3: REFACTOR — Limpiar sin romper

Con los tests en verde, mejorar el código:
- Extraer métodos o clases si hay duplicación
- Mejorar naming
- Verificar que los tests siguen en verde después de cada cambio

```bash
# Después de cada refactor
mvn test -Dtest=CreateIncidentUseCaseTest -q
```

## Patrones de test por capa

### Capa domain + application (unitarios — SIN Spring)
```java
@ExtendWith(MockitoExtension.class)
class {Name}UseCaseTest {
    @Mock private {Resource}Port port;
    @InjectMocks private {Name}UseCaseImpl useCase;

    @Test
    void should_{behavior}_when_{condition}() {
        // Arrange — Given
        given(port.method(any())).willReturn(/* fixture */);

        // Act — When
        var result = useCase.execute(/* input */);

        // Assert — Then
        assertThat(result).isNotNull();
        verify(port, times(1)).method(any());
    }
}
```

### Capa infrastructure/web (integración — @WebMvcTest)
```java
@WebMvcTest({Name}Controller.class)
class {Name}ControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean {Name}UseCase useCase;

    @Test
    void should_return_201_when_valid_request() throws Exception {
        given(useCase.execute(any())).willReturn(/* fixture */);
        mockMvc.perform(post("/api/v1/{resource}")
                .contentType(APPLICATION_JSON)
                .content(/* valid JSON */))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void should_return_400_when_field_is_blank() throws Exception {
        mockMvc.perform(post("/api/v1/{resource}")
                .contentType(APPLICATION_JSON)
                .content(/* invalid JSON */))
            .andExpect(status().isBadRequest());
    }
}
```

### Capa Spring AI (integración — @MockBean obligatorio)
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class {Name}IntegrationTest {
    // NUNCA llamadas reales a OpenAI en CI
    @MockBean ChatClient chatClient;
    @MockBean EmbeddingModel embeddingModel;
    @Autowired TestRestTemplate restTemplate;
}
```

### SSE endpoint (integración)
```java
@Test
void should_return_event_stream_content_type() throws Exception {
    mockMvc.perform(get("/api/v1/incidents/{id}/stream", UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", containsString("text/event-stream")));
}
```

## Prove-It Pattern — Bug Fixes

Cuando llega un bug: **NO empezar por el fix**. Empezar por el test.

```
Bug reportado
     │
     ▼
Escribir test que reproduce el bug
     │
     ▼
Test FALLA → bug confirmado
     │
     ▼
Implementar el fix
     │
     ▼
Test PASA → bug resuelto y regression guardada
     │
     ▼
Suite completa verde
```

## Racionalizaciones comunes

| Racionalización | Realidad |
|----------------|---------|
| "Escribiré el test después" | Después nunca llega. El test escrito después prueba implementación, no comportamiento. |
| "Es demasiado simple para testear" | El código simple se complica. El test documenta el comportamiento esperado para siempre. |
| "Ya lo probé a mano" | Las pruebas manuales no persisten. El próximo cambio lo romperá sin aviso. |
| "Los tests me hacen más lento" | Los tests te hacen lento ahora. Te hacen rápido en cada cambio futuro. |
| "El dominio no necesita tests, no tiene Spring" | Exactamente. Por eso los tests son más fáciles — sin mocks de framework. |
| "Puedo mockear todo con @SpringBootTest" | `@MockBean` en tests de dominio indica que hay una violación de Clean Architecture. |

## Red Flags

- Escribir código sin ningún test correspondiente
- Test que pasa en la primera ejecución sin implementación real
- "BUILD SUCCESS, Tests run: 0" — no corrió ningún test
- Bug fix sin test de reproducción previa
- `@MockBean ChatClient` en un test de `application/usecases` (el dominio no debe conocer Spring AI)
- Tests con nombres como "test1", "testMethod", "works"
- `@Disabled` o `Assumptions.assumeTrue(false)` para hacer pasar la suite

## Verificación (checklist de salida)

- [ ] Cada comportamiento nuevo tiene un test correspondiente
- [ ] El test fallaba ANTES de la implementación
- [ ] El test pasa DESPUÉS de la implementación
- [ ] Tests unitarios del dominio/application sin `@SpringBootTest`
- [ ] Bug fixes incluyen test de reproducción que falló antes del fix
- [ ] Suite completa verde: `mvn test -q` → BUILD SUCCESS
- [ ] Nombres de tests describen el comportamiento: `should_{behavior}_when_{condition}`
