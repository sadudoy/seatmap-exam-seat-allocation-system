package com.seatmap.controller;

import com.seatmap.dao.ExamDAO;
import com.seatmap.dao.RoomDAO;
import com.seatmap.dao.StudentDAO;
import com.seatmap.exception.DataAccessException;
import com.seatmap.model.Admin;
import com.seatmap.model.Exam;
import com.seatmap.model.Room;
import com.seatmap.util.AlertHelper;
import com.seatmap.util.SceneManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.List;

/**
 * Main workspace shell managing sidebar navigation, session logout, and live overview statistics.
 */
public class DashboardController {

    @FXML
    private Label adminBadgeLabel;

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
        Admin admin = SceneManager.getCurrentAdmin();
        if (admin != null) {
            adminBadgeLabel.setText("Signed in as: " + admin.getUsername());
        }

        overviewExamSubjectCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSubject()));
        overviewExamDateCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getExamDate() != null ? c.getValue().getExamDate().toString() : ""));
        overviewExamTimeCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStartTime() != null ? c.getValue().getStartTime().toString() : ""));

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

    public void openSeatingChartForExam(Exam exam) {
        FXMLLoader loader = loadContentView("seating.fxml", navSeatingBtn);
        if (loader != null && exam != null) {
            SeatingController controller = loader.getController();
            controller.selectExam(exam);
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
        } catch (DataAccessException ex) {
            AlertHelper.showError("Database Error", "Failed to load dashboard statistics:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleLogout() {
        SceneManager.setCurrentAdmin(null);
        SceneManager.showLoginScreen();
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
                navOverviewBtn, navStudentsBtn, navRoomsBtn, navExamsBtn, navAllocationBtn, navSeatingBtn);
        for (Button button : navButtons) {
            button.getStyleClass().remove("nav-button-active");
        }
        if (activeButton != null && !activeButton.getStyleClass().contains("nav-button-active")) {
            activeButton.getStyleClass().add("nav-button-active");
        }
    }
}
