package com.seatmap.ui;

import com.seatmap.config.DatabaseConnector;
import com.seatmap.dao.AllocationDAO;
import com.seatmap.dao.ExamDAO;
import com.seatmap.dao.RoomAssignmentDAO;
import com.seatmap.dao.RoomDAO;
import com.seatmap.dao.StudentDAO;
import com.seatmap.dao.UserDAO;
import com.seatmap.model.Exam;
import com.seatmap.model.Room;
import com.seatmap.model.User;
import com.seatmap.service.AuthService;
import com.seatmap.util.DataSeeder;
import com.seatmap.util.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JavaFX RBAC, Attendance, Seeding & FXML Smoke Tests")
class UiSmokeTest {

    @BeforeAll
    static void initToolkitAndDb() throws SQLException, InterruptedException {
        DatabaseConnector.initializeDatabase();

        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX Platform failed to initialize");
    }

    @AfterAll
    static void tearDown() {
        SceneManager.setCurrentUser(null);
        DatabaseConnector.close();
    }

    @Test
    @DisplayName("seeds admin, teacher1, 1 exam, 3 rooms, 300 students, and Room 1 assignment")
    void seedsRbacDemoDataIdempotently() {
        DataSeeder.seedIfEmpty();
        DataSeeder.seedIfEmpty();

        AuthService authService = new AuthService(new UserDAO());
        User admin = authService.login("admin", "admin123").orElseThrow();
        assertTrue(admin.isMasterAdmin());

        User teacher1 = authService.login("teacher1", "teacher123").orElseThrow();
        assertTrue(teacher1.isTeacher());

        List<Exam> exams = new ExamDAO().getAll();
        List<Room> rooms = new RoomDAO().getAll();
        int studentCount = new StudentDAO().getAll().size();

        assertFalse(exams.isEmpty(), "Expected at least 1 seeded exam");
        assertTrue(rooms.size() >= 3, "Expected at least 3 seeded rooms");
        assertTrue(studentCount >= 300, "Expected at least 300 seeded students");

        RoomAssignmentDAO assignmentDAO = new RoomAssignmentDAO();
        assertFalse(assignmentDAO.getByTeacherId(teacher1.getId()).isEmpty(),
                "Expected teacher1 to be assigned to Room 1");
        assertFalse(new AllocationDAO().getByExamId(exams.get(0).getId()).isEmpty(),
                "Expected seeded exam to have initial seat allocations");
    }

