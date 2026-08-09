# AGENTS.md

## Project Overview

**LogSentinel** es un agente autónomo de IA para ingenieros SRE/DevOps que reduce el MTTR de horas a segundos. Intercepta logs/stacktraces desestructurados, los interpreta semánticamente mediante RAG, los empareja con el Runbook óptimo y permite ejecutar Auto-Healing simulado, todo con streaming en tiempo real (SSE).

- Arquitectura completa: [AI4Devs-finalproject/](../AI4Devs-finalproject/)
- Modelo de datos: [#3-modelo-de-datos.md](../AI4Devs-finalproject/%233-modelo-de-datos.md)
- Contrato de API (OpenAPI 3.0): [docs/openapi: 3.0.yml](docs/openapi:%203.0.yml)
- Historias de usuario: [docs/user-stories/](docs/user-stories/)

---

## Stack Tecnológico

| Capa | Tecnología |
|------|-----------|
| Backend | Java 25, Spring Boot 4.1.0, Spring AI 2.0.0 |
| Persistencia | PostgreSQL 16 + extensión `pgvector` |
| Migraciones | Flyway |
| IA / Embeddings | Ollama local `llama3.1` + `nomic-embed-text` (default) · OpenAI `gpt-4o` + `text-embedding-3-small` (perfil opcional), vía Spring AI |
| Frontend | React 18+, TypeScript, Vite, Tailwind CSS *(pendiente de inicializar)* |
| Tests | JUnit 5, Mockito, Testcontainers (PostgreSQL) |
| Despliegue objetivo | Vercel (frontend) + Render (backend+BD) |

---

## Setup del Entorno Local

### Prerrequisitos
- Java 25 (Temurin), Maven 3.9+
- Docker y Docker Compose (requeridos para la BD, Ollama local y tests de integración)
- API Key de OpenAI — **solo si** se activa el perfil opcional `openai`; el perfil `ollama` (por defecto) no requiere ninguna key

### 1. Variables de entorno (backend)
```bash
cp backend/.env.example backend/.env
# Perfil ollama (default): no requiere completar nada para arrancar local
# Perfil openai (opcional): completar SPRING_AI_OPENAI_API_KEY y activar SPRING_PROFILES_ACTIVE=dev,openai
```

Variables gestionadas (ver `backend/.env.example`):
- `SPRING_AI_OLLAMA_BASE_URL` — opcional, default: `http://localhost:11434`
- `SPRING_AI_OPENAI_API_KEY` — clave de OpenAI, solo si el perfil `openai` está activo
- `SPRING_DATASOURCE_URL` — default: `jdbc:postgresql://localhost:5432/logsentinel`
- `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` — default: `logsentinel`

### 2. Base de datos y modelos de IA locales (Docker)
```bash
docker compose up db ollama -d
```

### 3. Ejecutar el backend
```bash
cd backend
SPRING_PROFILES_ACTIVE=dev,ollama mvn spring-boot:run
# API:       http://localhost:8080
# Swagger:   http://localhost:8080/swagger-ui.html
# Actuator:  http://localhost:8080/actuator/health
```

### 4. Frontend *(aún no inicializado)*
```bash
# Pendiente: cd frontend && npm create vite@latest . -- --template react-ts
```

---

## Comandos Esenciales

| Acción | Comando |
|--------|---------|
| Compilar | `cd backend && mvn clean package` |
| Ejecutar servidor dev | `cd backend && mvn spring-boot:run` |
| Todos los tests | `cd backend && mvn test` *(requiere Docker)* |
| Build Docker imagen | `docker build -t logsentinel-backend backend/` |
| Stack completo | `docker compose up --build -d` |

> **Importante:** Los tests de integración usan **Testcontainers** — Docker debe estar corriendo. Perfil de test: `backend/src/test/resources/application-test.yml` con `url: jdbc:tc:postgresql:16:///logsentinel_test`.

---

## Arquitectura del Backend (Clean Architecture / Hexagonal)

```
com.logsentinel
├── domain/                  # Núcleo: entidades y excepciones de negocio (sin frameworks)
│   ├── model/
│   └── exception/
├── application/             # Casos de uso y puertos (interfaces SOLID)
│   └── ports/
│       ├── in/              # Puertos de entrada (driving / casos de uso)
│       └── out/             # Puertos de salida (driven / SPI)
└── infrastructure/          # Detalles tecnológicos (frameworks, BD, IA)
    ├── adapters/
    │   ├── in/web/          # @RestController, DTOs, SSE mappers
    │   └── out/
    │       ├── persistence/ # JPA/Hibernate (PostgreSQL relacional)
    │       ├── vectorstore/ # pgvector via Spring AI PgVectorStore
    │       └── ai/          # ChatClient, EmbeddingClient (Ollama por defecto / OpenAI opcional)
    └── config/
```

**Regla de dependencia estricta:**
- `domain` → sin imports de frameworks (Pure Java)
- `application` → solo depende de `domain`
- `infrastructure` → depende de `application` y `domain`; **nunca** al revés
- Los controladores REST **nunca** exponen entidades `@Entity` de JPA; siempre DTOs

---

## Convenciones Clave

### Base de Datos y Flyway
- Migraciones en `backend/src/main/resources/db/migration/`, naming: `V{n}__{descripcion}.sql`
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate **solo valida**, nunca modifica el esquema
- `pgvector` inicializado manualmente en Flyway: `CREATE EXTENSION IF NOT EXISTS vector`
- Índice HNSW en `runbook_chunks.embedding` con `vector_cosine_ops`
- Las migraciones Flyway son **irreversibles en producción** — revisar cuidadosamente antes de hacer commit

### Spring AI (patrones obligatorios)
- Usar `ChatClient` / `EmbeddingClient` como abstracción — **nunca** los SDKs nativos de OpenAI directamente
- Para parsear respuestas del LLM a objetos Java usar `BeanOutputConverter` — garantiza JSON tipado y actúa como barrera anti-prompt-injection
- Modelo, temperatura y tokens configurados en `application.yml`, nunca hardcodeados

### Modelo de Clases (sin Lombok)
El proyecto **no usa Lombok**. Java 25 provee alternativas nativas superiores para cada caso:

| Caso de uso | Patrón obligatorio |
|-------------|-------------------|
| DTOs de API (`IncidentRequest`, `IncidentResponse`) | `record` nativo |
| POJOs para `BeanOutputConverter` de Spring AI (`ParsedLog`, `Diagnostic`) | `record` nativo |
| Objetos de valor del dominio | `record` inmutable |
| Entidades JPA `@Entity` | Clase estándar con getters explícitos; `equals()`/`hashCode()` basados **solo en `id`** para compatibilidad con proxies Hibernate |

```java
// ✅ Correcto — DTO como record
public record IncidentRequest(
    @NotBlank String systemName,
    @NotNull Priority priority,
    @NotBlank @Size(min = 10) String rawLogSnapshot
) {}

// ✅ Correcto — POJO para BeanOutputConverter
public record ParsedLog(String serviceName, String errorCode, String logLevel, String summary) {}

// ✅ Correcto — Entidad JPA sin Lombok
@Entity
@Table(name = "incidents")
public class Incident {
    @Id private UUID id;
    // getters explícitos — sin @Data, sin @EqualsAndHashCode sobre todos los campos
    @Override public boolean equals(Object o) { ... /* solo por id */ }
}
```

### SSE (Server-Sent Events)
- Los endpoints de streaming retornan `SseEmitter` de Spring MVC
- Cerrar siempre con `emitter.complete()` en bloque `finally` para evitar hilos huérfanos en Tomcat
- Los métodos de servicio que alimentan SSE deben anotarse con `@Async` (`@EnableAsync` ya activo en `LogSentinelApplication`)

### Seguridad
- **Nunca** hacer commit de `.env` ni credenciales hardcodeadas
- `RemediationService`: encapsular `ProcessBuilder` con validación estricta de inputs — prevención de command injection
- Los stacktraces internos de Java/SQL no deben exponerse en respuestas de la API; usar `@ControllerAdvice` global para traducirlos a respuestas limpias (HTTP 4xx/5xx sin detalles técnicos)

---

## Flujo RAG (Referencia Rápida)

```
Log crudo → LogParserService (BeanOutputConverter → ParsedLog tipado)
         → EmbeddingService (EmbeddingModel de Spring AI → float[N]; N=768 con Ollama/nomic-embed-text por defecto, N=1536 si el perfil openai está activo)
         → VectorStore query (pgvector distancia coseno sobre runbook_chunks, TOP 3 chunks)
         → AgentOrchestrator (prompt augmentation: system prompt SRE + runbooks)
         → ChatClient stream (Ollama/llama3.1 por defecto, OpenAI/gpt-4o si el perfil openai está activo; stream=true)
         → SseEmitter → Frontend EventSource (token a token)
         → RemediationService (CompletableFuture + @Transactional atómico)
```

---

## Estrategia de Testing

- **Unitarios**: mockear puertos de salida con Mockito. El dominio y casos de uso no deben requerir Spring context (no `@SpringBootTest`)
- **Integración**: usar `@SpringBootTest` + Testcontainers. `application-test.yml` activa el perfil `ollama` sin requerir API key; mockear `ChatClient` y `EmbeddingClient` con `@MockBean`
- **E2E (pendiente)**: Playwright — flujo: seleccionar escenario → analizar → verificar SSE → ejecutar remediación → estado `RESOLVED`

### ⚠ Pitfall: Testcontainers 2.x — artifact IDs renombrados

Spring Boot 4.1.0 gestiona **Testcontainers 2.x** (no 1.x). En la versión 2.x los artifact IDs cambiaron:

| Testcontainers 1.x (❌ no usar) | Testcontainers 2.x (✅ correcto) |
|--------------------------------|----------------------------------|
| `org.testcontainers:junit-jupiter` | `org.testcontainers:testcontainers-junit-jupiter` |
| `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql` |

Las versiones están gestionadas por el BOM de Spring Boot 4 — **no añadir `<version>` explícita** a estos artefactos.

---

## Pull Request Guidelines

- Formato de título: `[backend|frontend|iac|docs] descripción breve`
- Checks requeridos: `mvn test` (todos en verde, Docker disponible)
- No exponer credenciales; respetar la regla de dependencias de Clean Architecture
- Branch actual de trabajo: `feature/project-bootstrap`
