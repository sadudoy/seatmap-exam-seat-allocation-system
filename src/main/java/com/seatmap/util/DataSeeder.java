package com.seatmap.util;

import com.seatmap.dao.AllocationDAO;
import com.seatmap.dao.ExamDAO;
import com.seatmap.dao.RoomAssignmentDAO;
import com.seatmap.dao.RoomDAO;
import com.seatmap.dao.StudentDAO;
import com.seatmap.dao.UserDAO;
import com.seatmap.model.Exam;
import com.seatmap.model.Room;
import com.seatmap.model.Student;
import com.seatmap.model.User;
import com.seatmap.service.AllocationService;
import com.seatmap.service.AuthService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Seeds default RBAC users, exam, rooms, students, invigilation assignment, and initial allocations.
 */
public final class DataSeeder {

    public static final int DEFAULT_STUDENT_COUNT = 300;

    private static final String[] FIRST_NAMES = {
            "Aarav", "Adnan", "Aisha", "Alice", "Anika", "Arif", "Ayesha", "Bob",
            "Carol", "David", "Eva", "Farhan", "Fariha", "Hasan", "Imran", "Jannat",
            "Karim", "Labiba", "Mahir", "Maliha", "Nabil", "Nadia", "Nafis", "Nusrat",
            "Omar", "Rafiq", "Raisa", "Sadia", "Sami", "Tahmid", "Tamim", "Zara"
    };

    private static final String[] LAST_NAMES = {
            "Ahmed", "Akter", "Alam", "Ali", "Begum", "Bhuiyan", "Chowdhury", "Das",
            "Haque", "Hasan", "Hossain", "Islam", "Kabir", "Karim", "Khan", "Mahmud",
            "Miah", "Noor", "Rahman", "Roy", "Sarkar", "Siddiqui", "Talukder", "Uddin"
    };

    private static final String[] DEPARTMENTS = {"CSE", "EEE", "ME", "CE"};
    private static final String[] SEMESTERS = {"1", "2", "3", "4", "5", "6", "7", "8"};

    private final UserDAO userDAO;
    private final StudentDAO studentDAO;
    private final RoomDAO roomDAO;
    private final ExamDAO examDAO;
    private final RoomAssignmentDAO roomAssignmentDAO;
    private final AllocationDAO allocationDAO;
    private final AuthService authService;
    private final AllocationService allocationService;

    public DataSeeder() {
        this(new UserDAO(), new StudentDAO(), new RoomDAO(), new ExamDAO(),
                new RoomAssignmentDAO(), new AllocationDAO());
    }

    public DataSeeder(UserDAO userDAO, StudentDAO studentDAO, RoomDAO roomDAO, ExamDAO examDAO,
                      RoomAssignmentDAO roomAssignmentDAO, AllocationDAO allocationDAO) {
        this.userDAO = userDAO;
        this.studentDAO = studentDAO;
        this.roomDAO = roomDAO;
        this.examDAO = examDAO;
        this.roomAssignmentDAO = roomAssignmentDAO;
        this.allocationDAO = allocationDAO;
        this.authService = new AuthService(userDAO);
        this.allocationService = new AllocationService(studentDAO, roomDAO, allocationDAO);
    }

    /**
     * Convenience entrypoint using default DAOs.
     */
    public static void seedIfEmpty() {
        new DataSeeder().seed();
    }

    /**
     * Seeds default users, students, rooms, exam, and initial teacher assignment if empty.
     */
    public void seed() {
        seedDefaultUsers();
        seedBulkStudents();
        seedRooms();
        boolean examNewlySeeded = seedDefaultExam();
        seedDefaultAssignmentAndAllocations(examNewlySeeded);
    }

    private void seedDefaultUsers() {
        if (userDAO.count() == 0) {
            authService.registerUser("admin", "admin123", "Master Administrator", User.ROLE_MASTER_ADMIN);
            authService.registerUser("teacher1", "teacher123", "Prof. Sarah Ahmed", User.ROLE_TEACHER);
        }
    }

    private void seedBulkStudents() {
        if (!studentDAO.getAll().isEmpty()) {
            return;
        }

        Random random = new Random(2026L);
        Map<String, Integer> deptCounters = new HashMap<>();
        List<Student> students = new ArrayList<>(DEFAULT_STUDENT_COUNT);

        for (int i = 0; i < DEFAULT_STUDENT_COUNT; i++) {
            String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
            String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
            String fullName = firstName + " " + lastName;

            String dept = DEPARTMENTS[random.nextInt(DEPARTMENTS.length)];
            String semester = SEMESTERS[random.nextInt(SEMESTERS.length)];

            int seq = deptCounters.merge(dept, 1, Integer::sum);
            String rollNumber = String.format("2024-%s-%03d", dept, seq);

            students.add(new Student(rollNumber, fullName, dept, semester));
        }

        studentDAO.addAll(students);
    }

    private void seedRooms() {
        if (!roomDAO.getAll().isEmpty()) {
            return;
        }

        // 3 rooms totaling 320 seats (sufficient for 300 students with anti-cheating spacing)
        roomDAO.add(new Room("Hall-101", 110, 10, 11));
        roomDAO.add(new Room("Hall-102", 110, 10, 11));
        roomDAO.add(new Room("Hall-103", 100, 10, 10));
    }

    private boolean seedDefaultExam() {
        if (!examDAO.getAll().isEmpty()) {
            return false;
        }
        examDAO.add(new Exam(
                "Engineering Final Examination",
                LocalDate.of(2026, 12, 15),
                LocalTime.of(9, 30)
        ));
        return true;
    }

    private void seedDefaultAssignmentAndAllocations(boolean examNewlySeeded) {
        List<Exam> exams = examDAO.getAll();
        List<Room> rooms = roomDAO.getAll();
        Optional<User> teacher1 = userDAO.getByUsername("teacher1");

        if (exams.isEmpty() || rooms.isEmpty() || teacher1.isEmpty()) {
            return;
        }

        Exam primaryExam = exams.get(0);
        Room room1 = rooms.get(0);

        if (roomAssignmentDAO.getAll().isEmpty()) {
            roomAssignmentDAO.assign(primaryExam.getId(), room1.getId(), teacher1.get().getId());
        }

        if (examNewlySeeded && allocationDAO.getByExamId(primaryExam.getId()).isEmpty()
                && !studentDAO.getAll().isEmpty()) {
            allocationService.allocateAndSave(primaryExam, "ALL", "");
        }
    }
}
