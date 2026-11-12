package com.seatmap.util;

import com.seatmap.model.Allocation;
import com.seatmap.model.Student;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.Optional;

/**
 * Shared JavaFX dialog helpers for consistent user feedback across controllers.
 */
public final class AlertHelper {

    private AlertHelper() {
    }

    public static void showInfo(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    public static void showWarning(String title, String message) {
        showAlert(Alert.AlertType.WARNING, title, message);
    }

    public static void showError(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, message);
    }

    public static boolean showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Prompts the user to mark an occupied seat's attendance status.
     *
     * @return chosen status ({@code PRESENT}, {@code ABSENT}, {@code PENDING}), or empty if cancelled
     */
    public static Optional<String> promptAttendanceStatus(String seatCode, Allocation allocation, Student student) {
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Mark Attendance - Seat " + seatCode);
        dialog.setHeaderText(student.getName() + " (" + student.getRollNumber() + ")");
        dialog.setContentText("Department: " + student.getDepartment()
                + " | Semester: " + student.getSemester()
                + "\nCurrent Status: " + allocation.getStatus()
                + "\n\nSelect attendance status:");

        ButtonType markPresent = new ButtonType("Mark Present");
        ButtonType markAbsent = new ButtonType("Mark Absent");
        ButtonType markPending = new ButtonType("Set Pending");
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getButtonTypes().setAll(markPresent, markAbsent, markPending, cancel);

        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isEmpty() || choice.get() == cancel) {
            return Optional.empty();
        }
        if (choice.get() == markPresent) {
            return Optional.of(Allocation.STATUS_PRESENT);
        }
        if (choice.get() == markAbsent) {
            return Optional.of(Allocation.STATUS_ABSENT);
        }
        return Optional.of(Allocation.STATUS_PENDING);
    }

    private static void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
