package com.seatmap.controller;

import com.seatmap.dao.AllocationDAO;
import com.seatmap.dao.ExamDAO;
import com.seatmap.dao.RoomDAO;
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
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for {@code seating.fxml} rendering non-overlapping VBox room seat cards
 * and exporting PDF charts.
 */
public class SeatingController {

    private record DeptStyle(String bgHex, String borderHex, String textHex) {
    }

    private static final DeptStyle[] PALETTE = {
            new DeptStyle("#DBEAFE", "#3B82F6", "#1E3A8A"),
            new DeptStyle("#D1FAE5", "#10B981", "#065F46"),
            new DeptStyle("#FEF3C7", "#F59E0B", "#92400E"),
            new DeptStyle("#EDE9FE", "#8B5CF6", "#5B21B6"),
            new DeptStyle("#FCE7F3", "#EC4899", "#9D174D"),
            new DeptStyle("#CCFBF1", "#14B8A6", "#115E59"),
            new DeptStyle("#FFEDD5", "#F97316", "#9A3412"),
            new DeptStyle("#E0E7FF", "#6366F1", "#3730A3")
    };

    @FXML
    private ComboBox<Exam> examComboBox;

    @FXML
    private ComboBox<Room> roomComboBox;

    @FXML
    private Label occupancyLabel;

    @FXML
    private FlowPane legendPane;

    @FXML
    private GridPane seatGrid;

    @FXML
    private Label selectedSeatBadge;

    @FXML
    private Label detailRollLabel;

    @FXML
    private Label detailNameLabel;

    @FXML
    private Label detailDeptLabel;

    @FXML
    private Label detailSemesterLabel;

    @FXML
    private Label detailStatusLabel;

    @FXML
    private Button markAttendanceButton;

    private final ExamDAO examDAO = new ExamDAO();
    private final RoomDAO roomDAO = new RoomDAO();
    private final StudentDAO studentDAO = new StudentDAO();
    private final AllocationDAO allocationDAO = new AllocationDAO();
    private final ExportService exportService = new ExportService();

    private VBox selectedCard;
    private String selectedSeatCode;
    private Allocation selectedAllocation;
    private Student selectedStudent;

    @FXML
    private void initialize() {
        configureComboBoxes();
        loadOptionsAndRender();
    }

    /**
     * Selects a specific exam (e.g. when navigating directly from Allocation Engine).
     */
    public void selectExam(Exam targetExam) {
        if (targetExam == null) {
            return;
        }
        for (Exam exam : examComboBox.getItems()) {
            if (exam.getId() == targetExam.getId()) {
                examComboBox.setValue(exam);
                renderGrid();
                return;
            }
        }
    }

    @FXML
    private void handleSelectionChanged() {
        renderGrid();
    }

    @FXML
    private void handleRefresh() {
        Exam previousExam = examComboBox.getValue();
        Room previousRoom = roomComboBox.getValue();
        loadOptionsAndRender();
        if (previousExam != null) {
            selectExam(previousExam);
        }
        if (previousRoom != null) {
            for (Room room : roomComboBox.getItems()) {
                if (room.getId() == previousRoom.getId()) {
                    roomComboBox.setValue(room);
                    break;
                }
            }
        }
        renderGrid();
    }

    @FXML
    private void handleMarkAttendance() {
        if (selectedAllocation == null || selectedStudent == null || selectedSeatCode == null) {
            return;
        }
        AlertHelper.promptAttendanceStatus(selectedSeatCode, selectedAllocation, selectedStudent)
                .ifPresent(newStatus -> {
                    try {
                        if (allocationDAO.updateAttendanceStatus(selectedAllocation.getId(), newStatus)) {
                            selectedAllocation.setStatus(newStatus);
                            detailStatusLabel.setText(newStatus);
                        }
                    } catch (DataAccessException ex) {
                        AlertHelper.showError("Attendance Update Failed",
                                "Could not update attendance status:\n" + ex.getMessage());
                    }
                });
    }

    @FXML
    private void handleExportPdf() {
        Exam exam = examComboBox.getValue();
        Room room = roomComboBox.getValue();
        if (exam == null || room == null) {
            AlertHelper.showWarning("Selection Required", "Please select both an Exam and a Room to export.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Seating Chart PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));
        String safeRoom = room.getRoomNumber().replaceAll("[^a-zA-Z0-9_-]", "_");
        chooser.setInitialFileName("seating-" + safeRoom + ".pdf");

        File targetFile = chooser.showSaveDialog(SceneManager.getPrimaryStage());
        if (targetFile == null) {
            return;
        }

        try {
            exportService.exportRoomSeatingChart(exam.getId(), room.getId(), targetFile.toPath());
            AlertHelper.showInfo("PDF Exported",
                    "Seating chart saved successfully to:\n" + targetFile.getAbsolutePath());
        } catch (ServiceException | DataAccessException ex) {
            AlertHelper.showError("PDF Export Failed", "Unable to export seating chart:\n" + ex.getMessage());
        }
    }

    private void configureComboBoxes() {
        examComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Exam exam) {
                return exam == null ? "" : exam.getSubject() + " (" + exam.getExamDate() + ")";
            }

            @Override
            public Exam fromString(String s) {
                return null;
            }
        });

        roomComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Room room) {
                return room == null ? ""
                        : room.getRoomNumber() + " (" + room.getRows() + "x" + room.getColumns()
                                + ", cap " + room.getCapacity() + ")";
            }

            @Override
            public Room fromString(String s) {
                return null;
            }
        });
    }

    private void loadOptionsAndRender() {
        try {
            List<Exam> exams = examDAO.getAll();
            List<Room> rooms = roomDAO.getAll();

            examComboBox.setItems(FXCollections.observableArrayList(exams));
            roomComboBox.setItems(FXCollections.observableArrayList(rooms));

            if (!exams.isEmpty() && examComboBox.getValue() == null) {
                examComboBox.getSelectionModel().selectFirst();
            }
            if (!rooms.isEmpty() && roomComboBox.getValue() == null) {
                roomComboBox.getSelectionModel().selectFirst();
            }
            renderGrid();
        } catch (DataAccessException ex) {
            AlertHelper.showError("Database Error", "Failed to load exams or rooms:\n" + ex.getMessage());
        }
    }

    private void renderGrid() {
        seatGrid.getChildren().clear();
        legendPane.getChildren().clear();
        clearSeatInspector();

        Exam exam = examComboBox.getValue();
        Room room = roomComboBox.getValue();
        if (exam == null || room == null) {
            occupancyLabel.setText("0 / 0 seats");
            Label hint = new Label("Select an exam and room to inspect the seating chart.");
            hint.getStyleClass().add("muted-label");
            seatGrid.add(hint, 0, 0);
            return;
        }

        try {
            List<Allocation> allocations = allocationDAO.getByExamIdAndRoomId(exam.getId(), room.getId());
            Map<Long, Student> studentsById = new HashMap<>();
            for (Student student : studentDAO.getAll()) {
                studentsById.put(student.getId(), student);
            }

            Allocation[][] allocationGrid = new Allocation[room.getRows()][room.getColumns()];
            Map<String, DeptStyle> deptStyles = new LinkedHashMap<>();

            for (Allocation allocation : allocations) {
                int r = allocation.getSeatRow();
                int c = allocation.getSeatCol();
                if (r >= 0 && r < room.getRows() && c >= 0 && c < room.getColumns()) {
                    allocationGrid[r][c] = allocation;
                    Student student = studentsById.get(allocation.getStudentId());
                    if (student != null) {
                        String deptKey = normalizeDept(student.getDepartment());
                        deptStyles.computeIfAbsent(deptKey,
                                k -> PALETTE[deptStyles.size() % PALETTE.length]);
                    }
                }
            }

            occupancyLabel.setText(allocations.size() + " / " + room.getCapacity() + " seats");
            renderLegend(deptStyles);

            for (int r = 0; r < room.getRows(); r++) {
                for (int c = 0; c < room.getColumns(); c++) {
                    Allocation allocation = allocationGrid[r][c];
                    Student student = allocation == null ? null : studentsById.get(allocation.getStudentId());
                    VBox card = createSeatCard(r, c, allocation, student, deptStyles);
                    seatGrid.add(card, c, r);
                }
            }
        } catch (DataAccessException ex) {
            AlertHelper.showError("Database Error", "Failed to load seating allocations:\n" + ex.getMessage());
        }
    }

    private VBox createSeatCard(int row, int col, Allocation allocation, Student student,
                                Map<String, DeptStyle> deptStyles) {
        String seatCode = "R" + (row + 1) + "-C" + (col + 1);
        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.setMinWidth(120);
        card.setPrefWidth(132);
        card.setMinHeight(85);
        card.setPadding(new Insets(5));

        Label seatLabel = new Label(seatCode);
        seatLabel.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: #475569;");

        if (allocation == null || student == null) {
            card.getStyleClass().add("seat-card-empty");
            Label emptyLabel = new Label("Empty");
            emptyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
            card.getChildren().addAll(seatLabel, emptyLabel);
            card.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    selectEmptySeat(card, seatCode);
                }
            });
            return card;
        }

        DeptStyle style = deptStyles.get(normalizeDept(student.getDepartment()));
        seatLabel.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-text-fill: " + style.textHex() + ";");

        Label rollLabel = new Label(student.getRollNumber());
        rollLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label deptLabel = new Label(student.getDepartment());
        deptLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + style.textHex() + ";");

        card.getStyleClass().add("seat-card");
        card.setCursor(Cursor.HAND);
        card.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s;",
                style.bgHex(), style.borderHex()));
        card.getChildren().addAll(seatLabel, rollLabel, deptLabel);

        ContextMenu contextMenu = new ContextMenu();
        MenuItem removeStudentItem = new MenuItem("Remove Student");
        removeStudentItem.setOnAction(e -> handleRemoveStudent(allocation, student, seatCode));
        contextMenu.getItems().add(removeStudentItem);
        card.setOnContextMenuRequested(e -> contextMenu.show(card, e.getScreenX(), e.getScreenY()));

        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                selectOccupiedSeat(card, seatCode, allocation, student);
            }
        });
        return card;
    }

    private void handleRemoveStudent(Allocation allocation, Student student, String seatCode) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Remove Student");
        confirmAlert.setHeaderText("Remove " + student.getName() + " (" + student.getRollNumber() + ") from seat " + seatCode + "?");
        confirmAlert.setContentText("This will vacate seat " + seatCode + " for this examination. Are you sure?");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                if (allocationDAO.deleteAllocation(allocation.getId())) {
                    if (selectedAllocation != null && selectedAllocation.getId() == allocation.getId()) {
                        clearSeatInspector();
                    }
                    renderGrid();
                    AlertHelper.showInfo("Student Removed",
                            "Removed " + student.getName() + " (" + student.getRollNumber() + ") from seat " + seatCode + ".");
                } else {
                    AlertHelper.showWarning("Delete Failed", "The seat allocation could not be removed.");
                }
            } catch (DataAccessException ex) {
                AlertHelper.showError("Database Error", "Failed to remove student from seat:\n" + ex.getMessage());
            }
        }
    }

    private void renderLegend(Map<String, DeptStyle> deptStyles) {
        for (Map.Entry<String, DeptStyle> entry : deptStyles.entrySet()) {
            DeptStyle style = entry.getValue();
            Label chip = new Label(entry.getKey());
            chip.getStyleClass().add("legend-chip");
            chip.setStyle(String.format(
                    "-fx-background-color: %s; -fx-border-color: %s; -fx-text-fill: %s;",
                    style.bgHex(), style.borderHex(), style.textHex()));
            legendPane.getChildren().add(chip);
        }
    }

    private void selectOccupiedSeat(VBox card, String seatCode, Allocation allocation, Student student) {
        highlightSelectedCard(card);
        this.selectedSeatCode = seatCode;
        this.selectedAllocation = allocation;
        this.selectedStudent = student;

        selectedSeatBadge.setText("Seat " + seatCode + " (Occupied)");
        detailRollLabel.setText(student.getRollNumber());
        detailNameLabel.setText(student.getName());
        detailDeptLabel.setText(student.getDepartment());
        detailSemesterLabel.setText(student.getSemester());
        detailStatusLabel.setText(allocation.getStatus());
        markAttendanceButton.setDisable(false);
    }

    private void selectEmptySeat(VBox card, String seatCode) {
        highlightSelectedCard(card);
        this.selectedSeatCode = seatCode;
        this.selectedAllocation = null;
        this.selectedStudent = null;

        selectedSeatBadge.setText("Seat " + seatCode + " (Unassigned)");
        detailRollLabel.setText("-");
        detailNameLabel.setText("No student assigned");
        detailDeptLabel.setText("-");
        detailSemesterLabel.setText("-");
        detailStatusLabel.setText("-");
        markAttendanceButton.setDisable(true);
    }

    private void highlightSelectedCard(VBox card) {
        if (selectedCard != null) {
            selectedCard.getStyleClass().remove("seat-card-selected");
        }
        selectedCard = card;
        if (!card.getStyleClass().contains("seat-card-selected")) {
            card.getStyleClass().add("seat-card-selected");
        }
    }

    private void clearSeatInspector() {
        selectedCard = null;
        selectedSeatCode = null;
        selectedAllocation = null;
        selectedStudent = null;
        selectedSeatBadge.setText("No seat selected");
        detailRollLabel.setText("-");
        detailNameLabel.setText("-");
        detailDeptLabel.setText("-");
        detailSemesterLabel.setText("-");
        detailStatusLabel.setText("-");
        markAttendanceButton.setDisable(true);
    }

    private static String normalizeDept(String department) {
        return department == null ? "UNKNOWN" : department.trim().toUpperCase(Locale.ROOT);
    }
}
