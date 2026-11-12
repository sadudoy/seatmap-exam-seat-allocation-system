package com.seatmap.service;

import com.seatmap.config.DatabaseConnector;
import com.seatmap.model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AuthService")
class AuthServiceTest {

    private final AuthService authService = new AuthService();
    private String createdTestUsername;

    @BeforeAll
    static void initDb() throws SQLException {
        DatabaseConnector.initializeDatabase();
    }

    @AfterEach
    void cleanupCreatedUser() throws SQLException {
        if (createdTestUsername != null) {
            try (Connection conn = DatabaseConnector.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE username = ?")) {
                ps.setString(1, createdTestUsername);
                ps.executeUpdate();
            }
            createdTestUsername = null;
        }
    }

    @AfterAll
    static void closeDb() {
        DatabaseConnector.close();
    }

    @Test
    @DisplayName("hashes password with BCrypt and verifies matching candidate")
    void hashesAndVerifiesPassword() {
        String plain = "StrongPass!2026";
        String hash1 = authService.hashPassword(plain);
        String hash2 = authService.hashPassword(plain);

        assertNotEquals(plain, hash1);
        assertTrue(hash1.startsWith("$2a$") || hash1.startsWith("$2b$"));
        assertNotEquals(hash1, hash2, "Each call should generate a unique BCrypt salt");

        assertTrue(authService.verifyPassword(plain, hash1));
        assertFalse(authService.verifyPassword("WrongPass", hash1));
        assertFalse(authService.verifyPassword(null, hash1));
        assertFalse(authService.verifyPassword(plain, "not-a-bcrypt-hash"));
    }

    @Test
    @DisplayName("rejects blank password when hashing")
    void rejectsBlankPassword() {
        assertThrows(IllegalArgumentException.class, () -> authService.hashPassword("   "));
        assertThrows(IllegalArgumentException.class, () -> authService.hashPassword(null));
    }

    @Test
    @DisplayName("registers RBAC user with hashed password and authenticates via login")
    void registersAndLogsInUser() {
        createdTestUsername = "test_teacher_" + UUID.randomUUID();
        String password = "SecretTeacherPassword#99";

        User created = authService.registerUser(
                createdTestUsername, password, "Test Teacher", User.ROLE_TEACHER);
        assertTrue(created.getId() > 0);
        assertNotEquals(password, created.getPasswordHash());
        assertEquals(User.ROLE_TEACHER, created.getRole());

        Optional<User> valid = authService.login(createdTestUsername, password);
        Optional<User> invalid = authService.login(createdTestUsername, "WrongPassword");

        assertTrue(valid.isPresent());
        assertEquals("Test Teacher", valid.get().getFullName());
        assertTrue(valid.get().isTeacher());
        assertFalse(invalid.isPresent());
    }
}
