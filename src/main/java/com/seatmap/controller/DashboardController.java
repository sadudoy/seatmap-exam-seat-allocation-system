package com.seatmap.controller;

import com.seatmap.dao.ExamDAO;
import com.seatmap.dao.RoomDAO;
import com.seatmap.dao.StudentDAO;
import com.seatmap.exception.DataAccessException;
import com.seatmap.model.Exam;
import com.seatmap.model.Room;
import com.seatmap.model.User;
import com.seatmap.util.AlertHelper;
import com.seatmap.util.SceneManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Main role-aware workspace shell managing sidebar navigation, RBAC visibility, and logout.
 */
public class DashboardController {

    @FXML
    private Label userBadgeLabel;

    @FXML
    private Button navOverviewBtn;

    @FXML
    private Button navStudentsBtn;

    @FXML
    private Button navRoomsBtn;

    @FXML
    private Button navExamsBtn;

    @FXML
    private Button navAllocationBtn;

    @FXML
    private Button navSeatingBtn;

    @FXML
    private Button navUserManagementBtn;

    @FXML
    private Button navAssignmentsBtn;

    @FXML
    private Button navMyInvigilationsBtn;

    @FXML
    private StackPane contentArea;

    @FXML
    private VBox overviewPane;

    @FXML
    private Label totalStudentsLabel;

    @FXML
    private Label totalRoomsLabel;

    @FXML
    private Label totalExamsLabel;

    @FXML
    private Label totalCapacityLabel;

    @FXML
    private PieChart departmentPieChart;

    @FXML
    private BarChart<String, Number> roomUtilizationChart;

    @FXML
    private TableView<Exam> recentExamsTable;

    @FXML
    private TableColumn<Exam, String> overviewExamSubjectCol;

    @FXML
    private TableColumn<Exam, String> overviewExamDateCol;

    @FXML
    private TableColumn<Exam, String> overviewExamTimeCol;

    private final StudentDAO studentDAO = new StudentDAO();
    private final RoomDAO roomDAO = new RoomDAO();
    private final ExamDAO examDAO = new ExamDAO();

