-- =========================================================
-- V1__init_schema.sql
-- Migración inicial: Esquema completo de LogSentinel
-- =========================================================

-- Habilitar extensión pgvector para búsqueda semántica
CREATE EXTENSION IF NOT EXISTS vector;

-- Habilitar extensión para gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =========================================================
-- TABLA: incidents
-- Ciclo de vida de los incidentes de infraestructura
-- =========================================================
CREATE TABLE incidents (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    system_name VARCHAR(100) NOT NULL,
    status      VARCHAR(30)  NOT NULL DEFAULT 'OPEN',
    priority    VARCHAR(10)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_incidents_status   CHECK (status   IN ('OPEN', 'ANALYZING', 'RESOLVED')),
    CONSTRAINT chk_incidents_priority CHECK (priority IN ('P1', 'P2', 'P3'))
);

-- =========================================================
-- TABLA: incident_analyses
-- Historial de diagnósticos generados por la IA (RAG)
-- =========================================================
CREATE TABLE incident_analyses (
    id                UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id       UUID      NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    raw_log_snapshot  TEXT      NOT NULL,
    diagnostic_output TEXT      NOT NULL,
    tokens_used       INTEGER   NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

-- =========================================================
-- TABLA: remediation_actions
-- Registro de auditoría de scripts de remediación
-- =========================================================
CREATE TABLE remediation_actions (
    id               UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    analysis_id      UUID      NOT NULL REFERENCES incident_analyses(id),
    generated_script TEXT      NOT NULL,
    execution_status VARCHAR(30) NOT NULL DEFAULT 'DRY_RUN',
    executed_at      TIMESTAMP DEFAULT NOW(),
    execution_log    TEXT,

    CONSTRAINT chk_remediation_status CHECK (execution_status IN ('SUCCESS', 'FAILED', 'DRY_RUN'))
);

-- =========================================================
-- TABLA: runbooks
-- Base de conocimientos SRE (cabecera del documento)
-- =========================================================
CREATE TABLE runbooks (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title      VARCHAR(255) NOT NULL,
    source_url VARCHAR(512),
    tags       VARCHAR(50)[],
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- =========================================================
-- TABLA: runbook_chunks
-- Fragmentos vectorizados de runbooks (patrón Chunking RAG)
-- =========================================================
CREATE TABLE runbook_chunks (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    runbook_id UUID      NOT NULL REFERENCES runbooks(id) ON DELETE CASCADE,
    content    TEXT      NOT NULL,
    embedding  VECTOR(1536) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- =========================================================
-- ÍNDICES DE RENDIMIENTO
-- =========================================================

-- Búsqueda vectorial por coseno: RAG Fast-Path con HNSW
CREATE INDEX idx_runbook_chunks_embedding
    ON runbook_chunks USING hnsw (embedding vector_cosine_ops);

-- Índices operativos para filtros frecuentes
CREATE INDEX idx_incidents_status_priority  ON incidents (status, priority);
CREATE INDEX idx_analyses_incident_id       ON incident_analyses (incident_id);
CREATE INDEX idx_remediation_analysis_id    ON remediation_actions (analysis_id);
