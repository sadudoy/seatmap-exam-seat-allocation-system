package com.seatmap.util;

import com.seatmap.dao.AdminDAO;
import com.seatmap.dao.RoomDAO;
import com.seatmap.dao.StudentDAO;
import com.seatmap.model.Room;
import com.seatmap.model.Student;
import com.seatmap.service.AuthService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Seeds default administrator and realistic bulk demo data when database tables are empty.
 */
public final class DataSeeder {

    public static final int DEFAULT_STUDENT_COUNT = 400;

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

    private final AdminDAO adminDAO;
    private final StudentDAO studentDAO;
    private final RoomDAO roomDAO;
    private final AuthService authService;

    public DataSeeder() {
        this(new AdminDAO(), new StudentDAO(), new RoomDAO());
    }

    public DataSeeder(AdminDAO adminDAO, StudentDAO studentDAO, RoomDAO roomDAO) {
        this.adminDAO = adminDAO;
        this.studentDAO = studentDAO;
        this.roomDAO = roomDAO;
        this.authService = new AuthService(adminDAO);
    }

    /**
     * Convenience entrypoint using default DAOs.
     */
    public static void seedIfEmpty() {
        new DataSeeder().seed();
    }

    /**
     * Seeds default admin, bulk students, and rooms only when their respective tables are empty.
     */
    public void seed() {
        seedDefaultAdmin();
        seedBulkStudents();
        seedRooms();
    }

    private void seedDefaultAdmin() {
        if (adminDAO.count() == 0) {
            authService.registerAdmin("admin", "admin123");
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

        // Initial 2 rooms (41 seats) + 14 larger halls = 16 rooms (557 total seats)
        List<Room> rooms = List.of(
                new Room("Hall-101", 25, 5, 5),
                new Room("Hall-102", 16, 4, 4),
                new Room("Hall-201", 36, 6, 6),
                new Room("Hall-202", 36, 6, 6),
                new Room("Hall-203", 36, 6, 6),
                new Room("Hall-204", 36, 6, 6),
                new Room("Hall-205", 36, 6, 6),
                new Room("Hall-206", 36, 6, 6),
                new Room("Hall-301", 40, 8, 5),
                new Room("Hall-302", 40, 8, 5),
                new Room("Hall-303", 40, 8, 5),
                new Room("Hall-304", 40, 8, 5),
                new Room("Hall-305", 40, 8, 5),
                new Room("Hall-306", 40, 8, 5),
                new Room("Hall-401", 30, 6, 5),
                new Room("Hall-402", 30, 6, 5)
        );

        for (Room room : rooms) {
            roomDAO.add(room);
        }
    }
}
