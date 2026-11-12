-- Seatmap Exam Seat Allocation System - Phase 4 Schema (RBAC, Assignments & Attendance)

DROP TABLE IF EXISTS admins;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    full_name TEXT NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('MASTER_ADMIN', 'TEACHER'))
);

CREATE TABLE IF NOT EXISTS students (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    roll_number TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    department TEXT NOT NULL,
    semester TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS rooms (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_number TEXT NOT NULL UNIQUE,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    rows INTEGER NOT NULL CHECK (rows > 0),
    columns INTEGER NOT NULL CHECK (columns > 0)
);

CREATE TABLE IF NOT EXISTS exams (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject TEXT NOT NULL,
    exam_date DATE NOT NULL,
    start_time TIME NOT NULL
);

CREATE TABLE IF NOT EXISTS allocations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exam_id BIGINT NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    room_id BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    seat_row INTEGER NOT NULL CHECK (seat_row >= 0),
    seat_col INTEGER NOT NULL CHECK (seat_col >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    UNIQUE (exam_id, student_id),
    UNIQUE (exam_id, room_id, seat_row, seat_col)
);

ALTER TABLE allocations
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE allocations
    DROP CONSTRAINT IF EXISTS chk_allocations_status;

ALTER TABLE allocations
    ADD CONSTRAINT chk_allocations_status CHECK (status IN ('PENDING', 'PRESENT', 'ABSENT'));

CREATE TABLE IF NOT EXISTS room_assignments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exam_id BIGINT NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    room_id BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    teacher_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE (exam_id, room_id, teacher_id)
);

CREATE INDEX IF NOT EXISTS idx_allocations_exam_id ON allocations (exam_id);
CREATE INDEX IF NOT EXISTS idx_allocations_student_id ON allocations (student_id);
CREATE INDEX IF NOT EXISTS idx_allocations_room_id ON allocations (room_id);
CREATE INDEX IF NOT EXISTS idx_room_assignments_exam_id ON room_assignments (exam_id);
CREATE INDEX IF NOT EXISTS idx_room_assignments_room_id ON room_assignments (room_id);
CREATE INDEX IF NOT EXISTS idx_room_assignments_teacher_id ON room_assignments (teacher_id);
