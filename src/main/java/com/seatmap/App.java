package com.seatmap;

import com.seatmap.config.DatabaseConnector;
import com.seatmap.util.AlertHelper;
import com.seatmap.util.DataSeeder;
import com.seatmap.util.SceneManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Main single-window JavaFX Application launcher for the Seatmap Exam Seat Allocation System.
 */
public class App extends Application {

    private static final String APP_ICON_PATH = "/com/images/icon.png";

    private String startupError;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        try {
            DatabaseConnector.initializeDatabase();
            DataSeeder.seedIfEmpty();
        } catch (Exception ex) {
            startupError = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        }
    }

    @Override
    public void start(Stage primaryStage) {
        loadApplicationIcon(primaryStage);

        if (startupError != null) {
            AlertHelper.showError(
                    "Database Connection Error",
                    "Could not initialize the PostgreSQL database:\n\n" + startupError
                            + "\n\nVerify PostgreSQL is running and check src/main/resources/config.properties.");
            Platform.exit();
            return;
        }

        primaryStage.setMaximized(true);
        SceneManager.initialize(primaryStage);
        SceneManager.showLoginScreen();
    }

    @Override
    public void stop() {
        DatabaseConnector.close();
    }

    private void loadApplicationIcon(Stage stage) {
        try (InputStream iconStream = getClass().getResourceAsStream(APP_ICON_PATH)) {
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (IOException ignored) {
            // Non-fatal if icon resource cannot be read
        }
    }
}
