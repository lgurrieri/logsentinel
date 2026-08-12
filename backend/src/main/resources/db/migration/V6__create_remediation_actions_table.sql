-- =========================================================
-- V6__create_remediation_actions_table.sql
-- Auditoria de ejecucion de scripts de remediacion en sandbox aislado
-- Ticket: LOG-US4-BE-02
-- =========================================================

-- No se declara UNIQUE sobre incident_id: un mismo incidente puede tener varios
-- intentos de remediacion (reintentos tras un FAILED), por lo que la relacion es
-- uno-a-muchos, a diferencia de incident_diagnostics (uno-a-uno).
CREATE TABLE remediation_actions (
    id                UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id       UUID                        NOT NULL
                                                     REFERENCES incidents (id),
    generated_script  TEXT                        NOT NULL,
    execution_status  VARCHAR(20)                 NOT NULL,
    execution_log     TEXT,
    executed_at       TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),

    -- Restriccion defensiva: solo valores validos a nivel de esquema. Incluye
    -- EXECUTING (fila insertada por la Transaccion A antes de invocar el sandbox)
    -- y DRY_RUN (reservado; sin logica de negocio implementada en este ticket).
    CONSTRAINT chk_remediation_actions_execution_status
        CHECK (execution_status IN ('EXECUTING', 'SUCCESS', 'FAILED', 'DRY_RUN'))
);

-- Indice para recuperar el historial de remediaciones de un incidente
CREATE INDEX idx_remediation_actions_incident_id ON remediation_actions (incident_id);
