package com.shadowbase.backend.service.adapter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Exception.DataSeedingException;

/**
 * Factory component responsible for resolving the appropriate {@link DatabaseDialectAdapter}
 * based on database engine types, enforcing same-engine copy support (PostgreSQL -> PostgreSQL, MySQL -> MySQL).
 */
@Component
public class DatabaseDialectAdapterFactory {

    private final Map<DatabaseType, DatabaseDialectAdapter> adapterMap;

    public DatabaseDialectAdapterFactory(List<DatabaseDialectAdapter> adapters) {
        this.adapterMap = adapters.stream()
                .collect(Collectors.toMap(DatabaseDialectAdapter::getDatabaseType, Function.identity()));
    }

    /**
     * Retrieves the dialect adapter for the given database engine.
     *
     * @param databaseType DatabaseType (POSTGRESQL or MYSQL)
     * @return the matching dialect adapter
     * @throws DataSeedingException if databaseType is null or unsupported
     */
    public DatabaseDialectAdapter getAdapter(DatabaseType databaseType) {
        if (databaseType == null) {
            throw new DataSeedingException("Target database engine type cannot be null");
        }

        DatabaseDialectAdapter adapter = adapterMap.get(databaseType);
        if (adapter == null) {
            throw new DataSeedingException("Unsupported database engine for data seeding: " + databaseType);
        }
        return adapter;
    }

    /**
     * Validates that source and target database engines are compatible.
     * Initially, only homogeneous transfers (PostgreSQL -> PostgreSQL, MySQL -> MySQL) are supported.
     *
     * @param sourceType source database type
     * @param targetType target database type
     * @throws DataSeedingException if cross-database transfer is attempted
     */
    public void validateCompatibility(DatabaseType sourceType, DatabaseType targetType) {
        if (sourceType == null || targetType == null) {
            throw new DataSeedingException("Both source and target database types must be specified");
        }

        if (sourceType != targetType) {
            throw new DataSeedingException(String.format(
                    "Cross-engine data seeding is not supported in this version. Source is %s but target container is %s.",
                    sourceType, targetType
            ));
        }
    }
}
