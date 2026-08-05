---
name: rag-pipeline-implementation
description: >
  Guides implementation of the RAG pipeline — the core of LogSentinel.
  Use AFTER scaffold-hex-usecase (hexagonal structure first). Use when implementing
  US2 (vector search), US3 (SSE streaming), or any extension of the AI diagnostic pipeline.
  Covers Spring AI 2.0 + pgvector + BeanOutputConverter + SSE.
---

# RAG Pipeline Implementation — LogSentinel

## Prerequisito
Ejecutar `scaffold-hex-usecase` **primero** para generar la estructura hexagonal del caso de uso.
Esta skill añade únicamente los adaptadores Spring AI encima del esqueleto ya creado.

## Overview

El pipeline RAG es el núcleo de LogSentinel. Une 5 componentes con patrones específicos
de Spring AI 2.0 que difieren del Spring estándar. Cada paso tiene un anti-patrón obvio
y un patrón correcto no obvio. Esta skill guía la implementación en orden estricto.

## Cuándo usar

- Implementando `LogParserService` (US2)
- Implementando `EmbeddingService` y búsqueda vectorial (US2)
- Implementando `AgentOrchestrator` con streaming (US3)
- Implementando cualquier extensión del pipeline de análisis
- Debugging de respuestas inesperadas del LLM

**Cuándo NO usar:** Para la capa de persistencia relacional (usar `scaffold-hex-usecase`)
o para el frontend SSE consumer (usar `scaffold-react-feature`).

## El pipeline completo

```
Log crudo (rawLogSnapshot)
     │
     ▼ Paso 1: LogParserService
BeanOutputConverter<ParsedLog> ─→ ParsedLog{serviceName, errorCode, logLevel, summary}
     │
     ▼ Paso 2: EmbeddingService
EmbeddingModel.embed(text) ─→ float[1536]
     │
     ▼ Paso 3: VectorStore query
PgVectorStore.similaritySearch() ─→ List<Document> TOP 3
     │
     ▼ Paso 4: AgentOrchestrator
ChatClient.stream() + prompt augmentation ─→ Flux<String>
     │
     ▼ Paso 5: SseEmitter
SseEmitter + @Async ─→ Frontend EventSource
```

---

## Paso 1: LogParserService — BeanOutputConverter

**Port out**: `AIServicePort.parseLog(String rawLog): ParsedLog`

**Implementación** (`infrastructure/adapters/out/ai/`):

```java
// ParsedLog es un record — no una clase con @JsonProperty
public record ParsedLog(
    String serviceName,
    String errorCode,
    String logLevel,       // ERROR, WARN, INFO
    String summary         // resumen ejecutable en 1 oración
) {}
```

```java
@Component
public class SpringAIServiceAdapter implements AIServicePort {

    private final ChatClient chatClient;

    public SpringAIServiceAdapter(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public ParsedLog parseLog(String rawLog) {
        var converter = new BeanOutputConverter<>(ParsedLog.class);

        var response = chatClient.prompt()
            .system("""
                Eres un experto SRE. Analiza el log y extrae la información técnica.
                %s
                """.formatted(converter.getFormat()))  // ← inyecta instrucciones JSON
            .user(rawLog)
            .call()
            .content();

        return converter.convert(response);  // ← falla seguro si el LLM devuelve texto libre
    }
}
```

**Anti-patrón**: Parsear texto libre del LLM con regex o `String.split()`.
**Por qué falla**: El LLM puede devolver texto libre. `BeanOutputConverter` fuerza JSON estricto
y actúa como barrera anti-prompt-injection — si el LLM es manipulado, el parse falla seguro.

**Test RED primero** (antes de implementar):
```java
@Test
void should_extract_service_name_from_raw_log() {
    given(chatClient.prompt()).willReturn(/* chain mock */);
    var result = adapter.parseLog("ERROR auth-service connection refused");
    assertThat(result.serviceName()).isEqualTo("auth-service");
}
```

---

## Paso 2: EmbeddingService — Vector generado

**Port out**: `VectorSearchPort.embed(String text): float[]`

```java
@Override
public float[] embed(String text) {
    var response = embeddingModel.embedForResponse(List.of(text));
    var vector = response.getResults().get(0).getOutput();
    // Verificar dimensión — SIEMPRE 1536 para text-embedding-3-small
    if (vector.length != 1536) {
        throw new EmbeddingDimensionException(
            "Expected 1536, got " + vector.length);
    }
    return vector;
}
```

**Anti-patrón**: Guardar el embedding sin verificar la dimensión.
**Por qué falla**: Si el modelo cambia (ej: text-embedding-ada-002 → 1536, text-embedding-3-large → 3072),
los vectores se almacenan con dimensión incorrecta y la búsqueda coseno retorna basura sin error visible.

---

## Paso 3: VectorStore — Búsqueda semántica

**Port out**: `VectorSearchPort.findSimilarRunbooks(float[] embedding): List<RunbookChunk>`

```java
@Override
public List<RunbookChunk> findSimilarRunbooks(float[] embedding) {
    var request = SearchRequest
        .query(/* texto del log */ "")
        .withTopK(3)
        .withSimilarityThreshold(0.7);   // evitar falsos positivos

    return pgVectorStore.similaritySearch(request)
        .stream()
        .map(doc -> new RunbookChunk(
            doc.getId(),
            doc.getContent(),
            (Double) doc.getMetadata().get("similarity")
        ))
        .toList();
}
```

