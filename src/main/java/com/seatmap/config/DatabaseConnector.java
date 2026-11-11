package com.seatmap.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Manages the HikariCP connection pool and database schema initialization for Seatmap.
 */
public final class DatabaseConnector {

    private static final String CONFIG_RESOURCE = "config.properties";
    private static final String SCHEMA_RESOURCE = "schema.sql";
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final String DUPLICATE_DATABASE_SQL_STATE = "42P04";

    private static volatile HikariDataSource dataSource;

    private DatabaseConnector() {
    }

    /**
     * Returns an active pooled {@link Connection} to the configured PostgreSQL database.
     * Ensures the target database exists and initializes the HikariCP pool on first use.
     *
     * @return a JDBC connection from the pool
     * @throws SQLException if a database access error occurs
     */
    public static Connection getConnection() throws SQLException {
        return getOrCreateDataSource().getConnection();
    }

    /**
     * Ensures the target database exists and executes {@code schema.sql} idempotently.
     *
     * @throws SQLException if database creation or schema execution fails
     */
    public static void initializeDatabase() throws SQLException {
        String script = loadSchemaScript();
        List<String> statements = parseStatements(script);

        try (Connection connection = getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
                connection.commit();
            } catch (SQLException ex) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                throw ex;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    /**
     * Shuts down the HikariCP connection pool if it has been initialized.
     */
    public static synchronized void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private static HikariDataSource getOrCreateDataSource() throws SQLException {
        HikariDataSource existing = dataSource;
        if (existing != null && !existing.isClosed()) {
            return existing;
        }
        synchronized (DatabaseConnector.class) {
            if (dataSource == null || dataSource.isClosed()) {
                DbConfig config = loadConfig();
                ensureDatabaseExists(config);

                HikariConfig hikariConfig = new HikariConfig();
                hikariConfig.setJdbcUrl(config.targetJdbcUrl());
                hikariConfig.setUsername(config.user());
                hikariConfig.setPassword(config.password());
                hikariConfig.setPoolName("SeatmapHikariPool");
                hikariConfig.setMaximumPoolSize(10);
                hikariConfig.setMinimumIdle(2);
                hikariConfig.setConnectionTimeout(10_000L);
                hikariConfig.setIdleTimeout(300_000L);
                hikariConfig.setMaxLifetime(1_800_000L);

                dataSource = new HikariDataSource(hikariConfig);
            }
            return dataSource;
        }
    }

    private static void ensureDatabaseExists(DbConfig config) throws SQLException {
        try (Connection adminConnection = DriverManager.getConnection(
                config.maintenanceJdbcUrl(), config.user(), config.password())) {
            boolean exists;
            try (PreparedStatement checkStmt = adminConnection.prepareStatement(
                    "SELECT 1 FROM pg_database WHERE datname = ?")) {
                checkStmt.setString(1, config.dbName());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    exists = rs.next();
                }
            }
            if (!exists) {
                try (Statement createStmt = adminConnection.createStatement()) {
                    createStmt.execute("CREATE DATABASE \"" + config.dbName() + "\"");
                } catch (SQLException ex) {
                    // Ignore race condition if another process created the database first.
                    if (!DUPLICATE_DATABASE_SQL_STATE.equals(ex.getSQLState())) {
                        throw ex;
                    }
                }
            }
        }
    }

    private static DbConfig loadConfig() {
        Properties props = new Properties();
        ClassLoader classLoader = DatabaseConnector.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(CONFIG_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing configuration resource on classpath: " + CONFIG_RESOURCE);
            }
            props.load(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load configuration resource: " + CONFIG_RESOURCE, ex);
        }

        String host = requireNonBlank(props, "db.host");
        int port = parsePort(requireNonBlank(props, "db.port"));
        String dbName = requireNonBlank(props, "db.name");
        if (!SAFE_IDENTIFIER.matcher(dbName).matches()) {
            throw new IllegalStateException("Invalid PostgreSQL database identifier for db.name: " + dbName);
        }
        String user = requireNonBlank(props, "db.user");
        String password = props.getProperty("db.password");
        if (password == null) {
            throw new IllegalStateException("Missing required configuration property: db.password");
        }

        return new DbConfig(host, port, dbName, user, password);
    }

    private static String requireNonBlank(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing or blank configuration property: " + key);
        }
        return value.trim();
    }

    private static int parsePort(String portValue) {
        try {
            int port = Integer.parseInt(portValue);
            if (port < 1 || port > 65535) {
                throw new IllegalStateException("db.port must be between 1 and 65535: " + port);
            }
            return port;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid integer for db.port: " + portValue, ex);
        }
    }

    private static String loadSchemaScript() {
        ClassLoader classLoader = DatabaseConnector.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing SQL schema resource on classpath: " + SCHEMA_RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder script = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    script.append(line).append('\n');
                }
                return script.toString();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read schema resource: " + SCHEMA_RESOURCE, ex);
        }
    }

    private static List<String> parseStatements(String sqlScript) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : sqlScript.split("\\R")) {
            int commentIndex = line.indexOf("--");
            if (commentIndex >= 0) {
                cleaned.append(line, 0, commentIndex);
            } else {
                cleaned.append(line);
            }
            cleaned.append('\n');
        }

        List<String> statements = new ArrayList<>();
        for (String raw : cleaned.toString().split(";")) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                statements.add(trimmed);
            }
        }
        return statements;
    }

    private record DbConfig(String host, int port, String dbName, String user, String password) {
        String maintenanceJdbcUrl() {
            return "jdbc:postgresql://" + host + ":" + port + "/postgres";
        }

        String targetJdbcUrl() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
        }
    }
}
