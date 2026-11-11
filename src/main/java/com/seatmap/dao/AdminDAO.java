package com.seatmap.dao;

import com.seatmap.model.Admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * JDBC Data Access Object for {@link Admin} records.
 */
public class AdminDAO {

    public Optional<Admin> getByUsername(String username) {
        String sql = "SELECT id, username, password_hash FROM admins WHERE username = ?";
        return JdbcSupport.queryOptional(sql, ps -> ps.setString(1, username), this::mapRow);
    }

    public Admin createAdmin(Admin admin) {
        String sql = "INSERT INTO admins (username, password_hash) VALUES (?, ?)";
        long id = JdbcSupport.insertAndReturnId(sql, ps -> {
            ps.setString(1, admin.getUsername());
            ps.setString(2, admin.getPasswordHash());
        });
        admin.setId(id);
        return admin;
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM admins";
        return JdbcSupport.queryOptional(sql, ps -> { }, rs -> rs.getLong(1)).orElse(0L);
    }

    private Admin mapRow(ResultSet rs) throws SQLException {
        return new Admin(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash")
        );
    }
}
