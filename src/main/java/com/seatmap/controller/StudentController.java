package com.seatmap.controller;

import com.seatmap.dao.StudentDAO;
import com.seatmap.exception.DataAccessException;
import com.seatmap.exception.ServiceException;
import com.seatmap.model.Student;
import com.seatmap.service.CsvService;
import com.seatmap.service.CsvService.CsvParseResult;
import com.seatmap.util.AlertHelper;
import com.seatmap.util.SceneManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Controller for {@code student.fxml} supporting CRUD, name search, and bulk CSV import.
 */
public class StudentController {

    @FXML
    private TextField searchField;

    @FXML
    private TableView<Student> studentTable;

    @FXML
    private TableColumn<Student, String> rollCol;

    @FXML
    private TableColumn<Student, String> nameCol;

    @FXML
    private TableColumn<Student, String> departmentCol;

    @FXML
    private TableColumn<Student, String> semesterCol;

    @FXML
    private TextField rollField;

    @FXML
    private TextField nameField;

    @FXML
    private TextField departmentField;

    @FXML
    private TextField semesterField;

    @FXML
    private Button updateButton;

    @FXML
    private Button deleteButton;

    private final StudentDAO studentDAO = new StudentDAO();
    private final CsvService csvService = new CsvService();
    private Student selectedStudent;

    @FXML
    private void initialize() {
        rollCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRollNumber()));
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        departmentCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDepartment()));
        semesterCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSemester()));

        studentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedStudent = newVal;
            populateForm(newVal);
        });

        loadStudents();
    }

    @FXML
    private void handleSearch() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        try {
            List<Student> results = query.isEmpty() ? studentDAO.getAll() : studentDAO.searchByName(query);
            studentTable.setItems(FXCollections.observableArrayList(results));
        } catch (DataAccessException ex) {
            AlertHelper.showError("Search Failed", "Unable to search students:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleResetSearch() {
        searchField.clear();
        loadStudents();
    }

    @FXML
    private void handleAdd() {
        Student candidate = validateForm();
        if (candidate == null) {
            return;
        }
        try {
            studentDAO.add(candidate);
            loadStudents();
            handleClearForm();
            AlertHelper.showInfo("Student Added", "Student added successfully.");
        } catch (DataAccessException ex) {
            AlertHelper.showError("Add Student Failed",
                    "Could not add student (check that roll number is unique):\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleUpdate() {
        if (selectedStudent == null) {
            AlertHelper.showWarning("No Selection", "Please select a student from the table to update.");
            return;
        }
        Student updated = validateForm();
        if (updated == null) {
            return;
        }
        updated.setId(selectedStudent.getId());

        try {
            if (studentDAO.update(updated)) {
                loadStudents();
                handleClearForm();
                AlertHelper.showInfo("Student Updated", "Student record updated successfully.");
            } else {
                AlertHelper.showWarning("Update Failed", "Student record could not be found.");
            }
        } catch (DataAccessException ex) {
            AlertHelper.showError("Update Student Failed",
                    "Could not update student:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        if (selectedStudent == null) {
            return;
        }
        boolean confirmed = AlertHelper.showConfirmation(
                "Confirm Delete",
                "Are you sure you want to delete student " + selectedStudent.getName()
                        + " (" + selectedStudent.getRollNumber() + ")?");
        if (!confirmed) {
            return;
        }
        try {
            if (studentDAO.delete(selectedStudent.getId())) {
                loadStudents();
                handleClearForm();
                AlertHelper.showInfo("Student Deleted", "Student deleted successfully.");
            }
        } catch (DataAccessException ex) {
            AlertHelper.showError("Delete Failed", "Could not delete student:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleImportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Students CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        File selectedFile = chooser.showOpenDialog(SceneManager.getPrimaryStage());
        if (selectedFile == null) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(selectedFile.toPath(), StandardCharsets.UTF_8)) {
            CsvParseResult result = csvService.parseStudentsWithReport(reader);
            List<Student> parsed = result.students();
            if (parsed.isEmpty()) {
                AlertHelper.showWarning("CSV Import",
                        "No valid student rows were found in the selected CSV file.");
                return;
            }

            int inserted = studentDAO.addAll(parsed);
            loadStudents();

            StringBuilder summary = new StringBuilder();
            summary.append("Successfully imported ").append(inserted).append(" student(s).");
            if (!result.skippedRowWarnings().isEmpty()) {
                summary.append("\n\nSkipped ").append(result.skippedRowWarnings().size())
                        .append(" malformed row(s).");
            }
            AlertHelper.showInfo("CSV Import Complete", summary.toString());
        } catch (IOException | ServiceException | DataAccessException ex) {
            AlertHelper.showError("CSV Import Error",
                    "Failed to import CSV students (verify no duplicate roll numbers):\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleClearForm() {
        selectedStudent = null;
        studentTable.getSelectionModel().clearSelection();
        rollField.clear();
        nameField.clear();
        departmentField.clear();
        semesterField.clear();
        updateButton.setDisable(true);
        deleteButton.setDisable(true);
    }

    private void loadStudents() {
        try {
            studentTable.setItems(FXCollections.observableArrayList(studentDAO.getAll()));
        } catch (DataAccessException ex) {
            AlertHelper.showError("Database Error", "Unable to load students:\n" + ex.getMessage());
        }
    }

    private void populateForm(Student student) {
        if (student == null) {
            updateButton.setDisable(true);
            deleteButton.setDisable(true);
            return;
        }
        rollField.setText(student.getRollNumber());
        nameField.setText(student.getName());
        departmentField.setText(student.getDepartment());
        semesterField.setText(student.getSemester());
        updateButton.setDisable(false);
        deleteButton.setDisable(false);
    }

    private Student validateForm() {
        String roll = safeTrim(rollField.getText());
        String name = safeTrim(nameField.getText());
        String dept = safeTrim(departmentField.getText());
        String sem = safeTrim(semesterField.getText());

        if (roll.isEmpty() || name.isEmpty() || dept.isEmpty() || sem.isEmpty()) {
            AlertHelper.showWarning("Validation Error",
                    "Roll Number, Name, Department, and Semester are all required.");
            return null;
        }
        return new Student(roll, name, dept, sem);
    }

    private static String safeTrim(String text) {
        return text == null ? "" : text.trim();
    }
}
