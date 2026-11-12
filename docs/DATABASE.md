# Seatmap Database Design

Seatmap uses PostgreSQL (`seatmap_db`) managed through HikariCP (`SeatmapHikariPool`) and initialized idempotently from `src/main/resources/schema.sql`.

## Schema Tables

### `users`
Stores authenticated accounts and role assignments (`admins` from earlier phases is dropped during Phase 4 migration).
- `id` — `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
- `username` — `TEXT NOT NULL UNIQUE`
- `password_hash` — `TEXT NOT NULL` (BCrypt hash)
- `full_name` — `TEXT NOT NULL`
- `role` — `VARCHAR(32) NOT NULL CHECK (role IN ('MASTER_ADMIN', 'TEACHER'))`

### `students`
Stores registered examination candidates.
- `id` — `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
- `roll_number` — `TEXT NOT NULL UNIQUE`
- `name` — `TEXT NOT NULL`
- `department` — `TEXT NOT NULL`
- `semester` — `TEXT NOT NULL`

### `rooms`
Defines examination rooms and physical desk dimensions.
- `id` — `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
- `room_number` — `TEXT NOT NULL UNIQUE`
- `capacity` — `INTEGER NOT NULL CHECK (capacity > 0)`
- `rows` — `INTEGER NOT NULL CHECK (rows > 0)`
- `columns` — `INTEGER NOT NULL CHECK (columns > 0)`

### `exams`
Stores scheduled examination sessions.
- `id` — `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
- `subject` — `TEXT NOT NULL`
- `exam_date` — `DATE NOT NULL`
- `start_time` — `TIME NOT NULL`

### `allocations`
Maps each student to a room desk for a specific exam and tracks attendance status.
- `id` — `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
- `exam_id` — `BIGINT NOT NULL REFERENCES exams(id) ON DELETE CASCADE`
- `student_id` — `BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE`
- `room_id` — `BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE`
- `seat_row` — `INTEGER NOT NULL CHECK (seat_row >= 0)`
- `seat_col` — `INTEGER NOT NULL CHECK (seat_col >= 0)`
- `status` — `VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PRESENT', 'ABSENT'))`
- Unique constraints: `(exam_id, student_id)` and `(exam_id, room_id, seat_row, seat_col)`
- Indexes: `idx_allocations_exam_id`, `idx_allocations_student_id`, `idx_allocations_room_id`

### `room_assignments`
Assigns invigilating teachers (`users`) to specific `(exam_id, room_id)` pairs.
- `id` — `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
- `exam_id` — `BIGINT NOT NULL REFERENCES exams(id) ON DELETE CASCADE`
- `room_id` — `BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE`
- `teacher_id` — `BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE`
- Unique constraint: `(exam_id, room_id, teacher_id)`
- Indexes: `idx_room_assignments_exam_id`, `idx_room_assignments_room_id`, `idx_room_assignments_teacher_id`

## Connection Pooling & Initialization
- `DatabaseConnector` reads credentials from `src/main/resources/config.properties` via a try-with-resources stream.
- On startup, `ensureDatabaseExists` connects to the `postgres` maintenance database, creates `seatmap_db` if missing, and initializes a HikariCP pool (`maximumPoolSize = 10`, `minimumIdle = 2`).
- `initializeDatabase()` executes `schema.sql` within an explicit JDBC transaction, safely migrating existing Phase 1–3 databases (`DROP TABLE IF EXISTS admins`, `ALTER TABLE allocations ADD COLUMN IF NOT EXISTS status ...`).
