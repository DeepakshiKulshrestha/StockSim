package com.stocksim.exception;

/**
 * Thrown when a user tries to buy shares costing more than their available cash balance.
 *
 * Checked exception: service classes declare it with {@code throws} and
 * controllers catch it to show a JavaFX Alert.
 */
public class InsufficientBalanceException extends Exception {

    private static final long serialVersionUID = 1L;

    public InsufficientBalanceException(String message) {
        super(message);
    }

    public InsufficientBalanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
