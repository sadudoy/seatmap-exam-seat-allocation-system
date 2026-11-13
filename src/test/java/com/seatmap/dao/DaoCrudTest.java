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

    @Test
    @DisplayName("aggregates department counts, room capacity stats, and tests single and bulk allocation deletion")
    void testsAnalyticsAndSingleAndBulkAllocationDeletion() {
        // 1. Department Analytics
        java.util.Map<String, Integer> deptCounts = studentDAO.getDepartmentCounts();
        org.junit.jupiter.api.Assertions.assertNotNull(deptCounts);
        org.junit.jupiter.api.Assertions.assertFalse(deptCounts.isEmpty(), "Department counts should contain seeded departments");
        org.junit.jupiter.api.Assertions.assertTrue(deptCounts.containsKey("CSE") || deptCounts.values().stream().anyMatch(v -> v > 0));

        // 2. Room Capacity Analytics
        java.util.Map<String, Integer[]> roomStats = roomDAO.getRoomCapacityStats();
        org.junit.jupiter.api.Assertions.assertNotNull(roomStats);
        org.junit.jupiter.api.Assertions.assertFalse(roomStats.isEmpty(), "Room capacity stats should contain seeded rooms");
        for (java.util.Map.Entry<String, Integer[]> entry : roomStats.entrySet()) {
            Integer[] stats = entry.getValue();
            org.junit.jupiter.api.Assertions.assertEquals(2, stats.length);
            org.junit.jupiter.api.Assertions.assertTrue(stats[RoomDAO.INDEX_TOTAL_CAPACITY] > 0, "Capacity must be positive");
            org.junit.jupiter.api.Assertions.assertTrue(stats[RoomDAO.INDEX_ALLOCATED_SEATS] >= 0, "Allocated seats must be non-negative");
        }

        // 3. Single and Bulk Deletion
        Student tempStudent1 = null;
        Student tempStudent2 = null;
        Room tempRoom = null;
        Exam tempExam = null;

        try {
            tempStudent1 = studentDAO.add(new Student("DEL-001", "Del Student 1", "CSE", "1"));
            tempStudent2 = studentDAO.add(new Student("DEL-002", "Del Student 2", "EEE", "1"));
            tempRoom = roomDAO.add(new Room("DEL-R101", 10, 2, 5));
            tempExam = examDAO.add(new Exam("Deletion Test Exam", LocalDate.of(2026, 12, 30), LocalTime.of(9, 0)));

            Allocation a1 = allocationDAO.saveAllocation(
                    new Allocation(tempExam.getId(), tempStudent1.getId(), tempRoom.getId(), 0, 0));
            Allocation a2 = allocationDAO.saveAllocation(
                    new Allocation(tempExam.getId(), tempStudent2.getId(), tempRoom.getId(), 0, 1));

            assertEquals(2, allocationDAO.getByExamId(tempExam.getId()).size());

            // Test Single Delete
            assertTrue(allocationDAO.deleteAllocation((int) a1.getId()));
            assertEquals(1, allocationDAO.getByExamId(tempExam.getId()).size());
            assertEquals(a2.getId(), allocationDAO.getByExamId(tempExam.getId()).get(0).getId());

            // Add back an allocation and test Bulk Delete via AllocationService and AllocationDAO
            allocationDAO.saveAllocation(
                    new Allocation(tempExam.getId(), tempStudent1.getId(), tempRoom.getId(), 0, 0));
            assertEquals(2, allocationDAO.getByExamId(tempExam.getId()).size());

            com.seatmap.service.AllocationService allocationService = new com.seatmap.service.AllocationService();
            int deleted = allocationService.deleteAllocationsByExam((int) tempExam.getId());
            assertEquals(2, deleted);
            assertTrue(allocationDAO.getByExamId(tempExam.getId()).isEmpty());
        } finally {
            if (tempExam != null && tempExam.getId() > 0) {
                examDAO.delete(tempExam.getId());
            }
            if (tempRoom != null && tempRoom.getId() > 0) {
                roomDAO.delete(tempRoom.getId());
            }
            if (tempStudent1 != null && tempStudent1.getId() > 0) {
                studentDAO.delete(tempStudent1.getId());
            }
            if (tempStudent2 != null && tempStudent2.getId() > 0) {
                studentDAO.delete(tempStudent2.getId());
            }
        }
    }
}
