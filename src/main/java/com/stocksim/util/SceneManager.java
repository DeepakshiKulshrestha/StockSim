package com.stocksim.util;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Stage;

/**
 * Central place for loading FXML screens, applying the stylesheet and
 * showing JavaFX dialogs. Keeps navigation code out of every controller.
 */
public final class SceneManager {

    private static final Logger LOGGER = Logger.getLogger(SceneManager.class.getName());

    private static final String FXML_DIR = "/com/stocksim/fxml/";
    private static final String STYLESHEET = "/styles/main.css";

    private static Stage primaryStage;

    private SceneManager() {
    }

    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    /** Loads an FXML file and returns its root node. */
    public static Parent load(String fxmlName) throws IOException {
        return (Parent) loader(fxmlName).load();
    }

    /** Loader for callers that also need the controller instance. */
    public static FXMLLoader loader(String fxmlName) {
        String path = FXML_DIR + fxmlName;
        URL url = SceneManager.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("FXML not found on classpath: " + path);
        }
        return new FXMLLoader(url);
    }

    /** Replaces the content of the primary stage with the given screen. */
    public static void switchTo(String fxmlName, String windowTitle) {
        switchTo(fxmlName, windowTitle, null);
    }

    /**
     * Same as {@link #switchTo(String, String)} but hands the new controller to
     * {@code controllerSetup} first, so a screen can be told what to show
     * (for example which stock the details screen should open).
     */
    public static <T> void switchTo(String fxmlName, String windowTitle, Consumer<T> controllerSetup) {
        try {
            FXMLLoader fxmlLoader = loader(fxmlName);
            Parent root = fxmlLoader.load();
            if (controllerSetup != null) {
                @SuppressWarnings("unchecked")
                T controller = (T) fxmlLoader.getController();
                controllerSetup.accept(controller);
            }
            Scene scene = primaryStage.getScene();
            if (scene == null) {
                scene = new Scene(root);
                applyStylesheet(scene);
                primaryStage.setScene(scene);
            } else {
                scene.setRoot(root);
            }
            primaryStage.setTitle("StockSim - " + windowTitle);
            LOGGER.info("Navigated to " + fxmlName);
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Failed to load screen " + fxmlName, e);
            error("Navigation Error", "Could not open that screen.", e.getMessage());
        }
    }

    public static void applyStylesheet(Scene scene) {
        URL css = SceneManager.class.getResource(STYLESHEET);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        } else {
            LOGGER.warning("Stylesheet not found: " + STYLESHEET);
        }
    }

    public static void applyStylesheet(Dialog<?> dialog) {
        URL css = SceneManager.class.getResource(STYLESHEET);
        if (css != null) {
            dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
    }

    // ---------------- dialogs ----------------

    public static void error(String title, String header, String content) {
        show(Alert.AlertType.ERROR, title, header, content);
    }

    public static void info(String title, String header, String content) {
        show(Alert.AlertType.INFORMATION, title, header, content);
    }

    public static void warn(String title, String header, String content) {
        show(Alert.AlertType.WARNING, title, header, content);
    }

    public static boolean confirm(String title, String header, String content) {
        Alert alert = buildAlert(Alert.AlertType.CONFIRMATION, title, header, content);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private static void show(Alert.AlertType type, String title, String header, String content) {
        buildAlert(type, title, header, content).showAndWait();
    }

    private static Alert buildAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        applyStylesheet(alert);
        if (primaryStage != null && primaryStage.isShowing()) {
            alert.initOwner(primaryStage);
        }
        return alert;
    }
}
