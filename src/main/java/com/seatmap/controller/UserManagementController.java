package com.seatmap.controller;

import com.seatmap.dao.UserDAO;
import com.seatmap.exception.DataAccessException;
import com.seatmap.exception.ServiceException;
import com.seatmap.model.User;
import com.seatmap.service.AuthService;
import com.seatmap.util.AlertHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/**
 * Controller for {@code user_management.fxml} allowing Master Admins to manage teacher accounts.
 */
public class UserManagementController {

    @FXML
    private TableView<User> teacherTable;

    @FXML
    private TableColumn<User, String> fullNameCol;

    @FXML
    private TableColumn<User, String> usernameCol;

    @FXML
    private TableColumn<User, String> roleCol;

    @FXML
    private TextField fullNameField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button deleteButton;

    private final UserDAO userDAO = new UserDAO();
    private final AuthService authService = new AuthService(userDAO);
    private User selectedTeacher;

    @FXML
    private void initialize() {
        fullNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFullName()));
        usernameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        roleCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRole()));

        teacherTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedTeacher = newVal;
            deleteButton.setDisable(newVal == null);
        });

        loadTeachers();
    }

    @FXML
    private void loadTeachers() {
        try {
            teacherTable.setItems(FXCollections.observableArrayList(userDAO.getAllTeachers()));
        } catch (DataAccessException ex) {
            AlertHelper.showError("Database Error", "Failed to load teachers:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleCreateTeacher() {
        String fullName = safeTrim(fullNameField.getText());
        String username = safeTrim(usernameField.getText());
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (fullName.isEmpty() || username.isEmpty() || password.isBlank()) {
            AlertHelper.showWarning("Validation Error",
                    "Full Name, Username, and Password are all required.");
            return;
        }

        try {
            authService.registerUser(username, password, fullName, User.ROLE_TEACHER);
            loadTeachers();
            handleClearForm();
            AlertHelper.showInfo("Teacher Created", "Teacher account created successfully.");
        } catch (ServiceException | IllegalArgumentException ex) {
            AlertHelper.showError("Create Teacher Failed", ex.getMessage());
        }
    }

    @FXML
    private void handleDeleteTeacher() {
        if (selectedTeacher == null) {
            return;
        }
        boolean confirmed = AlertHelper.showConfirmation(
                "Confirm Delete",
                "Are you sure you want to delete teacher \"" + selectedTeacher.getFullName()
                        + "\" (" + selectedTeacher.getUsername() + ")? Their room assignments will also be removed.");
        if (!confirmed) {
            return;
        }

        try {
            if (userDAO.deleteUser(selectedTeacher.getId())) {
                loadTeachers();
                handleClearForm();
                AlertHelper.showInfo("Teacher Deleted", "Teacher account removed successfully.");
            }
        } catch (DataAccessException ex) {
            AlertHelper.showError("Delete Failed", "Could not delete teacher:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleClearForm() {
        selectedTeacher = null;
        teacherTable.getSelectionModel().clearSelection();
        fullNameField.clear();
        usernameField.clear();
        passwordField.clear();
        deleteButton.setDisable(true);
    }

    private static String safeTrim(String text) {
        return text == null ? "" : text.trim();
    }
}
