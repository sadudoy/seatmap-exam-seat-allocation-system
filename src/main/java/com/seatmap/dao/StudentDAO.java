package com.seatmap.dao;

import com.seatmap.config.DatabaseConnector;
import com.seatmap.exception.DataAccessException;
import com.seatmap.model.Student;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC Data Access Object for {@link Student} records.
 */
public class StudentDAO {

    public List<Student> getAll() {
        String sql = "SELECT id, roll_number, name, department, semester FROM students ORDER BY roll_number";
        return JdbcSupport.queryList(sql, ps -> { }, this::mapRow);
    }

    public Optional<Student> getById(long id) {
        String sql = "SELECT id, roll_number, name, department, semester FROM students WHERE id = ?";
        return JdbcSupport.queryOptional(sql, ps -> ps.setLong(1, id), this::mapRow);
    }

    public Student add(Student student) {
        String sql = "INSERT INTO students (roll_number, name, department, semester) VALUES (?, ?, ?, ?)";
        long id = JdbcSupport.insertAndReturnId(sql, ps -> {
            ps.setString(1, student.getRollNumber());
            ps.setString(2, student.getName());
            ps.setString(3, student.getDepartment());
            ps.setString(4, student.getSemester());
        });
        student.setId(id);
        return student;
    }

    /**
     * Inserts a batch of students within a single database transaction.
     *
     * @param students students to insert
     * @return number of inserted rows
     */
    public int addAll(List<Student> students) {
        if (students == null || students.isEmpty()) {
            return 0;
        }
        String sql = "INSERT INTO students (roll_number, name, department, semester) VALUES (?, ?, ?, ?)";
        try (Connection connection = DatabaseConnector.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                for (Student student : students) {
                    ps.setString(1, student.getRollNumber());
                    ps.setString(2, student.getName());
                    ps.setString(3, student.getDepartment());
                    ps.setString(4, student.getSemester());
                    ps.addBatch();
                }
                int[] counts = ps.executeBatch();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    int idx = 0;
                    while (keys.next() && idx < students.size()) {
                        students.get(idx++).setId(keys.getLong(1));
                    }
                }
                connection.commit();
                return counts.length;
            } catch (SQLException ex) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                throw ex;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Bulk student insert failed: " + ex.getMessage(), ex.getSQLState(), ex);
        }
    }

    public boolean update(Student student) {
        String sql = "UPDATE students SET roll_number = ?, name = ?, department = ?, semester = ? WHERE id = ?";
        int rows = JdbcSupport.executeUpdate(sql, ps -> {
            ps.setString(1, student.getRollNumber());
            ps.setString(2, student.getName());
            ps.setString(3, student.getDepartment());
            ps.setString(4, student.getSemester());
            ps.setLong(5, student.getId());
        });
        return rows > 0;
    }

    public boolean delete(long id) {
        String sql = "DELETE FROM students WHERE id = ?";
        return JdbcSupport.executeUpdate(sql, ps -> ps.setLong(1, id)) > 0;
    }

    public List<Student> getByDepartmentAndSemester(String department, String semester) {
        String sql = """
                SELECT id, roll_number, name, department, semester
                FROM students
                WHERE LOWER(department) = LOWER(?) AND LOWER(semester) = LOWER(?)
                ORDER BY roll_number
                """;
        return JdbcSupport.queryList(sql, ps -> {
            ps.setString(1, department);
            ps.setString(2, semester);
        }, this::mapRow);
    }

    public List<Student> getBySemester(String semester) {
        String sql = """
                SELECT id, roll_number, name, department, semester
                FROM students
                WHERE LOWER(semester) = LOWER(?)
                ORDER BY department, roll_number
                """;
        return JdbcSupport.queryList(sql, ps -> ps.setString(1, semester), this::mapRow);
    }

    public List<Student> searchByName(String nameQuery) {
        String pattern = "%" + JdbcSupport.escapeLike(nameQuery == null ? "" : nameQuery.trim()) + "%";
        String sql = """
                SELECT id, roll_number, name, department, semester
                FROM students
                WHERE name ILIKE ? ESCAPE '\\'
                ORDER BY roll_number
                """;
        return JdbcSupport.queryList(sql, ps -> ps.setString(1, pattern), this::mapRow);
    }

    /**
     * Fetches students filtered by optional lists of departments and numeric semesters.
     * Passing {@code null} or an empty list for either parameter skips filtering on that dimension.
     */
    public List<Student> getFilteredStudents(List<String> depts, List<Integer> sems) {
        List<String> normalizedDepts = depts == null ? List.of() : depts.stream()
                .filter(d -> d != null && !d.isBlank())
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        List<String> normalizedSems = sems == null ? List.of() : sems.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .distinct()
                .toList();

        StringBuilder sql = new StringBuilder(
                "SELECT id, roll_number, name, department, semester FROM students WHERE 1=1");
        if (!normalizedDepts.isEmpty()) {
            sql.append(" AND LOWER(department) = ANY(?)");
        }
        if (!normalizedSems.isEmpty()) {
            sql.append(" AND LOWER(semester) = ANY(?)");
        }
        sql.append(" ORDER BY roll_number");

        try (Connection connection = DatabaseConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int paramIdx = 1;
            if (!normalizedDepts.isEmpty()) {
                Array deptArray = connection.createArrayOf("text", normalizedDepts.toArray(String[]::new));
                statement.setArray(paramIdx++, deptArray);
            }
            if (!normalizedSems.isEmpty()) {
                Array semArray = connection.createArrayOf("text", normalizedSems.toArray(String[]::new));
                statement.setArray(paramIdx++, semArray);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<Student> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Filtered student query failed: " + ex.getMessage(), ex.getSQLState(), ex);
        }
    }

    /**
     * Aggregates students by department for analytics visualization.
     *
     * @return map of department name to student count, sorted alphabetically
     */
    public Map<String, Integer> getDepartmentCounts() {
        String sql = """
                SELECT department, COUNT(*) AS dept_count
                FROM students
                WHERE department IS NOT NULL AND TRIM(department) <> ''
                GROUP BY department
                ORDER BY department
                """;
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String dept = rs.getString("department");
                int count = rs.getInt("dept_count");
                counts.put(dept != null ? dept.trim() : "Unknown", count);
            }
            return counts;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to fetch department counts: " + ex.getMessage(), ex.getSQLState(), ex);
        }
    }

    private Student mapRow(ResultSet rs) throws SQLException {
        return new Student(
                rs.getLong("id"),
                rs.getString("roll_number"),
                rs.getString("name"),
                rs.getString("department"),
                rs.getString("semester")
        );
    }
}
