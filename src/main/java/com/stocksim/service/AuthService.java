package com.stocksim.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import com.stocksim.dao.UserDAO;
import com.stocksim.exception.UserNotFoundException;
import com.stocksim.model.User;
import com.stocksim.util.ConfigLoader;
import com.stocksim.util.PasswordUtil;
import com.stocksim.util.Validator;

/** Registration and login. Passwords are hashed with SHA-256 before they ever reach MySQL. */
public class AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());

    private final UserDAO userDAO;

    public AuthService() {
        this(new UserDAO());
    }

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * Validates the form, checks that username and email are free, hashes the
     * password and inserts the user with the configured starting capital.
     *
     * @return the saved user, with its generated user_id filled in
     * @throws IllegalArgumentException carrying a user-readable message when validation fails
     */
    public User register(String name, String username, String email,
                         String password, String confirmPassword) {

        List<String> errors = validateRegistration(name, username, email, password, confirmPassword);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errors));
        }

        double startingBalance = ConfigLoader.getDouble("sim.starting.balance", 100000.00);
        User user = new User(name.trim(), username.trim(), email.trim().toLowerCase(),
                PasswordUtil.hash(password), startingBalance);
        user.setSimDay(1);

        if (!userDAO.save(user)) {
            throw new IllegalStateException("Could not create the account. Please try again.");
        }
        LOGGER.info("User registered: " + user.getUsername());
        return user;
    }

    /** Field-by-field validation, so the register screen can show every problem at once. */
    public List<String> validateRegistration(String name, String username, String email,
                                             String password, String confirmPassword) {
        List<String> errors = new ArrayList<>();

        if (!Validator.isNotEmpty(name)) {
            errors.add("Full name is required.");
        }
        if (!Validator.isNotEmpty(username)) {
            errors.add("Username is required.");
        } else if (!Validator.isValidUsername(username)) {
            errors.add("Username must be 3-50 characters: letters, digits or underscore only.");
        } else if (userDAO.existsByUsername(username.trim())) {
            errors.add("That username is already taken.");
        }
        if (!Validator.isNotEmpty(email)) {
            errors.add("Email is required.");
        } else if (!Validator.isValidEmail(email)) {
            errors.add("Please enter a valid email address.");
        } else if (userDAO.existsByEmail(email.trim().toLowerCase())) {
            errors.add("An account with that email already exists.");
        }
        if (!Validator.isValidPassword(password)) {
            errors.add("Password must be at least " + Validator.MIN_PASSWORD_LENGTH + " characters.");
        } else if (!Validator.passwordsMatch(password, confirmPassword)) {
            errors.add("Passwords do not match.");
        }
        return errors;
    }

    /**
     * Logs a user in by username or email.
     *
     * @throws UserNotFoundException when no such user exists or the password is wrong
     *         (one message for both, so the screen never reveals which usernames exist)
     */
    public User login(String identifier, String password) throws UserNotFoundException {
        if (!Validator.isNotEmpty(identifier) || !Validator.isNotEmpty(password)) {
            throw new UserNotFoundException("Please enter both your username/email and password.");
        }

        Optional<User> found = userDAO.findByUsernameOrEmail(identifier.trim());
        if (found.isEmpty()) {
            LOGGER.warning("Login failed - no such user: " + identifier);
            throw new UserNotFoundException("Invalid username or password.");
        }

        User user = found.get();
        if (!PasswordUtil.matches(password, user.getPassword())) {
            LOGGER.warning("Login failed - wrong password for: " + user.getUsername());
            throw new UserNotFoundException("Invalid username or password.");
        }

        LOGGER.info("Login successful: " + user.getUsername());
        return user;
    }
}
