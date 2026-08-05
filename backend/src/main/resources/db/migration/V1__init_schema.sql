-- =========================================================
-- V1__init_schema.sql
-- Migración inicial: Esquema completo de LogSentinel
-- =========================================================

-- Habilitar extensión pgvector para búsqueda semántica
CREATE EXTENSION IF NOT EXISTS vector;

-- Habilitar extensión para gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pgcrypto";