    @FXML
    private void initialize() {
        overviewExamSubjectCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSubject()));
        overviewExamDateCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getExamDate() != null ? c.getValue().getExamDate().toString() : ""));
        overviewExamTimeCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStartTime() != null ? c.getValue().getStartTime().toString() : ""));

        User currentUser = SceneManager.getCurrentUser();
        boolean isTeacher = currentUser != null && currentUser.isTeacher();

        if (currentUser != null) {
            userBadgeLabel.setText(currentUser.getFullName() + "\nRole: " + currentUser.getRole());
        }

        configureNavigationByRole(isTeacher);
        setActiveNavButton(navOverviewBtn);
        refreshOverviewStats();
    }

    @FXML
    private void showOverview() {
        contentArea.getChildren().setAll(overviewPane);
        setActiveNavButton(navOverviewBtn);
        refreshOverviewStats();
    }

    @FXML
    private void showStudents() {
        loadContentView("student.fxml", navStudentsBtn);
    }

    @FXML
    private void showRooms() {
        loadContentView("room.fxml", navRoomsBtn);
    }

    @FXML
    private void showExams() {
        loadContentView("exam.fxml", navExamsBtn);
    }

    @FXML
    private void showAllocation() {
        FXMLLoader loader = loadContentView("allocation.fxml", navAllocationBtn);
        if (loader != null) {
            AllocationController controller = loader.getController();
            controller.setOnOpenSeatingChart(this::openSeatingChartForExam);
        }
    }

    @FXML
    private void showSeatingChart() {
        loadContentView("seating.fxml", navSeatingBtn);
    }

    @FXML
    private void showUserManagement() {
        loadContentView("user_management.fxml", navUserManagementBtn);
    }

    @FXML
    private void showAssignments() {
        loadContentView("assignments.fxml", navAssignmentsBtn);
    }

    @FXML
    private void showMyInvigilations() {
        FXMLLoader loader = loadContentView("my_invigilations.fxml", navMyInvigilationsBtn);
        if (loader != null) {
            MyInvigilationsController controller = loader.getController();
            controller.setOnOpenAttendance(this::openAttendanceForAssignment);
        }
    }

    public void openSeatingChartForExam(Exam exam) {
        FXMLLoader loader = loadContentView("seating.fxml", navSeatingBtn);
        if (loader != null && exam != null) {
            SeatingController controller = loader.getController();
            controller.selectExam(exam);
        }
    }

    public void openAttendanceForAssignment(Exam exam, Room room) {
        FXMLLoader loader = loadContentView("attendance.fxml", navMyInvigilationsBtn);
        if (loader != null) {
            AttendanceController controller = loader.getController();
            controller.loadAssignment(exam, room, this::showMyInvigilations);
        }
    }

    @FXML
    private void refreshOverviewStats() {
        try {
            int studentCount = studentDAO.getAll().size();
            List<Room> rooms = roomDAO.getAll();
            List<Exam> exams = examDAO.getAll();
            int totalCapacity = rooms.stream().mapToInt(Room::getCapacity).sum();

            totalStudentsLabel.setText(String.valueOf(studentCount));
            totalRoomsLabel.setText(String.valueOf(rooms.size()));
            totalExamsLabel.setText(String.valueOf(exams.size()));
            totalCapacityLabel.setText(String.valueOf(totalCapacity));
            recentExamsTable.setItems(FXCollections.observableArrayList(exams));

            // Populate Students by Department PieChart
            Map<String, Integer> deptCounts = studentDAO.getDepartmentCounts();
            ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
            for (Map.Entry<String, Integer> entry : deptCounts.entrySet()) {
                pieData.add(new PieChart.Data(entry.getKey() + " (" + entry.getValue() + ")", entry.getValue()));
            }
            if (departmentPieChart != null) {
                departmentPieChart.setData(pieData);
            }

            // Populate Room Utilization BarChart (Seats Allocated vs Total Capacity)
            Map<String, Integer[]> roomStats = roomDAO.getRoomCapacityStats();
            XYChart.Series<String, Number> allocatedSeries = new XYChart.Series<>();
            allocatedSeries.setName("Seats Allocated");

            XYChart.Series<String, Number> capacitySeries = new XYChart.Series<>();
            capacitySeries.setName("Total Capacity");

            for (Map.Entry<String, Integer[]> entry : roomStats.entrySet()) {
                String roomName = entry.getKey();
                Integer[] stats = entry.getValue();
                int capacity = stats[RoomDAO.INDEX_TOTAL_CAPACITY];
                int allocated = stats[RoomDAO.INDEX_ALLOCATED_SEATS];
                allocatedSeries.getData().add(new XYChart.Data<>(roomName, allocated));
                capacitySeries.getData().add(new XYChart.Data<>(roomName, capacity));
            }

            if (roomUtilizationChart != null) {
                roomUtilizationChart.getData().setAll(List.of(allocatedSeries, capacitySeries));
            }
        } catch (DataAccessException ex) {
            AlertHelper.showError("Database Error", "Failed to load dashboard statistics:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleLogout() {
        SceneManager.setCurrentUser(null);
        SceneManager.showLoginScreen();
    }

    private void configureNavigationByRole(boolean isTeacher) {
        setButtonVisibility(navOverviewBtn, true);
        setButtonVisibility(navStudentsBtn, true);
        setButtonVisibility(navRoomsBtn, true);
        setButtonVisibility(navExamsBtn, true);
        setButtonVisibility(navAllocationBtn, true);
        setButtonVisibility(navSeatingBtn, true);

        setButtonVisibility(navUserManagementBtn, !isTeacher);
        setButtonVisibility(navAssignmentsBtn, !isTeacher);
        setButtonVisibility(navMyInvigilationsBtn, isTeacher);
    }

    private static void setButtonVisibility(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
    }

    private FXMLLoader loadContentView(String fxmlName, Button navButton) {
        try {
            FXMLLoader loader = SceneManager.createLoader(fxmlName);
            Parent view = loader.load();
            contentArea.getChildren().setAll(view);
            setActiveNavButton(navButton);
            return loader;
        } catch (IOException ex) {
            AlertHelper.showError("Navigation Error", "Could not load screen " + fxmlName + ":\n" + ex.getMessage());
            return null;
        }
    }

    private void setActiveNavButton(Button activeButton) {
        List<Button> navButtons = List.of(
                navOverviewBtn, navStudentsBtn, navRoomsBtn, navExamsBtn,
                navAllocationBtn, navSeatingBtn, navUserManagementBtn,
                navAssignmentsBtn, navMyInvigilationsBtn
        );
        for (Button button : navButtons) {
            button.getStyleClass().remove("nav-button-active");
        }
        if (activeButton != null && !activeButton.getStyleClass().contains("nav-button-active")) {
            activeButton.getStyleClass().add("nav-button-active");
        }
    }
}
