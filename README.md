<img src="src/main/resources/com/images/icon2.png" width="300" />

# Seatmap — Enterprise Exam Seat Allocation System

**Seatmap** is a desktop exam operations and seat allocation platform built for educational institutions. Powered by JavaFX and PostgreSQL, it streamlines student management, examination room configuration, invigilator scheduling, automated anti-cheating seat planning, live attendance tracking, and printable PDF reports.

## Tech Stack

- **Language & UI:** Java 17+, JavaFX 21 (FXML + Custom CSS)
- **Database & Pooling:** PostgreSQL, HikariCP 7.1.0, PostgreSQL JDBC Driver
- **Security:** jBCrypt 0.4 (BCrypt password hashing) & Role-Based Access Control
- **Import / Export:** OpenCSV 5.9, Apache PDFBox 2.0.30
- **Build & Testing:** Apache Maven, JUnit 5

## Features

- **Role-Based Access Control (RBAC):**
  - `MASTER_ADMIN`: Full access to Overview, Students, Rooms, Exams, Granular Allocation, Bulk Allocation Clearing, Seating Charts, User Management (create/delete Teachers), Invigilator Assignments, and Attendance marking.
  - `TEACHER`: Access to Overview, Students, Rooms, Exams, Granular Allocation, Seating Charts, **My Invigilations**, and interactive Attendance marking/PDF export (User Management, Invigilator Assignments, and Bulk Allocation Clearing are hidden and inaccessible).
- **Dashboard Graphical Analytics:** Dynamic visual analytics on the Operations Dashboard featuring:
  - **Students by Department (`PieChart`):** Proportional distribution of student enrollments by major.
  - **Room Utilization (`BarChart`):** Side-by-side capacity comparison of Seats Allocated vs Total Capacity per room.
- **Bulk Allocation Management (Master Admin Only):**
  - Secure "Clear All Allocations for Exam" capability in the Granular Allocation engine with multi-exam selector and confirmation warnings.
  - Enforced via role verification both at the UI layer (hidden/unmanaged for Teachers) and backend service boundaries.
- **Interactive Seating Charts & Single-Seat Deletion:**
  - Dedicated VBox seat cards (`120x85` min bounds) with separate Seat, Roll Number (`#0f172a`), and Attendance Status labels (`PRESENT` = Green, `ABSENT` = Red, `PENDING` = Amber).
  - Right-click `ContextMenu` ("Remove Student") on occupied seats allowing invigilators and administrators to vacate specific desks with instant grid re-rendering.
- **Granular Allocation & Anti-Cheating Engine:** Multi-department and multi-semester student cohort filtering (`StudentDAO.getFilteredStudents`) with selectable seating strategies (**Anti-Cheating Mix** using bounded backtracking so no orthogonally adjacent desks share a department, or **Standard Sequential**).
- **Bulk CSV Import & PDF Reporting:** Import student rosters via CSV and export formatted PDF Room Seating Charts and Teacher Attendance Sheets with signature checkboxes.

## How to Run

### 1. Configure PostgreSQL
Ensure PostgreSQL is running locally (default port `5432`) and create `src/main/resources/config.properties` (ignored by Git):

```properties
db.host=localhost
db.port=5432
db.name=seatmap_db
db.user=postgres
db.password=your_password
```

*(On first startup, Seatmap automatically creates `seatmap_db`, initializes the schema, and seeds demo data.)*

### 2. Build & Launch
```bash
mvn clean compile
mvn javafx:run
```

### 3. Run Tests
```bash
mvn test
```

## Default Credentials

| Role | Username | Password | Access Scope |
| :--- | :--- | :--- | :--- |
| `MASTER_ADMIN` | `admin` | `admin123` | Full administrative access |
| `TEACHER` | `teacher1` | `teacher123` | Assigned rooms & attendance marking |

## Documentation

- [Architecture Overview](docs/ARCHITECTURE.md)
- [Database Schema & Pooling](docs/DATABASE.md)
- [Security & Access Control](docs/SECURITY.md)
