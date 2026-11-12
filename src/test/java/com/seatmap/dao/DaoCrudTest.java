package com.seatmap.dao;

import com.seatmap.config.DatabaseConnector;
import com.seatmap.model.Allocation;
import com.seatmap.model.Exam;
import com.seatmap.model.Room;
import com.seatmap.model.RoomAssignment;
import com.seatmap.model.Student;
import com.seatmap.model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DAO CRUD Operations")
class DaoCrudTest {

    private final StudentDAO studentDAO = new StudentDAO();
    private final RoomDAO roomDAO = new RoomDAO();
    private final ExamDAO examDAO = new ExamDAO();
    private final AllocationDAO allocationDAO = new AllocationDAO();
    private final UserDAO userDAO = new UserDAO();
    private final RoomAssignmentDAO assignmentDAO = new RoomAssignmentDAO();

    @BeforeAll
    static void initDb() throws SQLException {
        DatabaseConnector.initializeDatabase();
    }

    @AfterAll
    static void closeDb() {
        DatabaseConnector.close();
    }

    @Test
    @DisplayName("performs CRUD, attendance updates, room assignments, and filtered lookups")
    void exercisesStudentRoomExamAllocationUserAndAssignmentDaos() {
        Student student = null;
        Room room = null;
        Exam exam = null;
        User teacher = null;

        try {
            student = studentDAO.add(new Student("DAO-101", "Zara_Test%User", "CSE", "DAO-SEM-7"));
            assertTrue(student.getId() > 0);

            student.setName("Zara_Updated%User");
            assertTrue(studentDAO.update(student));

            long studentId = student.getId();
            List<Student> byDeptSem = studentDAO.getByDepartmentAndSemester("cse", "dao-sem-7");
            assertTrue(byDeptSem.stream().anyMatch(s -> s.getId() == studentId));

            // Literal '%' and '_' in name should be matched accurately via ESCAPE handling.
            List<Student> searched = studentDAO.searchByName("Updated%");
            assertEquals(1, searched.size());
            assertEquals(student.getId(), searched.get(0).getId());

            room = roomDAO.add(new Room("DAO-R999", 12, 3, 4));
            room.setCapacity(10);
            assertTrue(roomDAO.update(room));
            assertEquals(10, roomDAO.getById(room.getId()).orElseThrow().getCapacity());

            exam = examDAO.add(new Exam("DAO Networks", LocalDate.of(2026, 12, 25), LocalTime.of(10, 0)));
            exam.setSubject("DAO Networks II");
            assertTrue(examDAO.update(exam));
            assertEquals("DAO Networks II", examDAO.getById(exam.getId()).orElseThrow().getSubject());

            teacher = userDAO.createUser(new User(
                    "dao_teacher_" + UUID.randomUUID(),
                    "$2a$10$dummyhashfortestonly123456789012345678901234567890",
                    "DAO Test Teacher",
                    User.ROLE_TEACHER));
            long teacherId = teacher.getId();
            assertTrue(userDAO.getAllTeachers().stream().anyMatch(u -> u.getId() == teacherId));

            RoomAssignment assignment = assignmentDAO.assign(exam.getId(), room.getId(), teacher.getId());
            assertTrue(assignment.getId() > 0);
            assertEquals(1, assignmentDAO.getByTeacherId(teacher.getId()).size());
            assertTrue(assignmentDAO.deleteAssignment(assignment.getId()));

            Allocation allocation = allocationDAO.saveAllocation(
                    new Allocation(exam.getId(), student.getId(), room.getId(), 0, 0));
            assertEquals(Allocation.STATUS_PENDING, allocation.getStatus());

            assertTrue(allocationDAO.updateSeat(allocation.getId(), 2, 3));
            assertTrue(allocationDAO.updateAttendanceStatus(allocation.getId(), Allocation.STATUS_PRESENT));

            List<Allocation> fetched = allocationDAO.getByExamId(exam.getId());
            assertEquals(1, fetched.size());
            assertEquals(2, fetched.get(0).getSeatRow());
            assertEquals(3, fetched.get(0).getSeatCol());
            assertEquals(Allocation.STATUS_PRESENT, fetched.get(0).getStatus());

            assertEquals(1, allocationDAO.clearAllocationsForExam(exam.getId()));
            assertTrue(allocationDAO.getByExamId(exam.getId()).isEmpty());
        } finally {
            if (exam != null && exam.getId() > 0) {
                examDAO.delete(exam.getId());
            }
            if (room != null && room.getId() > 0) {
                roomDAO.delete(room.getId());
            }
            if (student != null && student.getId() > 0) {
                studentDAO.delete(student.getId());
            }
            if (teacher != null && teacher.getId() > 0) {
                userDAO.deleteUser(teacher.getId());
            }
        }
    }
}
