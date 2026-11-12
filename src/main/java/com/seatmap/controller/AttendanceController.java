package com.seatmap.controller;

import com.seatmap.dao.AllocationDAO;
import com.seatmap.dao.StudentDAO;
import com.seatmap.exception.DataAccessException;
import com.seatmap.exception.ServiceException;
import com.seatmap.model.Allocation;
import com.seatmap.model.Exam;
import com.seatmap.model.Room;
import com.seatmap.model.Student;
import com.seatmap.service.ExportService;
import com.seatmap.util.AlertHelper;
import com.seatmap.util.SceneManager;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for {@code attendance.fxml} rendering non-overlapping VBox seat cards
 * with Amber/Green/Red attendance status contrast and PDF attendance sheet export.
 */
public class AttendanceController {

    @FXML
    private Label headerTitleLabel;

    @FXML
    private Label headerSubtitleLabel;

    @FXML
    private Label summaryStatsLabel;

    @FXML
    private GridPane seatGrid;

    private final AllocationDAO allocationDAO = new AllocationDAO();
    private final StudentDAO studentDAO = new StudentDAO();
    private final ExportService exportService = new ExportService();

    private Exam currentExam;
    private Room currentRoom;
    private Runnable onBack;

    public void loadAssignment(Exam exam, Room room, Runnable onBack) {
        this.currentExam = exam;
        this.currentRoom = room;
        this.onBack = onBack;

        if (exam != null && room != null) {
            headerTitleLabel.setText(room.getRoomNumber() + " - " + exam.getSubject());
            headerSubtitleLabel.setText("Exam Date: " + exam.getExamDate() + " at " + exam.getStartTime()
                    + " | Click any occupied seat card to mark Present or Absent.");
            renderAttendanceGrid();
        }
    }

    @FXML
    private void handleRefresh() {
        renderAttendanceGrid();
    }

    @FXML
    private void handleBack() {
        if (onBack != null) {
            onBack.run();
        }
    }

    @FXML
    private void handleExportAttendancePdf() {
        if (currentExam == null || currentRoom == null) {
            AlertHelper.showWarning("No Room Loaded", "Open an assigned exam room first.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Teacher Attendance Sheet PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));
        String safeRoom = currentRoom.getRoomNumber().replaceAll("[^a-zA-Z0-9_-]", "_");
        chooser.setInitialFileName("attendance-" + safeRoom + ".pdf");

        File targetFile = chooser.showSaveDialog(SceneManager.getPrimaryStage());
        if (targetFile == null) {
            return;
        }

        try {
            exportService.exportAttendanceSheet(currentExam.getId(), currentRoom.getId(), targetFile.toPath());
            AlertHelper.showInfo("Attendance PDF Exported",
                    "Teacher Attendance Sheet saved successfully to:\n" + targetFile.getAbsolutePath());
        } catch (ServiceException | DataAccessException ex) {
            AlertHelper.showError("PDF Export Failed",
                    "Unable to generate Attendance PDF:\n" + ex.getMessage());
        }
    }

    private void renderAttendanceGrid() {
        seatGrid.getChildren().clear();
        if (currentExam == null || currentRoom == null) {
            return;
        }

        try {
            List<Allocation> allocations = allocationDAO.getByExamIdAndRoomId(
                    currentExam.getId(), currentRoom.getId());

            Map<Long, Student> studentsById = new HashMap<>();
            for (Student student : studentDAO.getAll()) {
                studentsById.put(student.getId(), student);
            }

            Allocation[][] allocationGrid = new Allocation[currentRoom.getRows()][currentRoom.getColumns()];
            long presentCount = 0;
            long absentCount = 0;
            long pendingCount = 0;

            for (Allocation allocation : allocations) {
                int r = allocation.getSeatRow();
                int c = allocation.getSeatCol();
                if (r >= 0 && r < currentRoom.getRows() && c >= 0 && c < currentRoom.getColumns()) {
                    allocationGrid[r][c] = allocation;
                }
                if (Allocation.STATUS_PRESENT.equalsIgnoreCase(allocation.getStatus())) {
                    presentCount++;
                } else if (Allocation.STATUS_ABSENT.equalsIgnoreCase(allocation.getStatus())) {
                    absentCount++;
                } else {
                    pendingCount++;
                }
            }

            summaryStatsLabel.setText(String.format(
                    "Seated: %d  |  Present: %d  |  Absent: %d  |  Pending: %d",
                    allocations.size(), presentCount, absentCount, pendingCount));

            for (int r = 0; r < currentRoom.getRows(); r++) {
                for (int c = 0; c < currentRoom.getColumns(); c++) {
                    Allocation allocation = allocationGrid[r][c];
                    Student student = allocation == null ? null : studentsById.get(allocation.getStudentId());
                    VBox seatCard = createAttendanceSeatCard(r, c, allocation, student);
                    seatGrid.add(seatCard, c, r);
                }
            }
        } catch (DataAccessException ex) {
            AlertHelper.showError("Database Error", "Failed to load attendance data:\n" + ex.getMessage());
        }
    }

    private VBox createAttendanceSeatCard(int row, int col, Allocation allocation, Student student) {
        String seatCode = "R" + (row + 1) + "-C" + (col + 1);

        VBox seatCard = new VBox(4);
        seatCard.setAlignment(Pos.CENTER);
        seatCard.setMinWidth(120);
        seatCard.setPrefWidth(132);
        seatCard.setMinHeight(85);
        seatCard.setPadding(new Insets(5));

        Label seatLabel = new Label(seatCode);
        seatLabel.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #475569;");

        if (allocation == null || student == null) {
            seatCard.getStyleClass().add("seat-card-empty");
            Label emptyLabel = new Label("Empty");
            emptyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
            seatCard.getChildren().addAll(seatLabel, emptyLabel);
            return seatCard;
        }

        String status = allocation.getStatus() == null || allocation.getStatus().isBlank()
                ? Allocation.STATUS_PENDING
                : allocation.getStatus().toUpperCase();

        seatCard.getStyleClass().addAll("seat-card", resolveStatusClass(status));
        seatCard.setCursor(Cursor.HAND);

        Label rollLabel = new Label(student.getRollNumber());
        rollLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label statusLabel = new Label(status);
        statusLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: "
                + resolveStatusTextColor(status) + ";");

        seatCard.getChildren().addAll(seatLabel, rollLabel, statusLabel);
        seatCard.setOnMouseClicked(e -> handleSeatClick(seatCode, allocation, student));
        return seatCard;
    }

    private void handleSeatClick(String seatCode, Allocation allocation, Student student) {
        AlertHelper.promptAttendanceStatus(seatCode, allocation, student).ifPresent(newStatus -> {
            try {
                if (allocationDAO.updateAttendanceStatus(allocation.getId(), newStatus)) {
                    renderAttendanceGrid();
                }
            } catch (DataAccessException ex) {
                AlertHelper.showError("Attendance Update Failed",
                        "Could not update attendance status:\n" + ex.getMessage());
            }
        });
    }

    private static String resolveStatusClass(String status) {
        if (Allocation.STATUS_PRESENT.equalsIgnoreCase(status)) {
            return "seat-present";
        }
        if (Allocation.STATUS_ABSENT.equalsIgnoreCase(status)) {
            return "seat-absent";
        }
        return "seat-pending";
    }

    private static String resolveStatusTextColor(String status) {
        if (Allocation.STATUS_PRESENT.equalsIgnoreCase(status)) {
            return "#15803D";
        }
        if (Allocation.STATUS_ABSENT.equalsIgnoreCase(status)) {
            return "#B91C1C";
        }
        return "#B45309";
    }
}
