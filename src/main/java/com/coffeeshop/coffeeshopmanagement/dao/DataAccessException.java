package com.coffeeshop.coffeeshopmanagement.dao;

/**
 * Wraps low-level SQLException in an unchecked exception so controllers can catch one
 * meaningful type and show the user a friendly alert, instead of a raw SQL stack trace.
 */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
