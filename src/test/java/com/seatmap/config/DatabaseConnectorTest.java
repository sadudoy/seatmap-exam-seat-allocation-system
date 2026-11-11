package com.seatmap.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DatabaseConnector")
class DatabaseConnectorTest {

    @AfterAll
    static void tearDown() {
        DatabaseConnector.close();
    }

    @Test
    @DisplayName("initializes schema idempotently and executes SELECT 1")
    void initializeAndSelectOne() throws SQLException {
        DatabaseConnector.initializeDatabase();
        DatabaseConnector.initializeDatabase();

        try (Connection connection = DatabaseConnector.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            assertTrue(resultSet.next(), "Expected SELECT 1 to return a row");
            assertEquals(1, resultSet.getInt(1));
        }
    }

    @Test
    @DisplayName("creates all foundational Seatmap tables")
    void createsRequiredTables() throws SQLException {
        DatabaseConnector.initializeDatabase();

        Set<String> expectedTables = Set.of("admins", "students", "rooms", "exams", "allocations");
        Set<String> actualTables = new HashSet<>();

        String sql = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                """;

        try (Connection connection = DatabaseConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                actualTables.add(resultSet.getString("table_name"));
            }
        }

        assertTrue(actualTables.containsAll(expectedTables),
                () -> "Missing expected tables. Found: " + actualTables);
    }
}
