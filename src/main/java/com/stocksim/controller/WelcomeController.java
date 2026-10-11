package com.stocksim.controller;

import java.util.logging.Logger;

import com.stocksim.util.SceneManager;

import javafx.fxml.FXML;

/** Landing screen: sends the user to login or registration. */
public class WelcomeController {

    private static final Logger LOGGER = Logger.getLogger(WelcomeController.class.getName());

    @FXML
    private void handleLogin() {
        LOGGER.info("Welcome screen -> login");
        SceneManager.switchTo("login.fxml", "Login");
    }

    @FXML
    private void handleRegister() {
        LOGGER.info("Welcome screen -> register");
        SceneManager.switchTo("register.fxml", "Register");
    }
}
