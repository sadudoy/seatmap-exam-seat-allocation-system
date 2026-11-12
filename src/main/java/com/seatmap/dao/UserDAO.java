package com.seatmap.dao;

import com.seatmap.model.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * JDBC Data Access Object for {@link User} records supporting role-based access control.
 */
public class UserDAO {

    public Optional<User> getByUsername(String username) {
        String sql = "SELECT id, username, password_hash, full_name, role FROM users WHERE username = ?";
        return JdbcSupport.queryOptional(sql, ps -> ps.setString(1, username), this::mapRow);
    }

    public Optional<User> getById(long id) {
        String sql = "SELECT id, username, password_hash, full_name, role FROM users WHERE id = ?";
        return JdbcSupport.queryOptional(sql, ps -> ps.setLong(1, id), this::mapRow);
    }

    public User createUser(User user) {
        String sql = "INSERT INTO users (username, password_hash, full_name, role) VALUES (?, ?, ?, ?)";
        long id = JdbcSupport.insertAndReturnId(sql, ps -> {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getRole());
        });
        user.setId(id);
        return user;
    }

    public List<User> getAllTeachers() {
        String sql = """
                SELECT id, username, password_hash, full_name, role
                FROM users
                WHERE role = ?
                ORDER BY full_name, username
                """;
        return JdbcSupport.queryList(sql, ps -> ps.setString(1, User.ROLE_TEACHER), this::mapRow);
    }

    public List<User> getAll() {
        String sql = "SELECT id, username, password_hash, full_name, role FROM users ORDER BY role, full_name, username";
        return JdbcSupport.queryList(sql, ps -> { }, this::mapRow);
    }

    public boolean deleteUser(long id) {
        String sql = "DELETE FROM users WHERE id = ?";
        return JdbcSupport.executeUpdate(sql, ps -> ps.setLong(1, id)) > 0;
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM users";
        return JdbcSupport.queryOptional(sql, ps -> { }, rs -> rs.getLong(1)).orElse(0L);
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("full_name"),
                rs.getString("role")
        );
    }
}
