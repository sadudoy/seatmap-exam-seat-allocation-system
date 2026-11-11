package com.seatmap.service;

import com.seatmap.dao.AdminDAO;
import com.seatmap.exception.DataAccessException;
import com.seatmap.exception.ServiceException;
import com.seatmap.model.Admin;
import org.mindrot.jbcrypt.BCrypt;

import java.util.Optional;

/**
 * Handles administrator registration and authentication using BCrypt password hashing.
 */
public class AuthService {

    private static final int BCRYPT_WORK_FACTOR = 10;
    private final AdminDAO adminDAO;

    public AuthService() {
        this(new AdminDAO());
    }

    public AuthService(AdminDAO adminDAO) {
        this.adminDAO = adminDAO;
    }

    /**
     * Hashes a plain-text password using BCrypt.
     *
     * @param plainPassword plain-text password
     * @return BCrypt hash
     */
    public String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_WORK_FACTOR));
    }

    /**
     * Verifies a plain-text password against a stored BCrypt hash.
     *
     * @param plainPassword plain-text password candidate
     * @param passwordHash  stored BCrypt hash
     * @return {@code true} if the password matches the hash
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
     * Registers a new administrator with a BCrypt-hashed password.
     *
     * @param username      unique username
     * @param plainPassword plain-text password (never persisted directly)
     * @return persisted {@link Admin}
     */
    public Admin registerAdmin(String username, String plainPassword) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        String normalizedUsername = username.trim();
        String hash = hashPassword(plainPassword);

        try {
            if (adminDAO.getByUsername(normalizedUsername).isPresent()) {
                throw new ServiceException("Username already exists: " + normalizedUsername);
            }
            return adminDAO.createAdmin(new Admin(normalizedUsername, hash));
        } catch (DataAccessException ex) {
            throw new ServiceException("Failed to register administrator: " + ex.getMessage(), ex);
        }
    }

    /**
     * Authenticates an administrator by username and plain-text password.
     *
     * @param username      admin username
     * @param plainPassword plain-text password
     * @return authenticated {@link Admin}, or {@link Optional#empty()} if credentials are invalid
     */
    public Optional<Admin> login(String username, String plainPassword) {
        if (username == null || username.isBlank() || plainPassword == null || plainPassword.isEmpty()) {
            return Optional.empty();
        }
        try {
            Optional<Admin> adminOpt = adminDAO.getByUsername(username.trim());
            if (adminOpt.isEmpty()) {
                return Optional.empty();
            }
            Admin admin = adminOpt.get();
            return verifyPassword(plainPassword, admin.getPasswordHash())
                    ? Optional.of(admin)
                    : Optional.empty();
        } catch (DataAccessException ex) {
            throw new ServiceException("Authentication lookup failed: " + ex.getMessage(), ex);
        }
    }
}
