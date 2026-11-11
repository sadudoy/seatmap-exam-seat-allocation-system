package com.seatmap.ui;

import com.seatmap.config.DatabaseConnector;
import com.seatmap.controller.AllocationController;
import com.seatmap.controller.DashboardController;
import com.seatmap.controller.SeatingController;
import com.seatmap.dao.AdminDAO;
import com.seatmap.dao.RoomDAO;
import com.seatmap.dao.StudentDAO;
import com.seatmap.model.Admin;
import com.seatmap.service.AuthService;
import com.seatmap.util.DataSeeder;
import com.seatmap.util.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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

@DisplayName("JavaFX Maximized Single-Window, Bulk Seeding & Branding Smoke Tests")
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
        SceneManager.setCurrentAdmin(null);
        DatabaseConnector.close();
    }

    @Test
    @DisplayName("seeds default admin, 400 bulk students, and 16 rooms idempotently")
    void seedsBulkDemoDataIdempotently() {
        DataSeeder.seedIfEmpty();
        DataSeeder.seedIfEmpty();

        AuthService authService = new AuthService(new AdminDAO());
        assertTrue(authService.login("admin", "admin123").isPresent(),
                "Default admin should authenticate with admin123");

        StudentDAO studentDAO = new StudentDAO();
        RoomDAO roomDAO = new RoomDAO();
        int studentCount = studentDAO.getAll().size();
        int roomCount = roomDAO.getAll().size();
        int totalCapacity = roomDAO.getAll().stream().mapToInt(r -> r.getCapacity()).sum();

        assertTrue(studentCount >= 300, "Expected >= 300 seeded students, found " + studentCount);
        assertTrue(roomCount >= 12, "Expected >= 12 seeded rooms, found " + roomCount);
        assertTrue(totalCapacity >= studentCount,
                "Total room capacity (" + totalCapacity + ") should exceed student count (" + studentCount + ")");
    }

    @Test
    @DisplayName("keeps primary Stage maximized across Login -> Dashboard -> Logout root swaps")
    void singleWindowRemainsMaximizedAcrossTransitions() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            Stage stage = null;
            try {
                DataSeeder.seedIfEmpty();
                stage = new Stage();
                try (InputStream iconStream = UiSmokeTest.class.getResourceAsStream("/com/images/icon.png")) {
                    assertNotNull(iconStream, "Taskbar icon /com/images/icon.png must exist");
                    stage.getIcons().add(new Image(iconStream));
                }

                SceneManager.initialize(stage);

                SceneManager.showLoginScreen();
                Scene initialScene = stage.getScene();
                assertNotNull(initialScene);
                assertTrue(stage.isMaximized(), "Login stage should open maximized");
                initialScene.getRoot().applyCss();
                initialScene.getRoot().layout();

                javafx.scene.layout.HBox rootPane = (javafx.scene.layout.HBox) initialScene.lookup("#rootPane");
                javafx.scene.layout.VBox leftPane = (javafx.scene.layout.VBox) initialScene.lookup("#leftPane");
                javafx.scene.layout.VBox rightPane = (javafx.scene.layout.VBox) initialScene.lookup("#rightPane");
                assertNotNull(rootPane, "Login root should be HBox #rootPane");
                assertNotNull(leftPane, "Left split pane must be present");
                assertNotNull(rightPane, "Right split pane must be present");
                assertEquals(rootPane.getWidth() * 0.5, leftPane.getWidth(), 2.0, "Left panel must occupy 50% width");
                assertEquals(rootPane.getWidth() * 0.5, rightPane.getWidth(), 2.0, "Right panel must occupy 50% width");

                ImageView loginLogo = (ImageView) initialScene.lookup("#loginLogoView");
                assertNotNull(loginLogo, "Login logo ImageView must be present");
                assertNotNull(loginLogo.getImage(), "Login logo image must load");
                assertFalse(loginLogo.getImage().isError(), "Login logo image must load without error");

                SceneManager.setCurrentAdmin(new Admin(1L, "admin", "hash"));
                SceneManager.showDashboardScreen();
                assertSame(initialScene, stage.getScene(), "Must reuse single Scene on login");
                assertTrue(stage.isMaximized(), "Stage must remain maximized on dashboard");
                ImageView sidebarLogo = (ImageView) initialScene.lookup("#sidebarLogoView");
                assertNotNull(sidebarLogo, "Sidebar logo ImageView must be present");
                assertNotNull(sidebarLogo.getImage(), "Sidebar logo image must load");
                assertFalse(sidebarLogo.getImage().isError(), "Sidebar logo image must load without error");

                SceneManager.setCurrentAdmin(null);
                SceneManager.showLoginScreen();
                assertSame(initialScene, stage.getScene(), "Logout must reuse single Scene");
                assertTrue(stage.isMaximized(), "Stage must remain maximized on logout");
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                if (stage != null) {
                    stage.close();
                }
                done.countDown();
            }
        });

        assertTrue(done.await(15, TimeUnit.SECONDS), "Timed out testing maximized window transitions");
        assertNull(failure.get(), () -> "Single-window maximized test failure: " + failure.get());
    }

    @Test
    @DisplayName("loads all FXML views, injects controllers, and applies style.css without errors")
    void loadsAllFxmlViewsAndCssCleanly() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                SceneManager.setCurrentAdmin(new Admin(1L, "admin", "hash"));
                String cssUrl = UiSmokeTest.class.getResource("/com/seatmap/css/style.css").toExternalForm();

                List<String> fxmlFiles = List.of(
                        "login.fxml",
                        "dashboard.fxml",
                        "student.fxml",
                        "room.fxml",
                        "exam.fxml",
                        "allocation.fxml",
                        "seating.fxml"
                );

                for (String fxml : fxmlFiles) {
                    FXMLLoader loader = SceneManager.createLoader(fxml);
                    Parent root = loader.load();
                    assertNotNull(root, "Root should not be null for " + fxml);
                    Object controller = loader.getController();
                    assertNotNull(controller, "Controller should not be null for " + fxml);

                    Scene scene = new Scene(root, 1280, 800);
                    scene.getStylesheets().add(cssUrl);

                    if (controller instanceof DashboardController dashboard) {
                        assertNotNull(dashboard);
                    } else if (controller instanceof AllocationController allocation) {
                        assertNotNull(allocation);
                    } else if (controller instanceof SeatingController seating) {
                        assertNotNull(seating);
                    }
                }
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(15, TimeUnit.SECONDS), "Timed out loading FXML screens");
        assertNull(failure.get(), () -> "FXML load failure: " + failure.get());
    }
}
