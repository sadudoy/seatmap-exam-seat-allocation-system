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
  - **`MASTER_ADMIN`**: Full sidebar access (`Overview`, `Students`, `Rooms`, `Exams`, `Allocation`, `Seating Chart`, `User Management`, `Assignments`, `Logout`). In the Allocation screen, the **Clear All Allocations for Exam** action button is visible and managed.
  - **`TEACHER`**: Operational sidebar access (`Overview`, `Students`, `Rooms`, `Exams`, `Allocation`, `Seating Chart`, `My Invigilations`, `Logout`) with `User Management` and `Assignments` hidden (`visible=false`, `managed=false`). In the Allocation screen, the bulk clear button is completely hidden and unmanaged.

## Dashboard Visual Analytics & Analytical Queries
- **Department Distribution (`PieChart`):** `StudentDAO.getDepartmentCounts()` runs an aggregated grouping query (`SELECT department, COUNT(*) ... GROUP BY department ORDER BY department`) and renders slice percentages and counts dynamically in `DashboardController.refreshOverviewStats()`.
- **Room Utilization (`BarChart`):** `RoomDAO.getRoomCapacityStats()` executes a left join aggregation between `rooms` and `allocations` (`SELECT r.room_number, r.capacity, COUNT(a.id) ... GROUP BY r.id, r.room_number, r.capacity ORDER BY r.room_number`), returning a `Map<String, Integer[]>` where index 0 is Total Capacity and index 1 is Allocated Seats. Rendered as a multi-series bar chart for immediate visual feedback.

## Allocation Lifecycle Management (Single & Bulk Deletion)
- **Single-Seat Removal:** Right-clicking any occupied seat card in the Visual Seating Chart (`SeatingController`) or Attendance Grid (`AttendanceController`) presents a `ContextMenu` with a "Remove Student" option. After confirmation, `AllocationDAO.deleteAllocation(allocationId)` removes the assignment and re-renders the room floor grid.
- **Bulk Allocation Clearing:** `AllocationDAO.deleteAllocationsByExam(examId)` and `AllocationService.deleteAllocationsByExam(examId)` perform an atomic delete of all allocations assigned to a target exam. Protected by `MASTER_ADMIN` RBAC and triggered via an interactive `ChoiceDialog` with severe confirmation safeguards.

## Granular Anti-Cheating Seat Allocation Engine
- `AllocationController` lets users filter cohorts by multiple departments and multiple numeric semesters (`StudentDAO.getFilteredStudents`) and choose a strategy (`Anti-Cheating Mix` or `Standard Sequential`).
- In **Anti-Cheating Mix**, `AllocationService` groups eligible students by normalized department and schedules rooms sequentially.
- For each room (`rows x columns`, bounded by `room.capacity`), `BacktrackState` searches in row-major order while enforcing that no orthogonally adjacent desk (`front`, `back`, `left`, `right`) contains a student from the same department.
- Candidate departments are prioritized by descending remaining student count to minimize dead ends; if full room occupancy is impossible (e.g., single-department cohorts exceeding checkerboard capacity $\lceil (R \cdot C) / 2 \rceil$), a bounded `searchMaximal` pass places as many valid seats as possible and returns unallocated overflow students in `AllocationResult`.
- `AllocationDAO.replaceAllocationsForExam` persists all seats atomically inside a single JDBC transaction (`setAutoCommit(false)`).

## Document & Data Pipelines
- **CSV Import/Export (`CsvService`):** Built on OpenCSV 5.9 with flexible header matching (`RollNumber`, `Name`, `Department`, `Semester`), row-level malformed syntax tolerance, and UTF-8 CSV exports.
- **PDF Generation (`ExportService`):** Built on Apache PDFBox 2.0.30, providing both visual room seating grid PDFs (`exportRoomSeatingChart`) and paginated Teacher Attendance Sheet PDFs (`exportAttendanceSheet`) with status counts and signature checkboxes.
