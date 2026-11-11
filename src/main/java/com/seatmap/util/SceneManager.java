package com.seatmap.util;

import com.seatmap.model.Admin;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

/**
 * Central single-window navigation and session manager for Seatmap.
 */
public final class SceneManager {

    public static final double DEFAULT_WIDTH = 1280;
    public static final double DEFAULT_HEIGHT = 800;

    private static final String FXML_BASE = "/com/seatmap/fxml/";
    private static final String STYLESHEET = "/com/seatmap/css/style.css";

    private static Stage primaryStage;
    private static Admin currentAdmin;

    private SceneManager() {
    }

    public static void initialize(Stage stage) {
        primaryStage = Objects.requireNonNull(stage, "Primary stage must not be null");
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static Admin getCurrentAdmin() {
        return currentAdmin;
    }

    public static void setCurrentAdmin(Admin admin) {
        currentAdmin = admin;
    }

    /**
     * Displays the login screen inside the maximized single primary stage.
     */
    public static void showLoginScreen() {
        switchRoot("login.fxml", "Seatmap - Administrator Login");
    }

    /**
     * Displays the dashboard workspace inside the maximized single primary stage.
     */
    public static void showDashboardScreen() {
        switchRoot("dashboard.fxml", "Seatmap - Exam Seat Allocation System");
    }

    /**
     * Swaps the root node of the existing stage's Scene (creating the Scene on initial launch)
     * while keeping the primary stage resizable and maximized.
     */
    public static void switchRoot(String fxmlName, String title) {
        if (primaryStage == null) {
            throw new IllegalStateException("SceneManager has not been initialized");
        }
        try {
            FXMLLoader loader = createLoader(fxmlName);
            Parent root = loader.load();

            Scene scene = primaryStage.getScene();
            if (scene == null) {
                scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
                String cssUrl = Objects.requireNonNull(
                        SceneManager.class.getResource(STYLESHEET),
                        "Stylesheet not found: " + STYLESHEET).toExternalForm();
                scene.getStylesheets().add(cssUrl);
                primaryStage.setScene(scene);
            } else {
                scene.setRoot(root);
            }

            primaryStage.setTitle(title);
            primaryStage.setResizable(true);
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(650);
            primaryStage.setMaxWidth(Double.MAX_VALUE);
            primaryStage.setMaxHeight(Double.MAX_VALUE);
            primaryStage.setMaximized(true);

            if (!primaryStage.isShowing()) {
                primaryStage.show();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load view: " + fxmlName, ex);
        }
    }

    /**
     * Loads an FXML view and returns its {@link FXMLLoader} so callers can access the root and controller.
     */
    public static FXMLLoader createLoader(String fxmlName) {
        URL fxmlUrl = Objects.requireNonNull(
                SceneManager.class.getResource(FXML_BASE + fxmlName),
                "FXML resource not found: " + FXML_BASE + fxmlName);
        return new FXMLLoader(fxmlUrl);
    }
}
