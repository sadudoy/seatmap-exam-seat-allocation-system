package com.seatmap.dao;

import com.seatmap.model.Exam;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * JDBC Data Access Object for {@link Exam} records.
 */
public class ExamDAO {

    public List<Exam> getAll() {
        String sql = "SELECT id, subject, exam_date, start_time FROM exams ORDER BY exam_date, start_time, id";
        return JdbcSupport.queryList(sql, ps -> { }, this::mapRow);
    }

    public Optional<Exam> getById(long id) {
        String sql = "SELECT id, subject, exam_date, start_time FROM exams WHERE id = ?";
        return JdbcSupport.queryOptional(sql, ps -> ps.setLong(1, id), this::mapRow);
    }

    public Exam add(Exam exam) {
        String sql = "INSERT INTO exams (subject, exam_date, start_time) VALUES (?, ?, ?)";
        long id = JdbcSupport.insertAndReturnId(sql, ps -> {
            ps.setString(1, exam.getSubject());
            ps.setObject(2, exam.getExamDate());
            ps.setObject(3, exam.getStartTime());
        });
        exam.setId(id);
        return exam;
    }

    public boolean update(Exam exam) {
        String sql = "UPDATE exams SET subject = ?, exam_date = ?, start_time = ? WHERE id = ?";
        int rows = JdbcSupport.executeUpdate(sql, ps -> {
            ps.setString(1, exam.getSubject());
            ps.setObject(2, exam.getExamDate());
            ps.setObject(3, exam.getStartTime());
            ps.setLong(4, exam.getId());
        });
        return rows > 0;
    }

    public boolean delete(long id) {
        String sql = "DELETE FROM exams WHERE id = ?";
        return JdbcSupport.executeUpdate(sql, ps -> ps.setLong(1, id)) > 0;
    }

    private Exam mapRow(ResultSet rs) throws SQLException {
        return new Exam(
                rs.getLong("id"),
                rs.getString("subject"),
                rs.getObject("exam_date", LocalDate.class),
                rs.getObject("start_time", LocalTime.class)
        );
    }
}
