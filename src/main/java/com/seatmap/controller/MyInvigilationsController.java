package com.seatmap.controller;

import com.seatmap.dao.ExamDAO;
import com.seatmap.dao.RoomAssignmentDAO;
import com.seatmap.dao.RoomDAO;
import com.seatmap.exception.DataAccessException;
import com.seatmap.model.Exam;
import com.seatmap.model.Room;
import com.seatmap.model.RoomAssignment;
import com.seatmap.model.User;
import com.seatmap.util.AlertHelper;
import com.seatmap.util.SceneManager;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Controller for {@code my_invigilations.fxml} showing exam rooms assigned to the logged-in teacher.
 */
public class MyInvigilationsController {

    public record InvigilationRow(RoomAssignment assignment, Exam exam, Room room) {
    }

    @FXML
    private TableView<InvigilationRow> invigilationTable;

    @FXML
    private TableColumn<InvigilationRow, String> examSubjectCol;

    @FXML
    private TableColumn<InvigilationRow, String> examDateCol;

    @FXML
    private TableColumn<InvigilationRow, String> examTimeCol;

    @FXML
    private TableColumn<InvigilationRow, String> roomNumberCol;

    @FXML
    private TableColumn<InvigilationRow, Integer> roomCapacityCol;

    @FXML
    private Button openSeatingButton;

    private final RoomAssignmentDAO assignmentDAO = new RoomAssignmentDAO();
    private final ExamDAO examDAO = new ExamDAO();
    private final RoomDAO roomDAO = new RoomDAO();

    private BiConsumer<Exam, Room> onOpenAttendance;

    public void setOnOpenAttendance(BiConsumer<Exam, Room> onOpenAttendance) {
        this.onOpenAttendance = onOpenAttendance;
    }

    @FXML
    private void initialize() {
        examSubjectCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().exam() != null ? c.getValue().exam().getSubject() : "-"));
        examDateCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().exam() != null && c.getValue().exam().getExamDate() != null
                        ? c.getValue().exam().getExamDate().toString()
                        : "-"));
        examTimeCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().exam() != null && c.getValue().exam().getStartTime() != null
                        ? c.getValue().exam().getStartTime().toString()
                        : "-"));
        roomNumberCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().room() != null ? c.getValue().room().getRoomNumber() : "-"));
        roomCapacityCol.setCellValueFactory(c -> new SimpleIntegerProperty(
                c.getValue().room() != null ? c.getValue().room().getCapacity() : 0).asObject());

        invigilationTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                openSeatingButton.setDisable(newVal == null));

        invigilationTable.setRowFactory(tv -> {
            TableRow<InvigilationRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    handleOpenSeatingChart();
                }
            });
            return row;
        });

        loadAssignments();
    }

    @FXML
    private void loadAssignments() {
        User currentUser = SceneManager.getCurrentUser();
        if (currentUser == null) {
            invigilationTable.setItems(FXCollections.observableArrayList());
            return;
        }

        try {
            List<RoomAssignment> assignments = assignmentDAO.getByTeacherId(currentUser.getId());
            Map<Long, Exam> examsById = new HashMap<>();
            examDAO.getAll().forEach(e -> examsById.put(e.getId(), e));
            Map<Long, Room> roomsById = new HashMap<>();
            roomDAO.getAll().forEach(r -> roomsById.put(r.getId(), r));

            List<InvigilationRow> rows = new ArrayList<>();
            for (RoomAssignment assignment : assignments) {
                Exam exam = examsById.get(assignment.getExamId());
                Room room = roomsById.get(assignment.getRoomId());
                if (exam != null && room != null) {
                    rows.add(new InvigilationRow(assignment, exam, room));
                }
            }
            invigilationTable.setItems(FXCollections.observableArrayList(rows));
        } catch (DataAccessException ex) {
            AlertHelper.showError("Database Error", "Failed to load your invigilations:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleOpenSeatingChart() {
        InvigilationRow selected = invigilationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.showWarning("No Room Selected", "Please select an assigned exam room first.");
            return;
        }
        if (onOpenAttendance != null) {
            onOpenAttendance.accept(selected.exam(), selected.room());
        }
    }
}
