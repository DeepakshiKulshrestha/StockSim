package com.stocksim.controller;

import java.util.List;
import java.util.logging.Logger;

import com.stocksim.model.User;
import com.stocksim.service.AuthService;
import com.stocksim.service.SimulationService;
import com.stocksim.util.ConfigLoader;
import com.stocksim.util.Formats;
import com.stocksim.util.SceneManager;
import com.stocksim.util.UserSession;
import com.stocksim.util.Validator;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/** Registration screen with per-field inline validation messages. */
public class RegisterController {

    private static final Logger LOGGER = Logger.getLogger(RegisterController.class.getName());

    @FXML private TextField nameField;
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;

    @FXML private Label nameError;
    @FXML private Label usernameError;
    @FXML private Label emailError;
    @FXML private Label passwordError;
    @FXML private Label confirmError;

    private final AuthService authService = new AuthService();
    private final SimulationService simulationService = new SimulationService();

    @FXML
    private void handleRegister() {
        clearErrors();

        if (!validateLocally()) {
            return;                       // cheap checks first, before touching the database
        }

        try {
            User user = authService.register(
                    nameField.getText(), usernameField.getText(), emailField.getText(),
                    passwordField.getText(), confirmPasswordField.getText());

            UserSession.getInstance().setCurrentUser(user);
            simulationService.syncPricesToUser(user.getUserId());

            double capital = ConfigLoader.getDouble("sim.starting.balance", 100000.00);
            SceneManager.info("Account Created",
                    "Welcome to StockSim, " + user.getName() + "!",
                    "You have " + Formats.money(capital) + " of virtual capital to invest.\n\n"
                            + "This is a simulator - no real money is involved at any point.");

            LOGGER.info("Registered and signed in: " + user.getUsername());
            SceneManager.switchTo("dashboard.fxml", "Dashboard");

        } catch (IllegalArgumentException e) {
            // the service re-checks uniqueness, so route those messages to the right field
            distributeServerErrors(List.of(e.getMessage().split("\n")));
        } catch (RuntimeException e) {
            LOGGER.severe("Registration failed: " + e.getMessage());
            SceneManager.error("Registration Error", "Could not create your account.",
                    e.getMessage());
        }
    }

    @FXML
    private void handleGoToLogin() {
        SceneManager.switchTo("login.fxml", "Login");
    }

    // ---------------------------------------------------------------

    private boolean validateLocally() {
        boolean valid = true;

        if (!Validator.isNotEmpty(nameField.getText())) {
            show(nameError, "Full name is required.");
            valid = false;
        }
        if (!Validator.isNotEmpty(usernameField.getText())) {
            show(usernameError, "Username is required.");
            valid = false;
        } else if (!Validator.isValidUsername(usernameField.getText())) {
            show(usernameError, "3-50 characters: letters, digits or underscore only.");
            valid = false;
        }
        if (!Validator.isValidEmail(emailField.getText())) {
            show(emailError, "Please enter a valid email address.");
            valid = false;
        }
        if (!Validator.isValidPassword(passwordField.getText())) {
            show(passwordError, "Password must be at least "
                    + Validator.MIN_PASSWORD_LENGTH + " characters.");
            valid = false;
        }
        if (!Validator.passwordsMatch(passwordField.getText(), confirmPasswordField.getText())) {
            show(confirmError, "Passwords do not match.");
            valid = false;
        }
        return valid;
    }

    private void distributeServerErrors(List<String> messages) {
        for (String message : messages) {
            String lower = message.toLowerCase();
            if (lower.contains("username")) {
                show(usernameError, message);
            } else if (lower.contains("email")) {
                show(emailError, message);
            } else if (lower.contains("password")) {
                show(passwordError, message);
            } else if (lower.contains("name")) {
                show(nameError, message);
            } else {
                SceneManager.error("Registration Error", "Please check your details.", message);
            }
        }
    }

    private void show(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void clearErrors() {
        for (Label label : List.of(nameError, usernameError, emailError,
                                   passwordError, confirmError)) {
            label.setVisible(false);
            label.setManaged(false);
        }
    }
}
