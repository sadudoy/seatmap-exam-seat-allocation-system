# Seatmap Architecture

Seatmap follows a layered desktop architecture separating presentation (JavaFX FXML + CSS), application navigation, business services, JDBC data access, and PostgreSQL persistence.

## Layer Overview

```text
+-------------------------------------------------------------------+
| Presentation Layer (JavaFX 21 + FXML + CSS)                       |
|  App -> SceneManager -> LoginController / DashboardController     |
|  Admin Views: Student, Room, Exam, Allocation, Seating, Users,    |
|               Assignments                                         |
|  Teacher Views: MyInvigilationsController, AttendanceController   |
+-------------------------------------------------------------------+
                                 |
                                 v
+-------------------------------------------------------------------+
| Service Layer (Business Logic & Validation)                       |
|  AuthService | AllocationService | CsvService | ExportService     |
+-------------------------------------------------------------------+
                                 |
                                 v
+-------------------------------------------------------------------+
| Data Access Layer (JDBC + PreparedStatement + Transactions)       |
|  UserDAO | StudentDAO | RoomDAO | ExamDAO | AllocationDAO         |
|  RoomAssignmentDAO | JdbcSupport | DataSeeder                     |
+-------------------------------------------------------------------+
                                 |
                                 v
+-------------------------------------------------------------------+
| Persistence Infrastructure                                        |
|  DatabaseConnector (HikariCP Pool) -> PostgreSQL (seatmap_db)     |
+-------------------------------------------------------------------+
```

## Single-Window Navigation & RBAC
- `App` initializes the schema via `DatabaseConnector.initializeDatabase()`, runs `DataSeeder.seedIfEmpty()`, sets the taskbar icon (`/com/images/icon.png`), and launches the single maximized `Stage` via `SceneManager`.
- `SceneManager` maintains a single `Stage` and reuses a single `Scene` instance (`scene.setRoot(...)`) across Login, Dashboard, and Logout.
- After `AuthService.login(username, password)` succeeds, `DashboardController` inspects `SceneManager.getCurrentUser().getRole()`:
  - **`MASTER_ADMIN`**: Full sidebar access (`Overview`, `Students`, `Rooms`, `Exams`, `Allocation`, `Seating Chart`, `User Management`, `Assignments`, `Logout`).
  - **`TEACHER`**: Operational sidebar access (`Overview`, `Students`, `Rooms`, `Exams`, `Allocation`, `Seating Chart`, `My Invigilations`, `Logout`) with `User Management` and `Assignments` hidden (`visible=false`, `managed=false`).

## Granular Anti-Cheating Seat Allocation Engine
- `AllocationController` lets users filter cohorts by multiple departments and multiple numeric semesters (`StudentDAO.getFilteredStudents`) and choose a strategy (`Anti-Cheating Mix` or `Standard Sequential`).
- In **Anti-Cheating Mix**, `AllocationService` groups eligible students by normalized department and schedules rooms sequentially.
- For each room (`rows x columns`, bounded by `room.capacity`), `BacktrackState` searches in row-major order while enforcing that no orthogonally adjacent desk (`front`, `back`, `left`, `right`) contains a student from the same department.
- Candidate departments are prioritized by descending remaining student count to minimize dead ends; if full room occupancy is impossible (e.g., single-department cohorts exceeding checkerboard capacity $\lceil (R \cdot C) / 2 \rceil$), a bounded `searchMaximal` pass places as many valid seats as possible and returns unallocated overflow students in `AllocationResult`.
- `AllocationDAO.replaceAllocationsForExam` persists all seats atomically inside a single JDBC transaction (`setAutoCommit(false)`).

## Document & Data Pipelines
- **CSV Import/Export (`CsvService`):** Built on OpenCSV 5.9 with flexible header matching (`RollNumber`, `Name`, `Department`, `Semester`), row-level malformed syntax tolerance, and UTF-8 CSV exports.
- **PDF Generation (`ExportService`):** Built on Apache PDFBox 2.0.30, providing both visual room seating grid PDFs (`exportRoomSeatingChart`) and paginated Teacher Attendance Sheet PDFs (`exportAttendanceSheet`) with status counts and signature checkboxes.
