-- =========================================================
-- V2__create_incidents_table.sql
-- Tabla principal de incidentes con restricciones defensivas
-- Ticket: LOG-US1-DB-01
-- =========================================================

CREATE TABLE incidents (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    system_name VARCHAR(255)    NOT NULL,
    urgency     VARCHAR(20)     NOT NULL,
    raw_logs    TEXT            NOT NULL,
    status      VARCHAR(20)     NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Restricciones defensivas: solo valores válidos a nivel de esquema
    CONSTRAINT chk_incidents_urgency
        CHECK (urgency IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),

    CONSTRAINT chk_incidents_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'))
);

-- Indice para búsqueda por estado (consultas frecuentes de dashboard)
CREATE INDEX idx_incidents_status ON incidents (status);

-- Indice para búsqueda por fecha de creación (orden cronológico)
CREATE INDEX idx_incidents_created_at ON incidents (created_at DESC);
