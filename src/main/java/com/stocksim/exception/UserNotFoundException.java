package com.stocksim.exception;

/**
 * Thrown when no user matches the supplied username/email, or the password does not match.
 *
 * Checked exception: service classes declare it with {@code throws} and
 * controllers catch it to show a JavaFX Alert.
 */
public class UserNotFoundException extends Exception {

    private static final long serialVersionUID = 1L;

    public UserNotFoundException(String message) {
        super(message);
    }

    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
