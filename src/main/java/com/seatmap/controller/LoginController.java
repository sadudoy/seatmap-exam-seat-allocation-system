package com.seatmap.controller;

import com.seatmap.exception.ServiceException;
import com.seatmap.model.Admin;
import com.seatmap.service.AuthService;
import com.seatmap.util.AlertHelper;
import com.seatmap.util.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Controller for {@code login.fxml} authenticating administrators via {@link AuthService}.
 */
public class LoginController {

    @FXML
    private HBox rootPane;

    @FXML
    private VBox leftPane;

    @FXML
    private VBox rightPane;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label errorLabel;

    @FXML
    private Button loginButton;

    private final AuthService authService = new AuthService();

    @FXML
    private void initialize() {
        leftPane.prefWidthProperty().bind(rootPane.widthProperty().multiply(0.5));
        rightPane.prefWidthProperty().bind(rootPane.widthProperty().multiply(0.5));
    }

    @FXML
    private void handleLogin() {
        clearInlineError();

        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showInlineError("Please enter both username and password.");
            return;
        }

        try {
            Optional<Admin> authenticated = authService.login(username, password);
            if (authenticated.isPresent()) {
                SceneManager.setCurrentAdmin(authenticated.get());
                SceneManager.showDashboardScreen();
            } else {
                showInlineError("Invalid username or password.");
                passwordField.clear();
                passwordField.requestFocus();
            }
        } catch (ServiceException ex) {
            AlertHelper.showError("Authentication Error",
                    "Unable to verify credentials against the database:\n" + ex.getMessage());
        }
    }

    private void showInlineError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearInlineError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
