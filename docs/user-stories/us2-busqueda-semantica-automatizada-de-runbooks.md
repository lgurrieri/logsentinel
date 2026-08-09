## US2: Búsqueda Semántica Automatizada de Runbooks (Contexto RAG)

### Narrativa

> **Como** Motor Orquestador de Backend
> **Quiero** calcular el vector del log ingresado y compararlo contra los fragmentos de la base de conocimiento
> **Para** extraer de forma automatizada las 3 guías de solución técnicas más relevantes del historial.

### Evaluación INVEST

* **Independent:** Se conecta con US1 mediante el ID del incidente, pero su lógica matemática de vectores es completamente aislada.
* **Negotiable:** El número de fragmentos recuperados (Top K) es parametrizable en el archivo de propiedades.
* **Valuable:** Crítica. Evita el "alucinamiento" del LLM inyectando información técnica verídica corporativa.
* **Estimable:** Sí. Depende de la llamada a la API de embeddings y una consulta nativa en la base de datos.
* **Small:** Se limita exclusivamente a recibir texto, extraer fragmentos por proximidad vectorial y devolverlos ordenados.
* **Testable:** Sí. Se valida mediante aserciones matemáticas de similitud coseno en un entorno de pruebas con datos controlados.

### Criterios de Aceptación (Gherkin)

```gherkin
Escenario: Recuperación exitosa de fragmentos de Runbooks por similitud semántica

  Dado que la base de datos contiene chunks indexados con vectores de la dimensión del modelo de embeddings activo (768 por defecto con Ollama/`nomic-embed-text`; 1536 si el perfil `openai` está activo)

  Cuando el servicio interno ejecuta la búsqueda por coseno usando el embedding del log del incidente

  Entonces la consulta debe retornar exactamente un máximo de 3 registros de la tabla "runbook_chunks"

  Y los registros deben estar ordenados descendentemente por su cercanía geométrica (menor distancia de coseno).

```

### Especificación Técnica de Implementación

* **Capa de Servicio (Java):** `EmbeddingModel` de Spring AI, configurado por defecto contra Ollama local (`nomic-embed-text`, dimensión 768) y opcionalmente contra OpenAI (`text-embedding-3-small`, dimensión 1536) vía el perfil `openai`. Cambiar de proveedor luego de tener datos persistidos requiere backfill/re-embedding.
* **Capa de Datos (SQL Nativo en JPA):** Uso del operador de distancia de coseno `<=>` provisto por la extensión `pgvector` de PostgreSQL.
* **Consulta SQL de Referencia:**
```sql
SELECT id, content, (embedding <=> ?1::vector) as distance 
FROM runbook_chunks 
ORDER BY distance ASC 
LIMIT 3;

```