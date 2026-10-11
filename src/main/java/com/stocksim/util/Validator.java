package com.stocksim.util;

import java.util.regex.Pattern;

/**
 * Static validation helpers shared by the registration form, the trade dialogs
 * and the goal/alert forms. Demonstrates: static utility class, regex.
 */
public final class Validator {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9_]{3,50}$");

    public static final int MIN_PASSWORD_LENGTH = 8;

    private Validator() {
    }

    public static boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static boolean isValidEmail(String email) {
        return isNotEmpty(email) && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /** 3-50 characters, letters/digits/underscore only. */
    public static boolean isValidUsername(String username) {
        return isNotEmpty(username) && USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    /** At least 8 characters, as stated in the project spec. */
    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH;
    }

    public static boolean passwordsMatch(String password, String confirmPassword) {
        return password != null && password.equals(confirmPassword);
    }

    /** True only for a string that parses to an integer greater than zero. */
    public static boolean isPositiveInteger(String value) {
        if (!isNotEmpty(value)) {
            return false;
        }
        try {
            return Integer.parseInt(value.trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** True only for a string that parses to a double greater than zero. */
    public static boolean isPositiveDouble(String value) {
        if (!isNotEmpty(value)) {
            return false;
        }
        try {
            return Double.parseDouble(value.trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }

    public static double parseDoubleOrZero(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return 0.0;
        }
    }
}
