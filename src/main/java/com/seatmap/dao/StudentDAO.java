package com.seatmap.dao;

import com.seatmap.config.DatabaseConnector;
import com.seatmap.exception.DataAccessException;
import com.seatmap.model.Student;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
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
