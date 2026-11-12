# Seatmap Security Model

## Password Storage & Authentication
- Plain-text passwords are never stored in PostgreSQL or logged to console output.
- `AuthService` hashes every password using `jBCrypt` (`BCrypt.gensalt(10)`) producing salted `$2a$` hashes stored in `users.password_hash`.
- Login verification uses `BCrypt.checkpw` inside `AuthService.verifyPassword`, handling `null`, blank inputs, and malformed hash strings gracefully.

## Role-Based Access Control (RBAC)
- Users authenticate into one of two roles enforced by both database check constraints (`CHECK (role IN ('MASTER_ADMIN', 'TEACHER'))`) and application navigation:
  - **`MASTER_ADMIN`**: Full administrative access across Students, Rooms, Exams, Granular Seat Allocation, Seating Charts, Teacher User Management, Invigilator Assignments, and Attendance marking.
  - **`TEACHER`**: Operational access to Overview, Students, Rooms, Exams, Granular Seat Allocation, Seating Charts, `My Invigilations`, and room-specific Attendance marking/PDF export. `User Management` and `Assignments` are hidden and removed from layout (`visible=false`, `managed=false`).

## SQL Injection Prevention
- Every database query, insert, update, and delete in `UserDAO`, `StudentDAO`, `RoomDAO`, `ExamDAO`, `AllocationDAO`, and `RoomAssignmentDAO` uses parameterized JDBC `PreparedStatement` bindings (`?`).
- Student name search (`StudentDAO.searchByName`) escapes SQL `LIKE`/`ILIKE` metacharacters (`\`, `%`, `_`) using `ESCAPE '\'` before parameter binding.
- `DatabaseConnector` validates the configured `db.name` identifier against `^[a-zA-Z_][a-zA-Z0-9_]*$` prior to database creation.

## Credential & Configuration Hygiene
- Database host, port, database name, user, and password are externalized to `src/main/resources/config.properties`.
- `config.properties` is listed in `.gitignore` so local database credentials are never committed to version control.
- CSV uploads (`CsvService`) validate row structure and skip malformed lines rather than executing raw inputs or failing the application.
