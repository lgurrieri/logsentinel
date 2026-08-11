-- =========================================================
-- V7__add_suggested_script_to_incident_diagnostics.sql
-- Captura estructurada del script de remediacion sugerido por la IA, derivado y
-- persistido de forma autoritativa por el backend al congelar el diagnostico
-- consolidado (Decision de diseno Opcion B, aprobada 2026-08-11): el cliente nunca
-- provee codigo ejecutable en el flujo de remediacion.
-- Ticket: LOG-US3-DB-02B
-- =========================================================

-- Nullable: no todo diagnostico contiene un bloque de codigo parseable (la IA puede
-- redactar un diagnostico sin remediacion accionable), y los diagnosticos ya
-- existentes (previos a este ticket) no tienen script derivado retroactivamente.
ALTER TABLE incident_diagnostics
    ADD COLUMN suggested_script TEXT NULL;
