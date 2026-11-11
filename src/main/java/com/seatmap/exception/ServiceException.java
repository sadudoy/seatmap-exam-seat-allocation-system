package com.seatmap.exception;

/**
 * Unchecked exception representing a business-logic or service-layer failure.
 */
public class ServiceException extends RuntimeException {

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
