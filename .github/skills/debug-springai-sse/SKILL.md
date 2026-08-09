---
name: debug-springai-sse
description: >
  Systematic debugging guide for LogSentinel's most common failure modes:
  SSE thread leaks, Spring AI streaming errors, BeanOutputConverter failures,
  and pgvector query issues. Use when something in the RAG pipeline is not working.
---

# Debug Spring AI + SSE — LogSentinel

## Overview

El stack async de LogSentinel (Spring AI + SSE + pgvector) es el más difícil de debuggear
porque los errores pueden ocurrir silenciosamente en hilos distintos al request principal.
Esta skill guía el diagnóstico sistemático de los 5 modos de falla más comunes.

## Cuándo usar

- El SSE se cierra sin enviar datos al frontend
- La respuesta del LLM es vacía, truncada o no es JSON
- `BeanOutputConverter` lanza excepciones
- La búsqueda vectorial es lenta o retorna resultados incorrectos
- `HikariPool-1 - Connection is not available` en logs
- El servidor no responde después de varias llamadas al stream

## Checklist de diagnóstico inicial

Antes de investigar cada modo de falla, verificar:

```bash
# 1. El backend está corriendo
curl -s http://localhost:8080/actuator/health | jq .

# 2. La BD está accesible
curl -s http://localhost:8080/actuator/health/db | jq .

# 3. Hay logs de error recientes
grep "ERROR\|WARN" backend/target/logs/logsentinel.log | tail -20
```

---

## Modo de falla 1: Hilos huérfanos de SseEmitter

**Síntoma**: Tomcat responde lento, `HikariPool timeout`, o el servidor deja de responder
después de varias llamadas. Los logs muestran `java.util.concurrent.RejectedExecutionException`.

**Diagnóstico**:
```bash
# Ver threads activos de Tomcat
curl -s http://localhost:8080/actuator/metrics/tomcat.threads.busy | jq .
# Si el valor sube y no baja → hay hilos huérfanos

# Ver thread dump
kill -3 {PID_DEL_BACKEND}  # imprime thread dump en stdout
# Buscar: "async-thread" en estado WAITING o BLOCKED sin actividad
```

**Causa más probable**:

```java
// ❌ Problema: emitter.complete() fuera de finally
executorService.execute(() -> {
    try {
        streamDiagnosis(emitter);
        emitter.complete();   // ← no se ejecuta si hay excepción
    } catch (Exception e) {
        emitter.completeWithError(e);
        // ← el hilo queda huérfano si completeWithError también falla
    }
});
```

**Fix**:
```java
// ✅ Solución: complete() SIEMPRE en finally
executorService.execute(() -> {
    try {
        streamDiagnosis(emitter);
    } catch (Exception e) {
        emitter.completeWithError(e);
    } finally {
        emitter.complete();   // ← siempre ejecutado
    }
});
```

**Verificar fix**: Hacer 10 llamadas seguidas al endpoint stream y verificar que
`tomcat.threads.busy` regresa al baseline.

---

## Modo de falla 2: Spring AI streaming falla silenciosamente

**Síntoma**: El SSE abre la conexión, el frontend recibe el `Content-Type: text/event-stream`,
pero no llegan chunks de datos. La conexión se cierra sola después de unos segundos.

**Diagnóstico**:
```yaml
# Habilitar debug de Spring AI en application-dev.yml
logging:
  level:
    org.springframework.ai: DEBUG
    org.springframework.web.servlet.mvc.method.annotation: DEBUG
```

```bash
# Reiniciar con logs DEBUG y hacer una petición
cd backend && mvn spring-boot:run -Dspring.profiles.active=dev,ollama 2>&1 | grep -E "ERROR|Spring AI|Ollama|OpenAI"
```

**Causas probables y fixes**:

| Causa | Síntoma en logs | Fix |
|-------|----------------|-----|
| (perfil `ollama`, default) Ollama no está corriendo | `Connection refused: localhost:11434` | `docker compose up ollama -d` o verificar `SPRING_AI_OLLAMA_BASE_URL` |
| (perfil `ollama`) Modelo aún no descargado | `model "llama3.1" not found` en logs de Ollama | Esperar el pull inicial (`pull-model-strategy: when_missing`) o `docker exec logsentinel-ollama ollama pull llama3.1` |
| (perfil `openai`, opcional) API key inválida | `401 Unauthorized from OpenAI` | Verificar `SPRING_AI_OPENAI_API_KEY` en `.env` |
| Timeout del LLM | `ReadTimeoutException after 30s` | Aumentar timeout: `spring.ai.ollama.chat.options.timeout` u `.openai.chat.options.timeout` según perfil activo |
| (perfil `openai`) Modelo no disponible | `404 model not found` | Verificar `gpt-4o` existe en la cuenta de OpenAI |
| Error en subscribe() | Sin logs (el error se traga) | Agregar log en el handler de error: `.subscribe(chunk -> ..., error -> log.error("stream error", error), ...)` |

**Diagnóstico en test**:
```java
// Reproducir en test con mock del ChatClient
@Test
void should_handle_chat_client_error_gracefully() {
    given(chatClient.prompt()).willThrow(new RuntimeException("API timeout"));

    var emitter = new SseEmitter(5000L);
    assertThatCode(() -> orchestrator.streamDiagnosis(incidentId, emitter))
        .doesNotThrowAnyException();
    // El emitter debe completar con error, no colgar
}
```

---

## Modo de falla 3: BeanOutputConverter lanza excepciones

**Síntoma**: `JsonMappingException`, `IllegalArgumentException: Cannot deserialize`,
o la respuesta del LLM es texto libre en vez de JSON.

**Diagnóstico**:
```java
// Habilitar log del response raw antes del parse
var response = chatClient.prompt()
    .system(systemPrompt)
    .user(rawLog)
    .call()
    .content();

log.debug("LLM raw response: {}", response);  // ← ver qué devuelve el LLM

var parsed = converter.convert(response);      // ← si falla, el log anterior muestra por qué
```

**Causas probables**:

| Causa | Síntoma | Fix |
|-------|---------|-----|
| `converter.getFormat()` no está en el prompt | LLM devuelve prosa | Agregar `%s`.formatted(converter.getFormat()) al system prompt |
| El record tiene tipos no serializables | `Cannot construct instance of UUID` | Usar `String` en el record y convertir después |
| Prompt injection en el log | LLM devuelve "Olvida todo lo anterior..." | El `BeanOutputConverter` falla seguro — reportar como security event |
| System prompt muy corto | LLM ignora las instrucciones de formato | Hacer el system prompt más explícito sobre el formato JSON esperado |

**Test de regresión**:
```java
@Test
void should_fail_safely_when_llm_returns_free_text() {
    given(chatClient.prompt()...content()).willReturn("El error es un problema de red");
    assertThatThrownBy(() -> adapter.parseLog("ERROR log"))
        .isInstanceOf(LogParseException.class)  // excepción de dominio, no JsonMappingException
        .hasMessageContaining("Unable to parse LLM response");
}
```

---

## Modo de falla 4: pgvector query lenta o resultados incorrectos

**Síntoma**: La búsqueda de runbooks tarda >2s, o retorna chunks que no son relevantes,
o retorna 0 resultados aunque hay datos.

**Diagnóstico**:
```sql
-- Verificar que el índice HNSW existe
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'runbook_chunks'
AND indexname LIKE '%embedding%';

-- Si no existe → crear:
CREATE INDEX idx_runbook_chunks_embedding
ON runbook_chunks USING hnsw (embedding vector_cosine_ops);

-- Verificar cantidad de chunks indexados
SELECT COUNT(*) FROM runbook_chunks;

-- Verificar dimensión de los embeddings almacenados
SELECT array_length(embedding, 1) FROM runbook_chunks LIMIT 1;
-- Debe coincidir con el perfil activo: 768 (Ollama/nomic-embed-text) o 1536 (OpenAI/text-embedding-3-small)
```

