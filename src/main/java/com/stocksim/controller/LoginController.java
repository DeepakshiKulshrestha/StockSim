package com.stocksim.controller;

import java.util.logging.Logger;

import com.stocksim.exception.UserNotFoundException;
import com.stocksim.model.User;
import com.stocksim.service.AuthService;
import com.stocksim.service.SimulationService;
import com.stocksim.util.SceneManager;
import com.stocksim.util.UserSession;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/** Login screen: authenticates, opens the session, and syncs the market to that user's day. */
public class LoginController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    @FXML private TextField identifierField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private final AuthService authService = new AuthService();
    private final SimulationService simulationService = new SimulationService();

    @FXML
    private void initialize() {
        identifierField.textProperty().addListener((obs, old, now) -> hideError());
        passwordField.textProperty().addListener((obs, old, now) -> hideError());
    }

    @FXML
    private void handleLogin() {
        hideError();
        try {
            User user = authService.login(identifierField.getText(), passwordField.getText());

            UserSession.getInstance().setCurrentUser(user);
            // put every stock price back on the day this user left off
            simulationService.syncPricesToUser(user.getUserId());

            LOGGER.info("Login successful: " + user.getUsername());
            SceneManager.switchTo("dashboard.fxml", "Dashboard");

        } catch (UserNotFoundException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.severe("Login failed unexpectedly: " + e.getMessage());
            SceneManager.error("Login Error", "Something went wrong while signing in.",
                    e.getMessage());
        }
    }

    @FXML
    private void handleGoToRegister() {
        SceneManager.switchTo("register.fxml", "Register");
    }

    @FXML
    private void handleBack() {
        SceneManager.switchTo("welcome.fxml", "Welcome");
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
