---
name: scaffold-hex-usecase
description: >
  Generates the complete Clean Architecture skeleton for a LogSentinel use case.
  Use AFTER tdd-logsentinel (tests first). Use when: "implementar US1/US2/US3/US4",
  "crear caso de uso X", "scaffold usecase".
---

# Skill: scaffold-hex-usecase

## Propósito
Genera el esqueleto completo de un caso de uso siguiendo la Clean Architecture (Hexagonal)
de LogSentinel. Usar DESPUÉS de `tdd-logsentinel` (primero el test, luego el scaffold).
Usar cuando: "implementar US1/US2/US3/US4", "crear caso de uso X",
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
    @NotNull Urgency urgency,
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

## Racionalizaciones comunes

| Racionalización | Realidad |
|----------------|----------|
| "Puedo usar @Autowired por ahora" | Constructor injection es obligatorio. @Autowired oculta dependencias y rompe tests unitarios. |
| "El puerto lo agrego después" | Sin el puerto, el caso de uso tiene un import de infrastructure. Viola Clean Architecture desde el commit 1. |
| "Este DTO puede ser una clase normal" | Los records son inmutables por diseño. Una clase mutable como DTO es un bug esperando ocurrir. |
| "Puedo retornar la entidad @Entity desde el controller" | La entidad expone el esquema de BD. El record DTO es el contrato de API externo. |
| "Agrego Lombok para ahorrar tiempo" | El proyecto no usa Lombok. Java 25 tiene records nativos. Agregar Lombok rompe el principio de la codebase. |

## Red Flags

- `import org.springframework.*` en cualquier clase de `domain/`
- `import com.logsentinel.infrastructure.*` en cualquier clase de `application/`
- Un método de `@RestController` que retorna un tipo `@Entity`
- `@Autowired` en cualquier capa (constructor injection obligatorio)
- `import lombok.*` en cualquier archivo Java del proyecto
- Una clase en `dto/` que no es `record` (es `class` con getters/setters)

## Verificación (checklist de salida)

- [ ] `domain/` → 0 imports de frameworks: `grep -r "import org.springframework" domain/`
- [ ] `application/` → 0 imports de infrastructure: `grep -r "import com.logsentinel.infrastructure" application/`
- [ ] `infrastructure/.../dto/` → todos son `record`: `grep -l "^public class" **/dto/*.java`
- [ ] `@Entity` no aparece en ningún `@RestController`
- [ ] El proyecto compila: `mvn compile -q` → BUILD SUCCESS
