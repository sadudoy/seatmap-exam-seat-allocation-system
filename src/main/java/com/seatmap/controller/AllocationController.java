package com.seatmap.controller;

import com.seatmap.dao.ExamDAO;
import com.seatmap.dao.StudentDAO;
import com.seatmap.exception.DataAccessException;
import com.seatmap.exception.ServiceException;
import com.seatmap.model.Exam;
import com.seatmap.model.Student;
import com.seatmap.service.AllocationService;
import com.seatmap.service.AllocationService.AllocationResult;
import com.seatmap.util.AlertHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Controller for {@code allocation.fxml} executing automated anti-cheating seat allocation.
 */
public class AllocationController {

    @FXML
    private ComboBox<Exam> examComboBox;

    @FXML
    private TextField departmentFilterField;

    @FXML
    private ComboBox<String> semesterComboBox;

    @FXML
    private Label statusLabel;

    @FXML
    private Label summaryCountLabel;

    @FXML
    private TableView<Student> unallocatedTable;

    @FXML
    private TableColumn<Student, String> unallocRollCol;

    @FXML
    private TableColumn<Student, String> unallocNameCol;

    @FXML
    private TableColumn<Student, String> unallocDeptCol;

    @FXML
    private TableColumn<Student, String> unallocSemCol;

    private final ExamDAO examDAO = new ExamDAO();
    private final StudentDAO studentDAO = new StudentDAO();
    private final AllocationService allocationService = new AllocationService();

    private Consumer<Exam> onOpenSeatingChart;

    public void setOnOpenSeatingChart(Consumer<Exam> onOpenSeatingChart) {
        this.onOpenSeatingChart = onOpenSeatingChart;
    }

    @FXML
    private void initialize() {
        configureExamComboBox();
        configureUnallocatedTable();
        loadDropdownOptions();
    }

    @FXML
    private void handleGenerateAllocations() {
        Exam selectedExam = examComboBox.getValue();
        if (selectedExam == null) {
            AlertHelper.showWarning("No Exam Selected", "Please select an exam before generating allocations.");
            return;
        }

        String targetDept = departmentFilterField.getText() == null ? "" : departmentFilterField.getText().trim();
        String selectedSem = semesterComboBox.getEditor().getText() != null
                ? semesterComboBox.getEditor().getText().trim()
                : (semesterComboBox.getValue() == null ? "" : semesterComboBox.getValue().trim());
        String effectiveSem = selectedSem.equalsIgnoreCase("ALL") ? "" : selectedSem;

        try {
            AllocationResult result = allocationService.allocateAndSave(selectedExam, targetDept, effectiveSem);
            int seatedCount = result.allocations().size();
            int unallocatedCount = result.unallocatedStudents().size();

            unallocatedTable.setItems(FXCollections.observableArrayList(result.unallocatedStudents()));
            summaryCountLabel.setText(seatedCount + " seated, " + unallocatedCount + " unallocated");

            if (seatedCount == 0 && unallocatedCount == 0) {
                showStatusBanner("No eligible students matched the selected filters.", false);
                AlertHelper.showWarning("No Students Found",
                        "No students matched the selected department/semester filter.");
            } else if (!result.hasUnallocatedStudents()) {
                showStatusBanner("Allocation complete: All " + seatedCount
                        + " eligible student(s) seated with anti-cheating separation.", true);
                AlertHelper.showInfo("Allocation Successful",
                        "All " + seatedCount + " eligible student(s) were allocated seats successfully.");
            } else {
                showStatusBanner("Partial allocation: Seated " + seatedCount + " student(s), "
                        + unallocatedCount + " student(s) unallocated due to room capacity or anti-cheating constraints.",
                        false);
                AlertHelper.showWarning("Partial Allocation",
                        "Seated " + seatedCount + " student(s), but " + unallocatedCount
                                + " student(s) could not be placed. See the table below.");
            }
        } catch (ServiceException | DataAccessException | IllegalArgumentException ex) {
            AlertHelper.showError("Allocation Error", "Failed to generate allocations:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleOpenSeatingChart() {
        if (onOpenSeatingChart != null) {
            onOpenSeatingChart.accept(examComboBox.getValue());
        }
    }

    private void configureExamComboBox() {
        examComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Exam exam) {
                if (exam == null) {
                    return "";
                }
                return exam.getSubject() + " (" + exam.getExamDate() + " " + exam.getStartTime() + ")";
            }

            @Override
            public Exam fromString(String string) {
                return null;
            }
        });
    }

    private void configureUnallocatedTable() {
        unallocRollCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRollNumber()));
        unallocNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        unallocDeptCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDepartment()));
        unallocSemCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSemester()));
    }

    private void loadDropdownOptions() {
        try {
            List<Exam> exams = examDAO.getAll();
            examComboBox.setItems(FXCollections.observableArrayList(exams));
            if (!exams.isEmpty()) {
                examComboBox.getSelectionModel().selectFirst();
            }

            Set<String> semesters = new LinkedHashSet<>();
            semesters.add("ALL");
            for (Student student : studentDAO.getAll()) {
                if (student.getSemester() != null && !student.getSemester().isBlank()) {
                    semesters.add(student.getSemester().trim());
                }
            }
            semesterComboBox.setItems(FXCollections.observableArrayList(semesters));
            semesterComboBox.getSelectionModel().selectFirst();
        } catch (DataAccessException ex) {
            AlertHelper.showError("Database Error", "Failed to load exams or semesters:\n" + ex.getMessage());
        }
    }

    private void showStatusBanner(String message, boolean success) {
        statusLabel.getStyleClass().removeAll("success-banner", "warning-banner");
        statusLabel.getStyleClass().add(success ? "success-banner" : "warning-banner");
        statusLabel.setText(message);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }
}
