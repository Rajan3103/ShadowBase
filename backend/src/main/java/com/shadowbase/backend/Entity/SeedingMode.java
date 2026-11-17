package com.shadowbase.backend.Entity;

/**
 * Scope mode for selecting which tables to transfer during data seeding.
 */
public enum SeedingMode {
    /** Transfer all user tables discovered in the source database. */
    FULL_DATABASE,

    /** Transfer only explicitly requested tables, maintaining foreign key dependency order. */
    SELECTED_TABLES
}
