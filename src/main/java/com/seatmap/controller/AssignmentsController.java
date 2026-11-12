package com.seatmap.controller;

import com.seatmap.dao.ExamDAO;
import com.seatmap.dao.RoomAssignmentDAO;
import com.seatmap.dao.RoomDAO;
import com.seatmap.dao.UserDAO;
import com.seatmap.exception.DataAccessException;
import com.seatmap.model.Exam;
import com.seatmap.model.Room;
import com.seatmap.model.RoomAssignment;
import com.seatmap.model.User;
import com.seatmap.util.AlertHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for {@code assignments.fxml} enabling Master Admins to assign teachers to exam rooms.
 */
public class AssignmentsController {

    public record AssignmentRow(RoomAssignment assignment, Exam exam, Room room, User teacher) {
    }

    @FXML
    private TableView<AssignmentRow> assignmentTable;

    @FXML
    private TableColumn<AssignmentRow, String> examCol;

    @FXML
    private TableColumn<AssignmentRow, String> dateCol;

    @FXML
    private TableColumn<AssignmentRow, String> roomCol;

    @FXML
    private TableColumn<AssignmentRow, String> teacherCol;

    @FXML
    private ComboBox<Exam> examComboBox;

    @FXML
    private ComboBox<Room> roomComboBox;

    @FXML
    private ComboBox<User> teacherComboBox;

    @FXML
    private Button removeButton;

    private final ExamDAO examDAO = new ExamDAO();
    private final RoomDAO roomDAO = new RoomDAO();
    private final UserDAO userDAO = new UserDAO();
    private final RoomAssignmentDAO assignmentDAO = new RoomAssignmentDAO();

    @FXML
    private void initialize() {
        configureConverters();
        configureTable();

        assignmentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                removeButton.setDisable(newVal == null));

        loadData();
    }

    @FXML
    private void handleAssignInvigilator() {
        Exam exam = examComboBox.getValue();
        Room room = roomComboBox.getValue();
        User teacher = teacherComboBox.getValue();

        if (exam == null || room == null || teacher == null) {
            AlertHelper.showWarning("Selection Required",
                    "Please select an Exam, a Room, and a Teacher.");
            return;
        }

        try {
            if (assignmentDAO.exists(exam.getId(), room.getId(), teacher.getId())) {
                AlertHelper.showWarning("Already Assigned",
                        teacher.getFullName() + " is already assigned to " + room.getRoomNumber()
                                + " for this exam.");
                return;
            }
            assignmentDAO.assign(exam.getId(), room.getId(), teacher.getId());
            loadData();
            AlertHelper.showInfo("Invigilator Assigned",
                    "Assigned " + teacher.getFullName() + " to " + room.getRoomNumber()
                            + " (" + exam.getSubject() + ").");
        } catch (DataAccessException ex) {
            AlertHelper.showError("Assignment Failed", "Could not save assignment:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleRemoveAssignment() {
        AssignmentRow selected = assignmentTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        boolean confirmed = AlertHelper.showConfirmation(
                "Confirm Removal",
                "Remove invigilation assignment for " + selected.teacher().getFullName() + "?");
        if (!confirmed) {
            return;
        }
        try {
            if (assignmentDAO.deleteAssignment(selected.assignment().getId())) {
                loadData();
                AlertHelper.showInfo("Assignment Removed", "Invigilation assignment removed.");
            }
        } catch (DataAccessException ex) {
            AlertHelper.showError("Remove Failed", "Could not remove assignment:\n" + ex.getMessage());
        }
    }

    private void configureConverters() {
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
                return room == null ? "" : room.getRoomNumber() + " (cap " + room.getCapacity() + ")";
            }

            @Override
            public Room fromString(String s) {
                return null;
            }
        });

        teacherComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(User user) {
                return user == null ? "" : user.getFullName() + " (" + user.getUsername() + ")";
            }

            @Override
            public User fromString(String s) {
                return null;
            }
        });
    }

    private void configureTable() {
        examCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().exam() != null ? c.getValue().exam().getSubject() : "-"));
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().exam() != null && c.getValue().exam().getExamDate() != null
                        ? c.getValue().exam().getExamDate().toString()
                        : "-"));
        roomCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().room() != null ? c.getValue().room().getRoomNumber() : "-"));
        teacherCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().teacher() != null
                        ? c.getValue().teacher().getFullName() + " (" + c.getValue().teacher().getUsername() + ")"
                        : "-"));
    }

    private void loadData() {
        try {
            List<Exam> exams = examDAO.getAll();
            List<Room> rooms = roomDAO.getAll();
            List<User> teachers = userDAO.getAllTeachers();
            List<RoomAssignment> assignments = assignmentDAO.getAll();

            examComboBox.setItems(FXCollections.observableArrayList(exams));
            roomComboBox.setItems(FXCollections.observableArrayList(rooms));
            teacherComboBox.setItems(FXCollections.observableArrayList(teachers));

            if (!exams.isEmpty() && examComboBox.getValue() == null) {
                examComboBox.getSelectionModel().selectFirst();
            }
            if (!rooms.isEmpty() && roomComboBox.getValue() == null) {
                roomComboBox.getSelectionModel().selectFirst();
            }
            if (!teachers.isEmpty() && teacherComboBox.getValue() == null) {
                teacherComboBox.getSelectionModel().selectFirst();
            }

            Map<Long, Exam> examsById = new HashMap<>();
            exams.forEach(e -> examsById.put(e.getId(), e));
            Map<Long, Room> roomsById = new HashMap<>();
            rooms.forEach(r -> roomsById.put(r.getId(), r));
            Map<Long, User> teachersById = new HashMap<>();
            teachers.forEach(t -> teachersById.put(t.getId(), t));

            List<AssignmentRow> rows = new ArrayList<>();
            for (RoomAssignment assignment : assignments) {
                rows.add(new AssignmentRow(
                        assignment,
                        examsById.get(assignment.getExamId()),
                        roomsById.get(assignment.getRoomId()),
                        teachersById.get(assignment.getTeacherId())
                ));
            }
            assignmentTable.setItems(FXCollections.observableArrayList(rows));
        } catch (DataAccessException ex) {
            AlertHelper.showError("Database Error", "Failed to load assignments:\n" + ex.getMessage());
        }
    }
}
