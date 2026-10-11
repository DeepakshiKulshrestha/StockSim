package com.stocksim.exception;

/**
 * Thrown when a trade quantity is missing, non-numeric, zero or negative.
 *
 * Checked exception: service classes declare it with {@code throws} and
 * controllers catch it to show a JavaFX Alert.
 */
public class InvalidQuantityException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidQuantityException(String message) {
        super(message);
    }

    public InvalidQuantityException(String message, Throwable cause) {
        super(message, cause);
    }
}
