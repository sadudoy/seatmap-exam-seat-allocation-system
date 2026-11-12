package com.seatmap.service;

import com.seatmap.config.DatabaseConnector;
import com.seatmap.dao.AllocationDAO;
import com.seatmap.dao.ExamDAO;
import com.seatmap.dao.RoomDAO;
import com.seatmap.dao.StudentDAO;
import com.seatmap.model.Allocation;
import com.seatmap.model.Exam;
import com.seatmap.model.Room;
import com.seatmap.model.Student;
import com.seatmap.service.AllocationService.AllocationResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AllocationService")
class AllocationServiceTest {

    private final AllocationService allocationService = new AllocationService();

    @BeforeAll
    static void initDb() throws SQLException {
        DatabaseConnector.initializeDatabase();
    }

    @AfterAll
    static void closeDb() {
        DatabaseConnector.close();
    }

    @Test
    @DisplayName("allocates multi-department students without duplicate seats or same-department adjacency")
    void allocatesWithoutDuplicatesOrSameDepartmentAdjacency() {
        Exam exam = new Exam(1L, "Algorithms", LocalDate.of(2026, 12, 10), LocalTime.of(10, 0));
        Room room = new Room(1L, "R-101", 12, 3, 4);

        List<Student> students = new ArrayList<>();
        String[] depts = {"CSE", "EEE", "ME", "BBA"};
        for (int i = 0; i < 12; i++) {
            students.add(new Student(i + 1L, "R-" + (i + 1), "Student " + (i + 1), depts[i % depts.length], "4"));
        }

        AllocationResult result = allocationService.planAllocation(exam, students, List.of(room));

        assertEquals(12, result.allocations().size());
        assertFalse(result.hasUnallocatedStudents());
        assertNoDuplicateSeatsOrStudents(result.allocations());
        assertNoAdjacentSameDepartment(result.allocations(), students, List.of(room));
    }

    @Test
    @DisplayName("uses backtracking when greedy placement hits a dead end")
    void solvesDeadEndCaseViaBacktracking() {
        Exam exam = new Exam(2L, "Operating Systems", LocalDate.of(2026, 12, 12), LocalTime.of(9, 0));
        Room room = new Room(2L, "R-202", 6, 2, 3);

        List<Student> students = List.of(
                new Student(1L, "C1", "Alice", "CSE", "5"),
                new Student(2L, "C2", "Bob", "CSE", "5"),
                new Student(3L, "E1", "Carol", "EEE", "5"),
                new Student(4L, "E2", "Dave", "EEE", "5"),
                new Student(5L, "M1", "Eve", "ME", "5"),
                new Student(6L, "M2", "Frank", "ME", "5")
        );

        AllocationResult result = allocationService.planAllocation(exam, students, List.of(room));

        assertEquals(6, result.allocations().size());
        assertTrue(result.unallocatedStudents().isEmpty());
        assertNoDuplicateSeatsOrStudents(result.allocations());
        assertNoAdjacentSameDepartment(result.allocations(), students, List.of(room));
    }

    @Test
    @DisplayName("handles capacity overflow cleanly without duplicate seats")
    void handlesOverflowGracefully() {
        Exam exam = new Exam(3L, "Database Systems", LocalDate.of(2026, 12, 15), LocalTime.of(14, 0));
        Room room = new Room(3L, "R-303", 9, 3, 3);

        List<Student> students = new ArrayList<>();
        String[] depts = {"CSE", "EEE", "ME", "CSE", "EEE", "ME", "CSE", "EEE", "ME", "CSE"};
        for (int i = 0; i < 10; i++) {
            students.add(new Student(i + 1L, "S-" + (i + 1), "Student " + (i + 1), depts[i], "4"));
        }

        AllocationResult result = allocationService.planAllocation(exam, students, List.of(room));

        assertEquals(9, result.allocations().size());
        assertEquals(1, result.unallocatedStudents().size());
        assertNoDuplicateSeatsOrStudents(result.allocations());
        assertNoAdjacentSameDepartment(result.allocations(), students, List.of(room));
    }

    @Test
    @DisplayName("enforces anti-cheating checkerboard spacing when all students share one department")
    void singleDepartmentNeverPlacesAdjacentStudents() {
        Exam exam = new Exam(4L, "Compiler Design", LocalDate.of(2026, 12, 18), LocalTime.of(9, 0));
        Room room = new Room(4L, "R-104", 9, 3, 3);

        List<Student> students = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            students.add(new Student(i + 1L, "CSE-" + (i + 1), "Student " + (i + 1), "CSE", "6"));
        }

