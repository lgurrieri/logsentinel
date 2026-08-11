package com.logsentinel.application.ports.out;

import com.logsentinel.domain.model.RemediationAction;

/**
 * Driven port (SPI) for persisting the audit trail of a single remediation script
 * execution (LOG-US4-BE-02). Pure Java interface — NO import of Spring or JPA here.
 * <p>
 * Deliberately split into two methods matching the two-phase transactional design
 * of {@code RemediationStateMachine}: {@link #save} inserts a brand-new row
 * (Transaction A, always {@code EXECUTING}), and {@link #update} persists the
 * closure of an already-existing row (Transaction B, {@code SUCCESS}/{@code FAILED}).
 */
public interface RemediationActionRepository {

    /**
     * Persists a new remediation action and returns it with generated id and
     * createdAt populated.
     *
     * @param action a transient remediation action, normally produced by
     *               {@link RemediationAction#startExecuting}
     * @return the persisted remediation action with id and createdAt populated
     */
    RemediationAction save(RemediationAction action);

    /**
     * Persists the closure (final status, execution log, executedAt) of a
     * remediation action that was already committed by {@link #save}.
     *
     * @param action an already-persisted remediation action carrying its closure
     *               fields, normally produced by {@link RemediationAction#closeWith}
     * @return the updated remediation action
     */
    RemediationAction update(RemediationAction action);
}
