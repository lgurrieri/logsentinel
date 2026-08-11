-- =========================================================
-- V5__create_incident_diagnostics_table.sql
-- Historico de diagnostico consolidado de IA, congelado al cerrarse el canal SSE
-- Ticket: LOG-US3-DB-02
-- =========================================================

-- Relacion uno a uno con 'incidents': UNIQUE sobre incident_id impide mas de un
-- diagnostico congelado por incidente, y la FOREIGN KEY garantiza que nunca se
-- persista un diagnostico huerfano (sin incidente asociado).
CREATE TABLE incident_diagnostics (
    id              UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id     UUID                        NOT NULL UNIQUE
                                                  REFERENCES incidents (id),
    diagnostic_text TEXT                        NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW()
);
