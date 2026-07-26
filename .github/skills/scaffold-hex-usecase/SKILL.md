# Skill: scaffold-hex-usecase

## Propósito
Genera el esqueleto completo de un caso de uso siguiendo la Clean Architecture (Hexagonal)
de LogSentinel. Usar cuando: "implementar US1/US2/US3/US4", "crear caso de uso X",
"scaffold [nombre]UseCase".

## Contexto obligatorio
Leer `agents.md` antes de generar cualquier archivo. Stack: Java 25, Spring Boot 4.1.0,
Spring AI 2.0.0. Sin Lombok: usar `record` para DTOs, clases estándar para `@Entity`.

## Archivos a generar (en orden de dependencia)

### 1. Puerto de entrada — `application/ports/in/{Name}UseCase.java`
```java
package com.logsentinel.application.ports.in;

// Interface Pure Java — CERO imports de Spring, JPA o Spring AI
public interface {Name}UseCase {
    {ReturnType} execute({InputType} input);
}
```

### 2. Puerto(s) de salida — `application/ports/out/{Resource}Port.java`
```java
package com.logsentinel.application.ports.out;

// Un puerto por recurso externo (BD, VectorStore, AI)
// CERO imports de Spring Data, JPA o Spring AI aquí
public interface {Resource}Port {
    {ReturnType} method({Params});
}
```

### 3. Implementación — `application/usecases/{Name}UseCaseImpl.java`
```java
package com.logsentinel.application.usecases;

import com.logsentinel.application.ports.in.{Name}UseCase;
import com.logsentinel.application.ports.out.{Resource}Port;
// SOLO imports de application y domain — NUNCA de infrastructure

@Service  // única excepción permitida en application/usecases
public class {Name}UseCaseImpl implements {Name}UseCase {

    private final {Resource}Port port;  // inyectado por constructor

    public {Name}UseCaseImpl({Resource}Port port) {
        this.port = port;
    }

    @Override
    public {ReturnType} execute({InputType} input) {
        // lógica de orquestación
    }
}
```

### 4. DTO de entrada — `infrastructure/adapters/in/web/dto/{Name}Request.java`
```java
// SIEMPRE record — NUNCA clase con Lombok
public record {Name}Request(
    @NotBlank String systemName,
    @NotNull Priority priority,
    @NotBlank @Size(min = 10) String rawLogSnapshot
) {}
```

### 5. DTO de salida — `infrastructure/adapters/in/web/dto/{Name}Response.java`
```java
public record {Name}Response(UUID id, String status, OffsetDateTime createdAt) {}
```

### 6. Controlador — `infrastructure/adapters/in/web/{Name}Controller.java`
```java
@RestController
@RequestMapping("/api/v1/{resource}")
public class {Name}Controller {

    private final {Name}UseCase useCase;

    public {Name}Controller({Name}UseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public {Name}Response create(@Valid @RequestBody {Name}Request request) {
        // mapear request → dominio → useCase.execute() → mapear → response
        // NUNCA retornar la entidad @Entity directamente
    }
}
```

### 7. Entidad JPA (si aplica) — `infrastructure/adapters/out/persistence/{Entity}.java`
```java
@Entity
@Table(name = "table_name")
public class {Entity} {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Getters explícitos — SIN Lombok

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof {Entity} e)) return false;
        return id != null && id.equals(e.id);  // SOLO por id
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }
}
```

### 8. Test unitario base
Ver skill `generate-logsentinel-test` para el template exacto.

## Reglas de dependencia (NUNCA violar)
- `domain`       → CERO imports de frameworks (Pure Java)
- `application`  → CERO imports de `infrastructure`
- `infrastructure` → puede importar `application` y `domain`
- `@RestController` NUNCA conoce `@Entity` — siempre DTOs `record`
- Los `record` NO tienen setters (inmutables por diseño)
