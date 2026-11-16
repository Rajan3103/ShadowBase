package com.shadowbase.backend.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptStatementFailedException;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Exception.SchemaImportException;

import lombok.extern.slf4j.Slf4j;

/**
 * Component responsible for validating and executing SQL schema scripts
 * against target database container connections using Spring's {@link ScriptUtils}.
 * <p>
 * Key features:
 * <ul>
 *   <li>Pre-execution validation detecting binary pg_dump archives and client-specific directives (e.g. DELIMITER, \copy).</li>
 *   <li>Robust statement parsing respecting comments, quotes, and strings containing semicolons.</li>
 *   <li>Atomic error reporting propagating detailed statement failure messages.</li>
 * </ul>
 */
@Slf4j
@Component
public class SqlScriptExecutor {

    private static final Pattern MYSQL_DELIMITER_PATTERN = Pattern.compile("(?im)^\\s*DELIMITER\\s+");
    private static final Pattern PSQL_META_COMMAND_PATTERN = Pattern.compile("(?im)^\\s*\\\\(connect|copy|cd|set|echo|q|!)\\b");

    /**
     * Executes the provided SQL input stream against the active database connection.
     *
     * @param connection   active JDBC connection to the target container database
     * @param inputStream  stream containing the raw SQL script bytes
     * @param databaseType engine type of the target database (POSTGRESQL or MYSQL)
     * @throws SchemaImportException if syntax is invalid, unsupported directives are detected, or execution fails
     */
    public void execute(Connection connection, InputStream inputStream, DatabaseType databaseType) {
        if (connection == null) {
            throw new IllegalArgumentException("Target JDBC connection must not be null");
        }
        if (inputStream == null) {
            throw new IllegalArgumentException("SQL script input stream must not be null");
        }

        String sqlScript = readAndValidateScript(inputStream);

        ByteArrayResource resource = new ByteArrayResource(sqlScript.getBytes(StandardCharsets.UTF_8));
        EncodedResource encodedResource = new EncodedResource(resource, StandardCharsets.UTF_8);

        log.info("Executing SQL schema script (size: {} chars) against {} container database...",
                sqlScript.length(), databaseType);

        try {
            // Spring's ScriptUtils handles string literals with semicolons, multi-line blocks, and comments
            ScriptUtils.executeSqlScript(
                    connection,
                    encodedResource,
                    false, // continueOnError: false (fail-fast on any statement error)
                    false, // ignoreFailedDrops: false
                    new String[]{"--", "#"}, // commentPrefixes (supports standard SQL and MySQL #)
                    ";", // statement separator
                    "/*", // blockCommentStartDelimiter
                    "*/"  // blockCommentEndDelimiter
            );

            log.info("SQL schema script executed successfully against {} database.", databaseType);

        } catch (ScriptStatementFailedException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Statement execution failed.";
            log.error("Schema execution error: {}", errorMsg);
            throw new SchemaImportException(errorMsg, e);

        } catch (Exception e) {
            log.error("Failed to execute SQL script: {}", e.getMessage(), e);
            throw new SchemaImportException("SQL script execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Reads the script into memory as a UTF-8 string and verifies that it is valid plaintext SQL
     * free from incompatible client-specific directives.
     */
    private String readAndValidateScript(InputStream inputStream) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new SchemaImportException("Failed to read uploaded SQL script: " + e.getMessage(), e);
        }

        String content = sb.toString();

        if (content.isBlank()) {
            throw new SchemaImportException("Uploaded SQL script is empty or contains only whitespace.");
        }

        // 1. Check for PostgreSQL binary pg_dump archive header
        if (content.startsWith("PGDMP")) {
            throw new SchemaImportException(
                    "Uploaded file is a binary PostgreSQL custom archive (PGDMP), not a plaintext SQL script. " +
                    "Please re-export your schema using 'pg_dump --schema-only --format=plain'.");
        }

        // 2. Check for MySQL client DELIMITER command
        Matcher delimiterMatcher = MYSQL_DELIMITER_PATTERN.matcher(content);
        if (delimiterMatcher.find()) {
            throw new SchemaImportException(
                    "Unsupported client directive 'DELIMITER' detected in script. " +
                    "Standard JDBC execution supports standard semicolon-delimited SQL only. " +
                    "Please remove DELIMITER statements or use standard SQL syntax.");
        }

        // 3. Check for psql client meta-commands (e.g. \copy, \connect)
        Matcher psqlMatcher = PSQL_META_COMMAND_PATTERN.matcher(content);
        if (psqlMatcher.find()) {
            String command = psqlMatcher.group(1);
            throw new SchemaImportException(
                    "Unsupported psql client meta-command '\\" + command + "' detected in script. " +
                    "JDBC executors can only run standard SQL statements. " +
                    "Please remove psql-specific backslash commands from the file.");
        }

        return content;
    }
}
