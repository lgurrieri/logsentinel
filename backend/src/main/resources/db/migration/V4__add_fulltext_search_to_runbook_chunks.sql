-- =========================================================
-- V4__add_fulltext_search_to_runbook_chunks.sql
-- Fallback Full-Text search column for runbook_chunks
-- Ticket: LOG-US2-BE-02
-- =========================================================

-- Generated, always-in-sync tsvector column derived from 'content'. STORED (not
-- VIRTUAL) so it can be indexed and read without recomputing to_tsvector() on every
-- query. Used ONLY as the resilience fallback when the EmbeddingModel call fails
-- (timeout/quota) — the primary search path remains the pgvector cosine `<=>` query.
ALTER TABLE runbook_chunks
    ADD COLUMN content_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

-- GIN index required for the Full-Text fallback to scale; without it, `@@` matches
-- degrade to a sequential scan over the whole table.
CREATE INDEX idx_runbook_chunks_content_tsv_gin
    ON runbook_chunks
    USING gin (content_tsv);