        AllocationResult result = allocationService.planAllocation(exam, students, List.of(room));

        // Maximum independent set in a 3x3 grid is 5 seats.
        assertEquals(5, result.allocations().size());
        assertEquals(5, result.unallocatedStudents().size());
        assertNoDuplicateSeatsOrStudents(result.allocations());
        assertNoAdjacentSameDepartment(result.allocations(), students, List.of(room));
    }

    @Test
    @DisplayName("persists allocations transactionally and respects ON DELETE CASCADE")
    void persistsAllocationsAndCascadesOnExamDelete() {
        StudentDAO studentDAO = new StudentDAO();
        RoomDAO roomDAO = new RoomDAO();
        ExamDAO examDAO = new ExamDAO();
        AllocationDAO allocationDAO = new AllocationDAO();

        List<Student> createdStudents = new ArrayList<>();
        Room createdRoom = null;
        Exam createdExam = null;

        try {
            String semester = "TEST-SEM-ALLOC";
            createdStudents.add(studentDAO.add(new Student("TST-A1", "Test A1", "CSE", semester)));
            createdStudents.add(studentDAO.add(new Student("TST-A2", "Test A2", "EEE", semester)));
            createdStudents.add(studentDAO.add(new Student("TST-A3", "Test A3", "ME", semester)));
            createdStudents.add(studentDAO.add(new Student("TST-A4", "Test A4", "CSE", semester)));

            createdRoom = roomDAO.add(new Room("TST-ROOM-900", 4, 2, 2));
            createdExam = examDAO.add(new Exam(
                    "Integration Test Exam",
                    LocalDate.of(2026, 12, 20),
                    LocalTime.of(11, 0),
                    "ALL",
                    semester
            ));

            AllocationResult result = allocationService.planAllocation(
                    createdExam, createdStudents, List.of(createdRoom));
            allocationDAO.replaceAllocationsForExam(createdExam.getId(), result.allocations());

            List<Allocation> fetched = allocationDAO.getByExamId(createdExam.getId());
            assertEquals(4, fetched.size());
            assertNoDuplicateSeatsOrStudents(fetched);
            assertNoAdjacentSameDepartment(fetched, createdStudents, List.of(createdRoom));

            assertTrue(examDAO.delete(createdExam.getId()));
            createdExam = null;

            // Verify ON DELETE CASCADE cleared allocations when the exam was deleted.
            assertTrue(allocationDAO.getByExamId(fetched.get(0).getExamId()).isEmpty());
        } finally {
            if (createdExam != null && createdExam.getId() > 0) {
                examDAO.delete(createdExam.getId());
            }
            if (createdRoom != null && createdRoom.getId() > 0) {
                roomDAO.delete(createdRoom.getId());
            }
            for (Student student : createdStudents) {
                if (student.getId() > 0) {
                    studentDAO.delete(student.getId());
                }
            }
        }
    }

    @Test
    @DisplayName("runs granular allocation filtering only CSE and Semester 1 students")
    void granularAllocationFiltersByDepartmentAndSemester() {
        StudentDAO studentDAO = new StudentDAO();
        RoomDAO roomDAO = new RoomDAO();
        ExamDAO examDAO = new ExamDAO();
        AllocationDAO allocationDAO = new AllocationDAO();

        Student matchingStudent = null;
        Student nonMatchingStudent = null;
        Room tempRoom = null;
        Exam tempExam = null;

        try {
            matchingStudent = studentDAO.add(new Student("GRAN-CSE-001", "Granular Match", "CSE", "1"));
            nonMatchingStudent = studentDAO.add(new Student("GRAN-EEE-002", "Granular Skip", "EEE", "2"));

            List<Student> filtered = studentDAO.getFilteredStudents(List.of("CSE"), List.of(1));
            long matchingId = matchingStudent.getId();
            long nonMatchingId = nonMatchingStudent.getId();
            assertTrue(filtered.stream().anyMatch(s -> s.getId() == matchingId));
            assertFalse(filtered.stream().anyMatch(s -> s.getId() == nonMatchingId));
            assertTrue(filtered.stream().allMatch(
                    s -> "CSE".equalsIgnoreCase(s.getDepartment()) && "1".equals(s.getSemester())));

            tempRoom = roomDAO.add(new Room("GRAN-HALL", 40, 5, 8));
            tempExam = examDAO.add(new Exam("Granular CSE-1 Exam",
                    LocalDate.of(2026, 12, 28), LocalTime.of(14, 0)));

            AllocationResult result = allocationService.runAllocation(
                    tempExam.getId(), List.of("CSE"), List.of(1),
                    AllocationService.STRATEGY_ANTI_CHEATING_MIX);

            Map<Long, Student> byId = new HashMap<>();
            for (Student s : studentDAO.getAll()) {
                byId.put(s.getId(), s);
            }
            List<Allocation> saved = allocationDAO.getByExamId(tempExam.getId());
            assertEquals(result.allocations().size(), saved.size());
            assertTrue(saved.stream().anyMatch(a -> a.getStudentId() == matchingId));
            assertFalse(saved.stream().anyMatch(a -> a.getStudentId() == nonMatchingId));
            assertTrue(saved.stream().allMatch(a -> {
                Student s = byId.get(a.getStudentId());
                return s != null && "CSE".equalsIgnoreCase(s.getDepartment()) && "1".equals(s.getSemester());
            }));
        } finally {
            if (tempExam != null && tempExam.getId() > 0) {
                examDAO.delete(tempExam.getId());
            }
            if (tempRoom != null && tempRoom.getId() > 0) {
                roomDAO.delete(tempRoom.getId());
            }
            if (matchingStudent != null && matchingStudent.getId() > 0) {
                studentDAO.delete(matchingStudent.getId());
            }
            if (nonMatchingStudent != null && nonMatchingStudent.getId() > 0) {
                studentDAO.delete(nonMatchingStudent.getId());
            }
        }
    }

    private static void assertNoDuplicateSeatsOrStudents(List<Allocation> allocations) {
        Set<Long> studentIds = new HashSet<>();
        Set<String> seatKeys = new HashSet<>();

        for (Allocation allocation : allocations) {
            assertTrue(studentIds.add(allocation.getStudentId()),
                    () -> "Duplicate allocation for studentId=" + allocation.getStudentId());
            String seatKey = allocation.getRoomId() + ":" + allocation.getSeatRow() + ":" + allocation.getSeatCol();
            assertTrue(seatKeys.add(seatKey),
                    () -> "Duplicate seat assignment at " + seatKey);
        }
    }

    private static void assertNoAdjacentSameDepartment(List<Allocation> allocations,
                                                       List<Student> students,
                                                       List<Room> rooms) {
        Map<Long, Student> studentById = new HashMap<>();
        for (Student student : students) {
            studentById.put(student.getId(), student);
        }

        Map<Long, Room> roomById = new HashMap<>();
        Map<Long, Student[][]> grids = new HashMap<>();
        for (Room room : rooms) {
            roomById.put(room.getId(), room);
            grids.put(room.getId(), new Student[room.getRows()][room.getColumns()]);
        }

        for (Allocation allocation : allocations) {
            Student student = studentById.get(allocation.getStudentId());
            Student[][] grid = grids.get(allocation.getRoomId());
            grid[allocation.getSeatRow()][allocation.getSeatCol()] = student;
        }

        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (Map.Entry<Long, Student[][]> entry : grids.entrySet()) {
            Room room = roomById.get(entry.getKey());
            Student[][] grid = entry.getValue();

            for (int r = 0; r < room.getRows(); r++) {
                for (int c = 0; c < room.getColumns(); c++) {
                    Student current = grid[r][c];
                    if (current == null) {
                        continue;
                    }
                    for (int[] dir : directions) {
                        int nr = r + dir[0];
                        int nc = c + dir[1];
                        if (nr >= 0 && nr < room.getRows() && nc >= 0 && nc < room.getColumns()) {
                            Student neighbor = grid[nr][nc];
                            if (neighbor != null) {
                                final int row = r;
                                final int col = c;
                                assertFalse(
                                        current.getDepartment().equalsIgnoreCase(neighbor.getDepartment()),
                                        () -> String.format("Adjacent same-department students (%s) at (%d,%d) and (%d,%d)",
                                                current.getDepartment(), row, col, nr, nc));
                            }
                        }
                    }
                }
            }
        }
    }
}
