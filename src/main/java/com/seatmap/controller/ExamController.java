package com.seatmap.controller;

import com.seatmap.dao.ExamDAO;
import com.seatmap.exception.DataAccessException;
import com.seatmap.model.Exam;
import com.seatmap.util.AlertHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Controller for {@code exam.fxml} managing scheduled examinations.
 */
public class ExamController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

    @FXML
    private TableView<Exam> examTable;

    @FXML
    private TableColumn<Exam, String> subjectCol;

    @FXML
    private TableColumn<Exam, String> dateCol;

    @FXML
    private TableColumn<Exam, String> timeCol;

    @FXML
    private TextField subjectField;

    @FXML
    private DatePicker examDatePicker;

    @FXML
    private TextField startTimeField;

    @FXML
    private Button updateButton;

    @FXML
    private Button deleteButton;

    private final ExamDAO examDAO = new ExamDAO();
    private Exam selectedExam;

    @FXML
    private void initialize() {
        subjectCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSubject()));
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getExamDate() != null ? c.getValue().getExamDate().toString() : ""));
        timeCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStartTime() != null ? c.getValue().getStartTime().toString() : ""));

        examTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedExam = newVal;
            populateForm(newVal);
        });

        loadExams();
    }

    @FXML
    private void handleAdd() {
        Exam candidate = validateForm();
        if (candidate == null) {
            return;
        }
        try {
            examDAO.add(candidate);
            loadExams();
            handleClearForm();
            AlertHelper.showInfo("Exam Created", "Exam scheduled successfully.");
        } catch (DataAccessException ex) {
            AlertHelper.showError("Create Exam Failed", "Could not create exam:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleUpdate() {
        if (selectedExam == null) {
            AlertHelper.showWarning("No Selection", "Please select an exam to update.");
            return;
        }
        Exam updated = validateForm();
        if (updated == null) {
            return;
        }
        updated.setId(selectedExam.getId());

        try {
            if (examDAO.update(updated)) {
                loadExams();
                handleClearForm();
                AlertHelper.showInfo("Exam Updated", "Exam updated successfully.");
            }
        } catch (DataAccessException ex) {
            AlertHelper.showError("Update Exam Failed", "Could not update exam:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        if (selectedExam == null) {
            return;
        }
        boolean confirmed = AlertHelper.showConfirmation(
                "Confirm Delete",
                "Are you sure you want to delete exam \"" + selectedExam.getSubject()
                        + "\"? Any generated seat allocations for this exam will also be removed.");
        if (!confirmed) {
            return;
        }
        try {
            if (examDAO.delete(selectedExam.getId())) {
                loadExams();
                handleClearForm();
                AlertHelper.showInfo("Exam Deleted", "Exam deleted successfully.");
            }
        } catch (DataAccessException ex) {
            AlertHelper.showError("Delete Failed", "Could not delete exam:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleClearForm() {
        selectedExam = null;
        examTable.getSelectionModel().clearSelection();
        subjectField.clear();
        examDatePicker.setValue(null);
        startTimeField.clear();
        updateButton.setDisable(true);
        deleteButton.setDisable(true);
    }

    private void loadExams() {
        try {
            examTable.setItems(FXCollections.observableArrayList(examDAO.getAll()));
        } catch (DataAccessException ex) {
            AlertHelper.showError("Database Error", "Failed to load exams:\n" + ex.getMessage());
        }
    }

    private void populateForm(Exam exam) {
        if (exam == null) {
            updateButton.setDisable(true);
            deleteButton.setDisable(true);
            return;
        }
        subjectField.setText(exam.getSubject());
        examDatePicker.setValue(exam.getExamDate());
        startTimeField.setText(exam.getStartTime() != null ? exam.getStartTime().toString() : "");
        updateButton.setDisable(false);
        deleteButton.setDisable(false);
    }

    private Exam validateForm() {
        String subject = subjectField.getText() == null ? "" : subjectField.getText().trim();
        LocalDate date = examDatePicker.getValue();
        String timeText = startTimeField.getText() == null ? "" : startTimeField.getText().trim();

        if (subject.isEmpty() || date == null || timeText.isEmpty()) {
            AlertHelper.showWarning("Validation Error", "Subject, Exam Date, and Start Time are all required.");
            return null;
        }

        LocalTime startTime;
        try {
            startTime = LocalTime.parse(timeText, TIME_FORMAT);
        } catch (DateTimeParseException ex) {
            AlertHelper.showWarning("Invalid Time Format", "Please enter Start Time in 24-hour HH:mm format (e.g. 09:30 or 14:00).");
            return null;
        }

        return new Exam(subject, date, startTime);
    }
}
