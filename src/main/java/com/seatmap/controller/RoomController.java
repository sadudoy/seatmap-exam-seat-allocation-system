package com.seatmap.controller;

import com.seatmap.dao.RoomDAO;
import com.seatmap.exception.DataAccessException;
import com.seatmap.model.Room;
import com.seatmap.util.AlertHelper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/**
 * Controller for {@code room.fxml} managing examination rooms and grid dimensions.
 */
public class RoomController {

    @FXML
    private TableView<Room> roomTable;

    @FXML
    private TableColumn<Room, String> roomNumberCol;

    @FXML
    private TableColumn<Room, Integer> rowsCol;

    @FXML
    private TableColumn<Room, Integer> columnsCol;

    @FXML
    private TableColumn<Room, Integer> capacityCol;

    @FXML
    private TextField roomNumberField;

    @FXML
    private TextField rowsField;

    @FXML
    private TextField columnsField;

    @FXML
    private TextField capacityField;

    @FXML
    private Button updateButton;

    @FXML
    private Button deleteButton;

    private final RoomDAO roomDAO = new RoomDAO();
    private Room selectedRoom;

    @FXML
    private void initialize() {
        roomNumberCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRoomNumber()));
        rowsCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getRows()).asObject());
        columnsCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getColumns()).asObject());
        capacityCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getCapacity()).asObject());

        roomTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            selectedRoom = newVal;
            populateForm(newVal);
        });

        loadRooms();
    }

    @FXML
    private void handleAdd() {
        Room candidate = validateForm();
        if (candidate == null) {
            return;
        }
        try {
            roomDAO.add(candidate);
            loadRooms();
            handleClearForm();
            AlertHelper.showInfo("Room Added", "Room added successfully.");
        } catch (DataAccessException ex) {
            AlertHelper.showError("Add Room Failed",
                    "Could not add room (check that room number is unique):\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleUpdate() {
        if (selectedRoom == null) {
            AlertHelper.showWarning("No Selection", "Please select a room to update.");
            return;
        }
        Room updated = validateForm();
        if (updated == null) {
            return;
        }
        updated.setId(selectedRoom.getId());

        try {
            if (roomDAO.update(updated)) {
                loadRooms();
                handleClearForm();
                AlertHelper.showInfo("Room Updated", "Room updated successfully.");
            }
        } catch (DataAccessException ex) {
            AlertHelper.showError("Update Room Failed", "Could not update room:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        if (selectedRoom == null) {
            return;
        }
        boolean confirmed = AlertHelper.showConfirmation(
                "Confirm Delete",
                "Are you sure you want to delete room " + selectedRoom.getRoomNumber() + "?");
        if (!confirmed) {
            return;
        }
        try {
            if (roomDAO.delete(selectedRoom.getId())) {
                loadRooms();
                handleClearForm();
                AlertHelper.showInfo("Room Deleted", "Room deleted successfully.");
            }
        } catch (DataAccessException ex) {
            AlertHelper.showError("Delete Failed", "Could not delete room:\n" + ex.getMessage());
        }
    }

    @FXML
    private void handleClearForm() {
        selectedRoom = null;
        roomTable.getSelectionModel().clearSelection();
        roomNumberField.clear();
        rowsField.clear();
        columnsField.clear();
        capacityField.clear();
        updateButton.setDisable(true);
        deleteButton.setDisable(true);
    }

    private void loadRooms() {
        try {
            roomTable.setItems(FXCollections.observableArrayList(roomDAO.getAll()));
        } catch (DataAccessException ex) {
            AlertHelper.showError("Database Error", "Failed to load rooms:\n" + ex.getMessage());
        }
    }

    private void populateForm(Room room) {
        if (room == null) {
            updateButton.setDisable(true);
            deleteButton.setDisable(true);
            return;
        }
        roomNumberField.setText(room.getRoomNumber());
        rowsField.setText(String.valueOf(room.getRows()));
        columnsField.setText(String.valueOf(room.getColumns()));
        capacityField.setText(String.valueOf(room.getCapacity()));
        updateButton.setDisable(false);
        deleteButton.setDisable(false);
    }

    private Room validateForm() {
        String roomNumber = roomNumberField.getText() == null ? "" : roomNumberField.getText().trim();
        if (roomNumber.isEmpty()) {
            AlertHelper.showWarning("Validation Error", "Room number is required.");
            return null;
        }

        int rows;
        int cols;
        int capacity;
        try {
            rows = Integer.parseInt(rowsField.getText().trim());
            cols = Integer.parseInt(columnsField.getText().trim());
            capacity = Integer.parseInt(capacityField.getText().trim());
        } catch (NumberFormatException | NullPointerException ex) {
            AlertHelper.showWarning("Validation Error", "Rows, Columns, and Capacity must be valid whole numbers.");
            return null;
        }

        if (rows <= 0 || cols <= 0 || capacity <= 0) {
            AlertHelper.showWarning("Validation Error", "Rows, Columns, and Capacity must all be greater than 0.");
            return null;
        }
        if (capacity > rows * cols) {
            AlertHelper.showWarning("Validation Error",
                    "Capacity (" + capacity + ") cannot exceed rows x columns (" + (rows * cols) + ").");
            return null;
        }
        return new Room(roomNumber, capacity, rows, cols);
    }
}