**Verificar que el índice HNSW existe** (obligatorio — sin esto es O(n) full scan):
```sql
SELECT indexname FROM pg_indexes
WHERE tablename = 'vector_store' AND indexname LIKE '%embedding%';
```
El índice se crea en `V1__init_schema.sql`. Si no existe → la búsqueda es lenta pero silenciosamente correcta.

**Anti-patrón**: Usar `findAll()` y calcular similitud coseno en Java.
**Por qué falla**: No escala. pgvector con índice HNSW hace la búsqueda en milisegundos.

---

## Paso 4: AgentOrchestrator — Streaming con prompt augmentation

**Port in**: `AnalyzeIncidentUseCase` invoca el orchestrator.

```java
@Async   // ← obligatorio — el streaming bloquea el hilo
public void streamDiagnosis(UUID incidentId, ParsedLog parsedLog,
                             List<RunbookChunk> runbooks, SseEmitter emitter) {
    var runbookContext = runbooks.stream()
        .map(RunbookChunk::content)
        .collect(Collectors.joining("\n---\n"));

    chatClient.prompt()
        .system("""
            Eres un experto SRE. Diagnostica el incidente basándote ÚNICAMENTE
            en los siguientes runbooks disponibles. No inventes soluciones fuera del contexto.

            RUNBOOKS DISPONIBLES:
            %s
            """.formatted(runbookContext))
        .user("""
            Analiza este incidente:
            Servicio: %s | Error: %s | Log: %s
            """.formatted(parsedLog.serviceName(), parsedLog.errorCode(), parsedLog.summary()))
        .stream()
        .chatResponse()
        .subscribe(
            chunk -> {
                try {
                    emitter.send(SseEmitter.event()
                        .data(Map.of("chunk", chunk.getResult().getOutput().getText())));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            },
            emitter::completeWithError,
            emitter::complete
        );
}
```

**Anti-patrón**: Usar el SDK nativo de OpenAI (`OpenAIClient` o `openai-java`).
**Por qué falla**: Viola la abstracción de Spring AI. Cambiar el LLM (a Ollama, Anthropic, etc.)
requeriría reescribir el adapter. Con `ChatClient` es una línea en `application.yml`.

---

## Paso 5: SseEmitter — Pattern correcto

```java
@GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@PathVariable UUID id) {
    var emitter = new SseEmitter(30_000L);  // ← timeout explícito siempre

    executorService.execute(() -> {
        try {
            // invocar AgentOrchestrator.streamDiagnosis(id, ..., emitter)
            orchestratorPort.stream(id, emitter);
        } catch (Exception e) {
            emitter.completeWithError(e);
        } finally {
            emitter.complete();   // ← SIEMPRE en finally — evita hilos huérfanos
        }
    });

    return emitter;
}
```

**Anti-patrón**: `new SseEmitter()` sin timeout, o sin `complete()` en `finally`.
**Por qué falla**: Sin timeout, si el cliente desconecta abruptamente, el hilo de Tomcat queda
huérfano indefinidamente. En producción esto agota el thread pool.

---

## Racionalizaciones comunes

| Racionalización | Realidad |
|----------------|---------|
| "Usaré el SDK de OpenAI directamente, es más simple" | Acoplamiento directo. Cambiar el LLM requiere reescribir toda la capa AI. `ChatClient` abstrae esto. |
| "No hace falta `BeanOutputConverter`, parseo el texto yo" | El LLM puede ser manipulado para devolver texto libre. `BeanOutputConverter` falla seguro. |
| "El `SseEmitter` no necesita timeout, el cliente cierra la conexión" | El cliente puede desconectarse sin cerrar limpiamente. Sin timeout el hilo queda bloqueado. |
| "El índice HNSW lo agrego después, primero que funcione" | Sin índice HNSW, cada búsqueda es O(n) full scan. Con 10K runbook chunks es perceptiblemente lento. |
| "Hardcodeo `topK=3` en el adapter, no en config" | El número de chunks afecta calidad y costo. Debe ser configurable via `application.yml`. |

## Red Flags

- Import de `com.openai.*` o `io.github.openai.*` en cualquier capa — usar Spring AI
- `BeanOutputConverter` sin `converter.getFormat()` en el system prompt — el LLM no sabe el schema
- `new SseEmitter()` sin argumento de timeout
- `emitter.complete()` fuera de un bloque `finally`
- Cálculo de distancia coseno en Java en vez de en PostgreSQL con `<=>`
- `EmbeddingModel` inyectado en `application/usecases` — viola Clean Architecture

## Verificación (checklist de salida)

- [ ] `ParsedLog` es un `record` (no clase con @Data)
- [ ] `BeanOutputConverter.getFormat()` está en el system prompt
- [ ] Dimensión del embedding verificada: `vector.length == 1536`
- [ ] Índice HNSW existe en `runbook_chunks.embedding`
- [ ] `SseEmitter` construido con timeout `new SseEmitter(30_000L)`
- [ ] `emitter.complete()` está en bloque `finally`
- [ ] Test de integración del pipeline con `@MockBean ChatClient` y `@MockBean EmbeddingModel`
- [ ] `ChatClient` y `EmbeddingModel` inyectados vía constructor (no @Autowired)
