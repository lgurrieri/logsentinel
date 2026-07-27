---
applyTo: backend/**
---

# Instrucciones: Backend LogSentinel

## Stack exacto
Java 25 · Spring Boot 4.1.0 · Spring AI 2.0.0 · PostgreSQL 16 + pgvector · Flyway

## Sin Lombok — alternativas nativas Java 25
- DTOs y VOs: `record` (inmutable, equals/hashCode/toString automáticos)
- `@Entity` JPA: clase estándar con getters explícitos, `equals()`/`hashCode()` solo por `id`

## Spring AI — patrones obligatorios

```java
// Parsear respuesta del LLM a record tipado (barrera anti-prompt-injection)
var converter = new BeanOutputConverter<>(ParsedLog.class);
var result = converter.convert(responseText);
// Si el LLM devuelve texto libre en lugar de JSON, falla seguro

// Llamada con streaming al LLM
chatClient.prompt()
    .system("Eres un experto SRE...")
    .user(rawLog)
    .stream()
    .chatResponse()
    .subscribe(chunk -> emitter.send(chunk.getResult().getOutput().getText()));
```

## SSE — patrón obligatorio

```java
@GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@PathVariable UUID id) {
    var emitter = new SseEmitter(30_000L);
    executorService.execute(() -> {
        try {
            // lógica de streaming
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        } finally {
            emitter.complete();  // siempre en finally para evitar hilos huérfanos
        }
    });
    return emitter;
}
```

## Perfiles de Spring por entorno
- `dev`:     `application-dev.yml`     — show-sql=true, nivel DEBUG
- `staging`: `application-staging.yml` — nivel INFO
- `prod`:    `application-prod.yml`    — nivel WARN, `ddl-auto=validate`

Activar con: `SPRING_PROFILES_ACTIVE=dev` (env var) o `-Dspring.profiles.active=dev`

## Testcontainers 2.x — artifact IDs correctos (Spring Boot 4.1.0)

```xml
<!-- ✅ Correcto (2.x) -->
<artifactId>testcontainers-junit-jupiter</artifactId>
<artifactId>testcontainers-postgresql</artifactId>

<!-- ❌ Incorrecto (1.x — no existen en 2.x) -->
<artifactId>junit-jupiter</artifactId>
<artifactId>postgresql</artifactId>
```
Versiones gestionadas por el BOM de Spring Boot 4 — NO agregar `<version>` explícita.

## DevSecOps — reglas no negociables
- Credenciales SIEMPRE via `${ENV_VAR}` en `application-{profile}.yml`
- `ProcessBuilder` en `RemediationService`: validar el script antes de ejecutar
- `@ControllerAdvice` global: NUNCA exponer stacktraces Java en respuestas HTTP
- Nuevas variables de entorno: documentarlas en la skill `provision-logsentinel-env`

## Logging estructurado — obligatorio en infrastructure/

LogSentinel es una herramienta SRE. Sus propios logs deben ser de calidad SRE.

```java
// ✅ Correcto — log con contexto buscable
log.info("Incident created", Map.of(
    "incidentId", id.toString(),
    "systemName", systemName,
    "priority", priority.name()
));

log.error("RAG pipeline failed", Map.of(
    "incidentId", incidentId.toString(),
    "stage", "embedding",
    "cause", e.getMessage()
));

// ❌ Incorrecto — no buscable, no estructurado
log.info("Incident created: " + id);
log.error("Error: " + e.getMessage());
```

Niveles por capa:
- `domain/` → TRACE (raramente loguea — es Pure Java)
- `application/usecases/` → DEBUG (inicio/fin de casos de uso)
- `infrastructure/adapters/in/web/` → INFO (requests recibidos)
- `infrastructure/adapters/out/ai/` → INFO (llamadas al LLM con tokens usados)
- Errores en cualquier capa → ERROR con `incidentId` como contexto siempre que aplique
