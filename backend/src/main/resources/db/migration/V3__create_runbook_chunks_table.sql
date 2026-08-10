-- =========================================================
-- V3__create_runbook_chunks_table.sql
-- Tabla de fragmentos vectorizados de runbooks para búsqueda semántica (RAG)
-- Ticket: LOG-US2-DB-01
-- =========================================================

-- La extensión pgvector ya fue habilitada en V1__init_schema.sql. Se repite aquí
-- (idempotente por IF NOT EXISTS) para que esta migración sea auto-contenida y
-- cumpla explícitamente el criterio de aceptación de este ticket.
CREATE EXTENSION IF NOT EXISTS vector;

-- Dimensión del vector: 768, alineada al modelo de embeddings activo por defecto
-- (Ollama / nomic-embed-text). Si el perfil `openai` (text-embedding-3-small, 1536)
-- se activa en producción, cambiar de proveedor con datos ya persistidos requiere
-- backfill/re-embedding — no es un cambio de config en caliente ni de esta migración.
CREATE TABLE runbook_chunks (
    id         UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    content    TEXT                        NOT NULL,
    embedding  vector(768)                 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW()
);

-- Índice HNSW con métrica de distancia de coseno (vector_cosine_ops), requerido para
-- que la búsqueda por similitud (operador `<=>` de pgvector, LOG-US2-BE-02) escale en
-- O(log n) en vez de un full scan O(n).
CREATE INDEX idx_runbook_chunks_embedding_hnsw
    ON runbook_chunks
    USING hnsw (embedding vector_cosine_ops);