    @Test
    @DisplayName("enforces RBAC sidebar visibility for MASTER_ADMIN vs TEACHER on single maximized Stage")
    void enforcesRbacNavigationAndMaximizedSingleWindow() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            Stage stage = null;
            try {
                DataSeeder.seedIfEmpty();
                AuthService authService = new AuthService(new UserDAO());
                User admin = authService.login("admin", "admin123").orElseThrow();
                User teacher1 = authService.login("teacher1", "teacher123").orElseThrow();

                stage = new Stage();
                try (InputStream iconStream = UiSmokeTest.class.getResourceAsStream("/com/images/icon.png")) {
                    assertNotNull(iconStream);
                    stage.getIcons().add(new Image(iconStream));
                }

                SceneManager.initialize(stage);

                // 1. Login screen (50/50 split, maximized)
                SceneManager.showLoginScreen();
                Scene initialScene = stage.getScene();
                assertNotNull(initialScene);
                assertTrue(stage.isMaximized());
                initialScene.getRoot().applyCss();
                initialScene.getRoot().layout();

                HBox rootPane = (HBox) initialScene.lookup("#rootPane");
                VBox leftPane = (VBox) initialScene.lookup("#leftPane");
                VBox rightPane = (VBox) initialScene.lookup("#rightPane");
                assertEquals(rootPane.getWidth() * 0.5, leftPane.getWidth(), 2.0);
                assertEquals(rootPane.getWidth() * 0.5, rightPane.getWidth(), 2.0);

                ImageView loginLogo = (ImageView) initialScene.lookup("#loginLogoView");
                assertNotNull(loginLogo.getImage());
                assertFalse(loginLogo.getImage().isError());

                // 2. MASTER_ADMIN dashboard
                SceneManager.setCurrentUser(admin);
                SceneManager.showDashboardScreen();
                assertSame(initialScene, stage.getScene());
                assertTrue(stage.isMaximized());

                assertTrue(initialScene.lookup("#navOverviewBtn").isVisible());
                assertTrue(initialScene.lookup("#navUserManagementBtn").isVisible());
                assertTrue(initialScene.lookup("#navAssignmentsBtn").isVisible());
                assertFalse(initialScene.lookup("#navMyInvigilationsBtn").isVisible());

                // 3. Logout back to login, then sign in as TEACHER
                SceneManager.setCurrentUser(null);
                SceneManager.showLoginScreen();
                assertSame(initialScene, stage.getScene());
                assertTrue(stage.isMaximized());

                SceneManager.setCurrentUser(teacher1);
                SceneManager.showDashboardScreen();
                assertSame(initialScene, stage.getScene());
                assertTrue(stage.isMaximized());

                Button myInvigBtn = (Button) initialScene.lookup("#navMyInvigilationsBtn");
                assertTrue(myInvigBtn.isVisible() && myInvigBtn.isManaged(),
                        "Teacher must see My Invigilations button");
                for (String sharedNavId : List.of(
                        "#navOverviewBtn", "#navStudentsBtn", "#navRoomsBtn", "#navExamsBtn",
                        "#navAllocationBtn", "#navSeatingBtn")) {
                    Button btn = (Button) initialScene.lookup(sharedNavId);
                    assertTrue(btn.isVisible() && btn.isManaged(),
                            "Shared operational button " + sharedNavId + " must remain visible for TEACHER");
                }
                for (String adminOnlyNavId : List.of("#navUserManagementBtn", "#navAssignmentsBtn")) {
                    Button btn = (Button) initialScene.lookup(adminOnlyNavId);
                    assertFalse(btn.isVisible(), "Admin-only button " + adminOnlyNavId + " must be hidden for TEACHER");
                    assertFalse(btn.isManaged(), "Admin-only button " + adminOnlyNavId + " must be unmanaged for TEACHER");
                }

                // 4. Verify Attendance grid uses non-overlapping VBox seat cards (min 120x85) with 3 Labels
                FXMLLoader attendanceLoader = SceneManager.createLoader("attendance.fxml");
                Parent attendanceRoot = attendanceLoader.load();
                initialScene.setRoot(attendanceRoot);
                com.seatmap.controller.AttendanceController attendanceController = attendanceLoader.getController();
                Exam demoExam = new ExamDAO().getAll().get(0);
                Room demoRoom = new RoomDAO().getAll().get(0);
                attendanceController.loadAssignment(demoExam, demoRoom, () -> { });
                attendanceRoot.applyCss();
                attendanceRoot.layout();
                javafx.scene.layout.GridPane seatGrid =
                        (javafx.scene.layout.GridPane) initialScene.lookup("#seatGrid");
                assertNotNull(seatGrid);
                assertFalse(seatGrid.getChildren().isEmpty(), "Attendance seat grid should populate VBox cards");
                VBox firstCard = (VBox) seatGrid.getChildren().get(0);
                assertTrue(firstCard.getMinWidth() >= 120.0);
                assertTrue(firstCard.getMinHeight() >= 85.0);
                assertEquals(3, firstCard.getChildren().size(),
                        "Occupied seat card should contain 3 separate Labels (Seat, Roll, Status)");
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                if (stage != null) {
                    stage.close();
                }
                done.countDown();
            }
        });

        assertTrue(done.await(15, TimeUnit.SECONDS), "Timed out testing RBAC single-window flow");
        assertNull(failure.get(), () -> "RBAC UI smoke test failure: " + failure.get());
    }

    @Test
    @DisplayName("loads all 11 FXML views and style.css without errors")
    void loadsAllFxmlViewsAndCssCleanly() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                SceneManager.setCurrentUser(
                        new User(1L, "admin", "hash", "Master Administrator", User.ROLE_MASTER_ADMIN));
                String cssUrl = UiSmokeTest.class.getResource("/com/seatmap/css/style.css").toExternalForm();

                List<String> fxmlFiles = List.of(
                        "login.fxml",
                        "dashboard.fxml",
                        "student.fxml",
                        "room.fxml",
                        "exam.fxml",
                        "allocation.fxml",
                        "seating.fxml",
                        "user_management.fxml",
                        "assignments.fxml",
                        "my_invigilations.fxml",
                        "attendance.fxml"
                );

                for (String fxml : fxmlFiles) {
                    FXMLLoader loader = SceneManager.createLoader(fxml);
                    Parent root = loader.load();
                    assertNotNull(root, "Root should not be null for " + fxml);
                    assertNotNull(loader.getController(), "Controller should not be null for " + fxml);

                    Scene scene = new Scene(root, 1280, 800);
                    scene.getStylesheets().add(cssUrl);
                }
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(15, TimeUnit.SECONDS), "Timed out loading all FXML views");
        assertNull(failure.get(), () -> "FXML load failure: " + failure.get());
    }
}
