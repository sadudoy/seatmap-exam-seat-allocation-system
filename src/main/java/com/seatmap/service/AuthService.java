package com.seatmap.service;

import com.seatmap.dao.UserDAO;
import com.seatmap.exception.DataAccessException;
import com.seatmap.exception.ServiceException;
import com.seatmap.model.User;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Locale;
import java.util.Optional;

/**
 * Handles user registration and role-aware authentication using BCrypt password hashing.
 */
public class AuthService {

    private static final int BCRYPT_WORK_FACTOR = 10;
    private final UserDAO userDAO;

    public AuthService() {
        this(new UserDAO());
    }

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * Hashes a plain-text password using BCrypt.
     */
    public String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_WORK_FACTOR));
    }

    /**
     * Verifies a plain-text password against a stored BCrypt hash.
     */
    public boolean verifyPassword(String plainPassword, String passwordHash) {
        if (plainPassword == null || plainPassword.isEmpty()
                || passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, passwordHash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Registers a new user (Master Admin or Teacher) with a BCrypt-hashed password.
     */
    public User registerUser(String username, String plainPassword, String fullName, String role) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name must not be blank");
        }
        String normalizedRole = normalizeRole(role);
        String normalizedUsername = username.trim();
        String hash = hashPassword(plainPassword);

        try {
            if (userDAO.getByUsername(normalizedUsername).isPresent()) {
                throw new ServiceException("Username already exists: " + normalizedUsername);
            }
            return userDAO.createUser(new User(normalizedUsername, hash, fullName.trim(), normalizedRole));
        } catch (DataAccessException ex) {
            throw new ServiceException("Failed to register user: " + ex.getMessage(), ex);
        }
    }

    /**
     * Authenticates a user by username and plain-text password and returns their {@link User} profile and role.
     */
    public Optional<User> login(String username, String plainPassword) {
        if (username == null || username.isBlank() || plainPassword == null || plainPassword.isEmpty()) {
            return Optional.empty();
        }
        try {
            Optional<User> userOpt = userDAO.getByUsername(username.trim());
            if (userOpt.isEmpty()) {
                return Optional.empty();
            }
            User user = userOpt.get();
            return verifyPassword(plainPassword, user.getPasswordHash())
                    ? Optional.of(user)
                    : Optional.empty();
        } catch (DataAccessException ex) {
            throw new ServiceException("Authentication lookup failed: " + ex.getMessage(), ex);
        }
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role must not be blank");
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (!User.ROLE_MASTER_ADMIN.equals(normalized) && !User.ROLE_TEACHER.equals(normalized)) {
            throw new IllegalArgumentException("Unsupported role: " + role);
        }
        return normalized;
    }
}
