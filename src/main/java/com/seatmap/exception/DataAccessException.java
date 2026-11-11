package com.seatmap.exception;

/**
 * Unchecked exception thrown when a database access operation fails.
 */
public class DataAccessException extends RuntimeException {

    private final String sqlState;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
        this.sqlState = null;
    }

    public DataAccessException(String message, String sqlState, Throwable cause) {
        super(message, cause);
        this.sqlState = sqlState;
    }

    public String getSqlState() {
        return sqlState;
    }
}
