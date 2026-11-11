package com.seatmap.dao;

import com.seatmap.config.DatabaseConnector;
import com.seatmap.model.Allocation;
import com.seatmap.model.Exam;
import com.seatmap.model.Room;
import com.seatmap.model.Student;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DAO CRUD Operations")
class DaoCrudTest {

    private final StudentDAO studentDAO = new StudentDAO();
    private final RoomDAO roomDAO = new RoomDAO();
    private final ExamDAO examDAO = new ExamDAO();
    private final AllocationDAO allocationDAO = new AllocationDAO();

    @BeforeAll
    static void initDb() throws SQLException {
        DatabaseConnector.initializeDatabase();
    }

    @AfterAll
    static void closeDb() {
        DatabaseConnector.close();
    }

    @Test
    @DisplayName("performs CRUD, filtered lookup, name search, and manual seat override")
    void exercisesStudentRoomExamAndAllocationDaos() {
        Student student = null;
        Room room = null;
        Exam exam = null;

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

            Allocation allocation = allocationDAO.saveAllocation(
                    new Allocation(exam.getId(), student.getId(), room.getId(), 0, 0));
            assertTrue(allocation.getId() > 0);

            assertTrue(allocationDAO.updateSeat(allocation.getId(), 2, 3));
            List<Allocation> fetched = allocationDAO.getByExamId(exam.getId());
            assertEquals(1, fetched.size());
            assertEquals(2, fetched.get(0).getSeatRow());
            assertEquals(3, fetched.get(0).getSeatCol());

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
        }
    }
}
