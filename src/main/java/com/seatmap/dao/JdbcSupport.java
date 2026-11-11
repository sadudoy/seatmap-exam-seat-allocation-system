package com.seatmap.dao;

import com.seatmap.config.DatabaseConnector;
import com.seatmap.exception.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared JDBC execution helpers for DAO classes.
 */
final class JdbcSupport {

    @FunctionalInterface
    interface SqlConsumer<T> {
        void accept(T value) throws SQLException;
    }

    @FunctionalInterface
    interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private JdbcSupport() {
    }

    static <T> List<T> queryList(String sql, SqlConsumer<PreparedStatement> binder, RowMapper<T> mapper) {
        try (Connection connection = DatabaseConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.accept(statement);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
                return results;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Database query failed: " + ex.getMessage(), ex.getSQLState(), ex);
        }
    }

    static <T> Optional<T> queryOptional(String sql, SqlConsumer<PreparedStatement> binder, RowMapper<T> mapper) {
        try (Connection connection = DatabaseConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.accept(statement);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Database query failed: " + ex.getMessage(), ex.getSQLState(), ex);
        }
    }

    static long insertAndReturnId(String sql, SqlConsumer<PreparedStatement> binder) {
        try (Connection connection = DatabaseConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            binder.accept(statement);
            int affected = statement.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Insert affected zero rows", null);
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new DataAccessException("Insert succeeded but no generated key was returned", null);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Database insert failed: " + ex.getMessage(), ex.getSQLState(), ex);
        }
    }

    static int executeUpdate(String sql, SqlConsumer<PreparedStatement> binder) {
        try (Connection connection = DatabaseConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.accept(statement);
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Database update failed: " + ex.getMessage(), ex.getSQLState(), ex);
        }
    }

    static String escapeLike(String term) {
        return term
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
