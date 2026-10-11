package com.stocksim;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.stocksim.util.DBConnection;
import com.stocksim.util.DataSeeder;
import com.stocksim.util.SceneManager;
import com.stocksim.util.UserSession;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * StockSim entry point.
 *
 * Demonstrates the full JavaFX application lifecycle (Unit 5):
 * init() -> start() -> stop().
 */
public class Main extends Application {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private boolean databaseReady;

    /** Runs before the UI thread starts - good place for database work. */
    @Override
    public void init() {
        LOGGER.info("StockSim starting up...");
        databaseReady = DBConnection.testConnection();
        if (databaseReady) {
            try {
                DataSeeder.seedIfEmpty();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Data seeding failed", e);
            }
        } else {
            LOGGER.severe("Database is not reachable - check that MySQL is running.");
        }
    }

    @Override
    public void start(Stage primaryStage) {
        SceneManager.setPrimaryStage(primaryStage);

        if (!databaseReady) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("StockSim - Database Error");
            alert.setHeaderText("Cannot connect to MySQL");
            alert.setContentText("""
                    StockSim could not reach the 'stocksim' database.

                    Please check:
                      1. MySQL is running  (brew services start mysql)
                      2. The database exists (mysql -u root -p < src/main/resources/database.sql)
                      3. db.user / db.password in application.properties are correct""");
            alert.showAndWait();
            javafx.application.Platform.exit();
            return;
        }

        try {
            // Size the window against the screen it will actually open on, so the
            // larger text size still fits on a laptop display without being clipped.
            Rectangle2D screen = Screen.getPrimary().getVisualBounds();
            double width = Math.min(1500, screen.getWidth() * 0.94);
            double height = Math.min(980, screen.getHeight() * 0.94);

            Parent root = SceneManager.load("welcome.fxml");
            Scene scene = new Scene(root, width, height);
            SceneManager.applyStylesheet(scene);

            primaryStage.setTitle("StockSim - Learn. Simulate. Invest Virtually.");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(Math.min(1120, screen.getWidth()));
            primaryStage.setMinHeight(Math.min(740, screen.getHeight()));
            primaryStage.centerOnScreen();
            primaryStage.show();

            LOGGER.info(String.format("StockSim UI ready (%.0fx%.0f on a %.0fx%.0f screen)",
                    width, height, screen.getWidth(), screen.getHeight()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start the JavaFX UI", e);
            throw new IllegalStateException("Failed to start StockSim UI", e);
        }
    }

    /** Runs when the last window closes. */
    @Override
    public void stop() {
        LOGGER.info("StockSim shutting down...");
        UserSession.getInstance().logout();
        // Connections are per-operation and already closed by the DAOs' try-with-resources,
        // so there is nothing left to release here.
    }

    public static void main(String[] args) {
        launch(args);
    }
}
