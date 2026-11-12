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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Controller for {@code allocation.fxml} supporting multi-department, multi-semester
 * granular cohort filtering and seating strategy execution.
 */
public class AllocationController {

    @FXML
    private ComboBox<Exam> examComboBox;

    @FXML
    private ComboBox<String> strategyComboBox;

    @FXML
    private FlowPane departmentCheckPane;

    @FXML
    private FlowPane semesterCheckPane;

    @FXML
    private Label matchingCountLabel;

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

    private final List<CheckBox> departmentCheckBoxes = new ArrayList<>();
    private final List<CheckBox> semesterCheckBoxes = new ArrayList<>();
    private Consumer<Exam> onOpenSeatingChart;

    public void setOnOpenSeatingChart(Consumer<Exam> onOpenSeatingChart) {
        this.onOpenSeatingChart = onOpenSeatingChart;
    }

    @FXML
    private void initialize() {
        configureExamComboBox();
        configureStrategyComboBox();
        configureUnallocatedTable();
        loadOptions();
    }

    @FXML
    private void handleGenerateAllocations() {
        Exam selectedExam = examComboBox.getValue();
        if (selectedExam == null) {
            AlertHelper.showWarning("No Exam Selected", "Please select an exam before generating allocations.");
            return;
        }

        List<String> selectedDepartments = getSelectedDepartments();
        List<Integer> selectedSemesters = getSelectedSemesters();
        String strategy = strategyComboBox.getValue();

        try {
            AllocationResult result = allocationService.runAllocation(
                    selectedExam.getId(), selectedDepartments, selectedSemesters, strategy);
            int seatedCount = result.allocations().size();
            int unallocatedCount = result.unallocatedStudents().size();

            unallocatedTable.setItems(FXCollections.observableArrayList(result.unallocatedStudents()));
            summaryCountLabel.setText(seatedCount + " seated, " + unallocatedCount + " unallocated");

            if (seatedCount == 0 && unallocatedCount == 0) {
                showStatusBanner("No eligible students matched the selected department/semester filters.", false);
                AlertHelper.showWarning("No Students Found",
                        "No students matched the selected department and semester filters.");
            } else if (!result.hasUnallocatedStudents()) {
                showStatusBanner("Allocation complete (" + strategy + "): Seated all "
                        + seatedCount + " matching student(s).", true);
                AlertHelper.showInfo("Allocation Successful",
                        "Seated all " + seatedCount + " matching student(s) using " + strategy + ".");
            } else {
                showStatusBanner("Partial allocation (" + strategy + "): Seated " + seatedCount
                        + " student(s), " + unallocatedCount + " unallocated.", false);
                AlertHelper.showWarning("Partial Allocation",
                        "Seated " + seatedCount + " student(s), but " + unallocatedCount
                                + " student(s) could not be placed. See the table below.");
            }
        } catch (ServiceException | DataAccessException | IllegalArgumentException ex) {
            AlertHelper.showError("Allocation Error", "Failed to generate allocations:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleSelectAllDepartments() {
        setAllChecked(departmentCheckBoxes, true);
    }

    @FXML
    private void handleClearDepartments() {
        setAllChecked(departmentCheckBoxes, false);
    }

    @FXML
    private void handleSelectAllSemesters() {
        setAllChecked(semesterCheckBoxes, true);
    }

    @FXML
    private void handleClearSemesters() {
        setAllChecked(semesterCheckBoxes, false);
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

    private void configureStrategyComboBox() {
        strategyComboBox.setItems(FXCollections.observableArrayList(
                AllocationService.STRATEGY_ANTI_CHEATING_MIX,
                AllocationService.STRATEGY_SEQUENTIAL
        ));
        strategyComboBox.getSelectionModel().selectFirst();
    }

    private void configureUnallocatedTable() {
        unallocRollCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRollNumber()));
        unallocNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        unallocDeptCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDepartment()));
        unallocSemCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSemester()));
    }

    private void loadOptions() {
        try {
            List<Exam> exams = examDAO.getAll();
            examComboBox.setItems(FXCollections.observableArrayList(exams));
            if (!exams.isEmpty()) {
                examComboBox.getSelectionModel().selectFirst();
            }

            Set<String> departments = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            Set<Integer> semesters = new TreeSet<>();
            for (Student student : studentDAO.getAll()) {
                if (student.getDepartment() != null && !student.getDepartment().isBlank()) {
                    departments.add(student.getDepartment().trim().toUpperCase());
                }
                if (student.getSemester() != null && !student.getSemester().isBlank()) {
                    try {
                        semesters.add(Integer.parseInt(student.getSemester().trim()));
                    } catch (NumberFormatException ignored) {
                        // Ignore non-numeric semester labels in numeric filter list.
                    }
                }
            }

            populateDepartmentCheckBoxes(departments);
            populateSemesterCheckBoxes(semesters);
            updateMatchingCountPreview();
        } catch (DataAccessException ex) {
            AlertHelper.showError("Database Error", "Failed to load exams or student filters:\n" + ex.getMessage());
        }
    }

    private void populateDepartmentCheckBoxes(Set<String> departments) {
        departmentCheckPane.getChildren().clear();
        departmentCheckBoxes.clear();
        for (String dept : departments) {
            CheckBox cb = new CheckBox(dept);
            cb.setOnAction(e -> updateMatchingCountPreview());
            departmentCheckBoxes.add(cb);
            departmentCheckPane.getChildren().add(cb);
        }
    }

    private void populateSemesterCheckBoxes(Set<Integer> semesters) {
        semesterCheckPane.getChildren().clear();
        semesterCheckBoxes.clear();
        List<Integer> sorted = new ArrayList<>(semesters);
        sorted.sort(Comparator.naturalOrder());
        for (Integer sem : sorted) {
            CheckBox cb = new CheckBox("Sem " + sem);
            cb.setUserData(sem);
            cb.setOnAction(e -> updateMatchingCountPreview());
            semesterCheckBoxes.add(cb);
            semesterCheckPane.getChildren().add(cb);
        }
    }

    private void setAllChecked(List<CheckBox> boxes, boolean checked) {
        for (CheckBox cb : boxes) {
            cb.setSelected(checked);
        }
        updateMatchingCountPreview();
    }

    private List<String> getSelectedDepartments() {
        return departmentCheckBoxes.stream()
                .filter(CheckBox::isSelected)
                .map(CheckBox::getText)
                .toList();
    }

    private List<Integer> getSelectedSemesters() {
        return semesterCheckBoxes.stream()
                .filter(CheckBox::isSelected)
                .map(cb -> (Integer) cb.getUserData())
                .toList();
    }

    private void updateMatchingCountPreview() {
        try {
            List<String> depts = getSelectedDepartments();
            List<Integer> sems = getSelectedSemesters();
            int count = studentDAO.getFilteredStudents(depts, sems).size();
            String deptText = depts.isEmpty() ? "All Depts" : String.join(", ", depts);
            String semText = sems.isEmpty() ? "All Sems"
                    : "Sem " + sems.stream().map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("");
            matchingCountLabel.setText("Matching students: " + count + " (" + deptText + " | " + semText + ")");
        } catch (DataAccessException ex) {
            matchingCountLabel.setText("Matching students: unavailable");
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
