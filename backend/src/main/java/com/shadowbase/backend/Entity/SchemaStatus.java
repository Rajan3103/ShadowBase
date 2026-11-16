package com.shadowbase.backend.Entity;

/**
 * Lifecycle statuses representing the initialization state of a database schema
 * within a ShadowBase sandbox environment.
 * <p>
 * This status remains independent of {@link Sandbox.SandboxStatus} and {@link Container.ContainerStatus}.
 */
public enum SchemaStatus {
    /**
     * Initial default state; no schema script has been executed.
     */
    NOT_INITIALIZED,

    /**
     * An SQL schema script is currently executing against the target container database.
     */
    IN_PROGRESS,

    /**
     * All statements in the uploaded SQL script were executed successfully.
     */
    SUCCESS,

    /**
     * The schema script execution failed or was aborted due to errors.
     */
    FAILED
}