```bash
# Habilitar log de queries SQL para ver la query generada
# En application-dev.yml: spring.jpa.show-sql: true
# Buscar la query de búsqueda vectorial:
grep "runbook_chunks" backend/logs/spring.log | grep "ORDER BY"
```

**Causas y fixes**:

| Causa | Fix |
|-------|-----|
| Sin índice HNSW | Crear el índice con `vector_cosine_ops` |
| Dimensión incorrecta | Verificar que coincide con el perfil activo: `spring.ai.ollama.embedding.options.model=nomic-embed-text` (768) u `.openai.embedding.options.model=text-embedding-3-small` (1536) |
| `initialize-schema: true` recreó la tabla sin el índice | Cambiar a `false` y gestionar con Flyway |
| Threshold de similitud muy alto | Bajar de 0.9 a 0.7 en `SearchRequest.withSimilarityThreshold(0.7)` |
| 0 chunks en `runbook_chunks` | Verificar que el seed data se ejecutó correctamente |

**Test de diagnóstico rápido**:
```sql
-- Buscar un chunk manualmente con un vector de ejemplo
SELECT content,
       (embedding <=> '[0.1, 0.2, ...]'::vector) AS distance
FROM runbook_chunks
ORDER BY distance ASC
LIMIT 3;
```

---

## Modo de falla 5: Ollama no disponible (perfil `ollama`, default)

**Síntoma**: El backend falla al arrancar o al primer request de IA con `Connection refused`
contra `SPRING_AI_OLLAMA_BASE_URL`, o el modelo tarda mucho / falla la primera vez.

**Causas y fixes**:

| Causa | Síntoma | Fix |
|-------|---------|-----|
| Servicio `ollama` no está corriendo | `Connection refused: localhost:11434` (o `ollama:11434` en compose) | `docker compose up ollama -d`; verificar `docker compose ps` healthy |
| Modelo todavía no descargado (primer arranque) | Latencia alta o `model not found` momentáneo | `pull-model-strategy: when_missing` lo descarga automáticamente — requiere red la primera vez; verificar con `docker exec logsentinel-ollama ollama list` |
| OOM al descargar/correr un modelo grande en CI | El job de CI muere o el contenedor `ollama` se reinicia (`OOMKilled`) | Usar modelos livianos en CI (`nomic-embed-text`, `llama3.1` en su variante más chica) y asignar memoria suficiente al runner/Testcontainer |
| Volumen `ollama_data` no persistido entre runs | Cada arranque re-descarga el modelo | Confirmar que el volumen está montado (`docker compose config` muestra `ollama_data:/root/.ollama`) |

---

## Racionalizaciones comunes

| Racionalización | Realidad |
|----------------|---------|
| "Reinicio el servidor y funciona" | El restart libera los hilos huérfanos. El bug sigue ahí y volverá. Reproducir con test. |
| "Es problema de la API de OpenAI, no del código" | Puede ser. Verificar con curl directo primero. Si el curl funciona, el bug es local. |
| "Es problema de Ollama, no del código" | Verificar que el servicio está healthy (`docker compose ps`) y el modelo descargado (`ollama list`) antes de asumir un bug de código. |
| "El error solo pasa en producción" | Reproducir localmente con Testcontainers + `@MockBean` del LLM. |
| "Agregaré logs después de hacer funcionar" | Los logs son el único diagnóstico en async. Sin logs, el error es invisible. |

## Verificación (checklist de salida)

- [ ] El error está reproducido con un test automático que fallaba antes del fix
- [ ] `tomcat.threads.busy` vuelve al baseline tras el fix
- [ ] El fix está documentado en `agents.md` si es un pitfall nuevo del proyecto
- [ ] El test de regresión está en la suite: `mvn test -q` → BUILD SUCCESS
