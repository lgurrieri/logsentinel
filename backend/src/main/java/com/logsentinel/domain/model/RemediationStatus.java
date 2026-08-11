package com.logsentinel.domain.model;

/**
 * Lifecycle status of a {@link RemediationAction} (LOG-US4-BE-02), mapped to the
 * CHECK constraint of the {@code remediation_actions} table.
 * <p>
 * {@code EXECUTING} is committed immediately by Transaction A, before the sandboxed
 * script even starts running, so the audit trail survives a catastrophic failure of
 * the main thread during execution. {@code SUCCESS}/{@code FAILED} are committed by
 * Transaction B once the isolated execution concludes, derived from the process exit
 * code (zero = {@code SUCCESS}, non-zero = {@code FAILED}). {@code DRY_RUN} is
 * reserved for a future simulation mode, out of scope for this ticket.
 */
public enum RemediationStatus {
    EXECUTING,
    SUCCESS,
    FAILED,
    DRY_RUN
}
