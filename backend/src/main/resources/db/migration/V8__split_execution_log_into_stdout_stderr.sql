-- =========================================================
-- V8__split_execution_log_into_stdout_stderr.sql
-- Reemplaza el campo combinado execution_log de remediation_actions por
-- columnas independientes stdout_log / stderr_log, para que el frontend
-- pueda diferenciar ambos flujos sin heuristicas de texto sobre un string
-- combinado.
-- Ticket: LOG-US4-BE-02B
-- =========================================================

ALTER TABLE remediation_actions
    DROP COLUMN execution_log;

ALTER TABLE remediation_actions
    ADD COLUMN stdout_log TEXT,
    ADD COLUMN stderr_log TEXT;

COMMENT ON COLUMN remediation_actions.stdout_log IS
    'Standard output buffer captured independently from stderr during the sandboxed script execution (LOG-US4-BE-02B). Null while the action is EXECUTING.';

COMMENT ON COLUMN remediation_actions.stderr_log IS
    'Standard error buffer captured independently from stdout during the sandboxed script execution (LOG-US4-BE-02B). Null while the action is EXECUTING.';
