package com.stocksim.exception;

/**
 * Thrown when a user tries to sell more shares than they actually hold.
 *
 * Checked exception: service classes declare it with {@code throws} and
 * controllers catch it to show a JavaFX Alert.
 */
public class InsufficientSharesException extends Exception {

    private static final long serialVersionUID = 1L;

    public InsufficientSharesException(String message) {
        super(message);
    }

    public InsufficientSharesException(String message, Throwable cause) {
        super(message, cause);
    }
}
