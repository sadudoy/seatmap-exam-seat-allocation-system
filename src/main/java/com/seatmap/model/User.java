package com.seatmap.model;

/**
 * Represents an authenticated user account with role-based access control.
 */
public class User {

    public static final String ROLE_MASTER_ADMIN = "MASTER_ADMIN";
    public static final String ROLE_TEACHER = "TEACHER";

    private long id;
    private String username;
    private String passwordHash;
    private String fullName;
    private String role;

    public User() {
    }

    public User(String username, String passwordHash, String fullName, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    public User(long id, String username, String passwordHash, String fullName, String role) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isMasterAdmin() {
        return ROLE_MASTER_ADMIN.equalsIgnoreCase(role);
    }

    public boolean isTeacher() {
        return ROLE_TEACHER.equalsIgnoreCase(role);
    }
}
