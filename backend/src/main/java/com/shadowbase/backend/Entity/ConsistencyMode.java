package com.shadowbase.backend.Entity;

/**
 * Source database transaction isolation and consistency mode during data streaming.
 */
public enum ConsistencyMode {
    /**
     * Attempts a consistent snapshot read (e.g. REPEATABLE READ / SERIALIZABLE in read-only mode)
     * so transferred tables reflect a point-in-time snapshot.
     */
    SNAPSHOT,

    /**
     * Standard read-committed isolation for faster transfers without long-running snapshot overhead.
     */
    STANDARD
}
