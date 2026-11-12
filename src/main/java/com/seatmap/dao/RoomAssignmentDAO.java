package com.seatmap.dao;

import com.seatmap.model.RoomAssignment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * JDBC Data Access Object for {@link RoomAssignment} invigilation records.
 */
public class RoomAssignmentDAO {

    public RoomAssignment assign(RoomAssignment assignment) {
        String sql = "INSERT INTO room_assignments (exam_id, room_id, teacher_id) VALUES (?, ?, ?)";
        long id = JdbcSupport.insertAndReturnId(sql, ps -> {
            ps.setLong(1, assignment.getExamId());
            ps.setLong(2, assignment.getRoomId());
            ps.setLong(3, assignment.getTeacherId());
        });
        assignment.setId(id);
        return assignment;
    }

    public RoomAssignment assign(long examId, long roomId, long teacherId) {
        return assign(new RoomAssignment(examId, roomId, teacherId));
    }

    public List<RoomAssignment> getAll() {
        String sql = "SELECT id, exam_id, room_id, teacher_id FROM room_assignments ORDER BY exam_id, room_id, id";
        return JdbcSupport.queryList(sql, ps -> { }, this::mapRow);
    }

    public List<RoomAssignment> getByTeacherId(long teacherId) {
        String sql = """
                SELECT id, exam_id, room_id, teacher_id
                FROM room_assignments
                WHERE teacher_id = ?
                ORDER BY exam_id, room_id, id
                """;
        return JdbcSupport.queryList(sql, ps -> ps.setLong(1, teacherId), this::mapRow);
    }

    public boolean exists(long examId, long roomId, long teacherId) {
        String sql = "SELECT 1 FROM room_assignments WHERE exam_id = ? AND room_id = ? AND teacher_id = ?";
        return JdbcSupport.queryOptional(sql, ps -> {
            ps.setLong(1, examId);
            ps.setLong(2, roomId);
            ps.setLong(3, teacherId);
        }, rs -> rs.getInt(1)).isPresent();
    }

    public boolean deleteAssignment(long id) {
        String sql = "DELETE FROM room_assignments WHERE id = ?";
        return JdbcSupport.executeUpdate(sql, ps -> ps.setLong(1, id)) > 0;
    }

    private RoomAssignment mapRow(ResultSet rs) throws SQLException {
        return new RoomAssignment(
                rs.getLong("id"),
                rs.getLong("exam_id"),
                rs.getLong("room_id"),
                rs.getLong("teacher_id")
        );
    }
}
