package com.seatmap.dao;

import com.seatmap.config.DatabaseConnector;
import com.seatmap.exception.DataAccessException;
import com.seatmap.model.Allocation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * JDBC Data Access Object for {@link Allocation} records.
 */
public class AllocationDAO {

    private static final Set<String> VALID_STATUSES = Set.of(
            Allocation.STATUS_PENDING,
            Allocation.STATUS_PRESENT,
            Allocation.STATUS_ABSENT
    );

    public List<Allocation> getByExamId(long examId) {
        String sql = """
                SELECT id, exam_id, student_id, room_id, seat_row, seat_col, status
                FROM allocations
                WHERE exam_id = ?
                ORDER BY room_id, seat_row, seat_col
                """;
        return JdbcSupport.queryList(sql, ps -> ps.setLong(1, examId), this::mapRow);
    }

    public List<Allocation> getByExamIdAndRoomId(long examId, long roomId) {
        String sql = """
                SELECT id, exam_id, student_id, room_id, seat_row, seat_col, status
                FROM allocations
                WHERE exam_id = ? AND room_id = ?
                ORDER BY seat_row, seat_col
                """;
        return JdbcSupport.queryList(sql, ps -> {
            ps.setLong(1, examId);
            ps.setLong(2, roomId);
        }, this::mapRow);
    }

    public int clearAllocationsForExam(long examId) {
        String sql = "DELETE FROM allocations WHERE exam_id = ?";
        return JdbcSupport.executeUpdate(sql, ps -> ps.setLong(1, examId));
    }

    public Allocation saveAllocation(Allocation allocation) {
        String status = normalizeStatus(allocation.getStatus());
        String sql = """
                INSERT INTO allocations (exam_id, student_id, room_id, seat_row, seat_col, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        long id = JdbcSupport.insertAndReturnId(sql, ps -> {
            ps.setLong(1, allocation.getExamId());
            ps.setLong(2, allocation.getStudentId());
            ps.setLong(3, allocation.getRoomId());
            ps.setInt(4, allocation.getSeatRow());
            ps.setInt(5, allocation.getSeatCol());
            ps.setString(6, status);
        });
        allocation.setId(id);
        allocation.setStatus(status);
        return allocation;
    }

    /**
     * Atomically replaces all allocations for {@code examId} in a single transaction.
     *
     * @param examId      target exam id
     * @param allocations allocations to persist
     */
    public void replaceAllocationsForExam(long examId, List<Allocation> allocations) {
        String deleteSql = "DELETE FROM allocations WHERE exam_id = ?";
        String insertSql = """
                INSERT INTO allocations (exam_id, student_id, room_id, seat_row, seat_col, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DatabaseConnector.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSql)) {
                    deleteStmt.setLong(1, examId);
                    deleteStmt.executeUpdate();
                }

                if (allocations != null && !allocations.isEmpty()) {
                    try (PreparedStatement insertStmt = connection.prepareStatement(
                            insertSql, Statement.RETURN_GENERATED_KEYS)) {
                        for (Allocation allocation : allocations) {
                            String status = normalizeStatus(allocation.getStatus());
                            insertStmt.setLong(1, allocation.getExamId());
                            insertStmt.setLong(2, allocation.getStudentId());
                            insertStmt.setLong(3, allocation.getRoomId());
                            insertStmt.setInt(4, allocation.getSeatRow());
                            insertStmt.setInt(5, allocation.getSeatCol());
                            insertStmt.setString(6, status);
                            insertStmt.addBatch();
                        }
                        insertStmt.executeBatch();
                        try (ResultSet keys = insertStmt.getGeneratedKeys()) {
                            int idx = 0;
                            while (keys.next() && idx < allocations.size()) {
                                allocations.get(idx++).setId(keys.getLong(1));
                            }
                        }
                    }
                }
                connection.commit();
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
            throw new DataAccessException(
                    "Transactional allocation save failed: " + ex.getMessage(), ex.getSQLState(), ex);
        }
    }

    public boolean updateSeat(long allocationId, int seatRow, int seatCol) {
        String sql = "UPDATE allocations SET seat_row = ?, seat_col = ? WHERE id = ?";
        int rows = JdbcSupport.executeUpdate(sql, ps -> {
            ps.setInt(1, seatRow);
            ps.setInt(2, seatCol);
            ps.setLong(3, allocationId);
        });
        return rows > 0;
    }

    public boolean updateSeat(long allocationId, long roomId, int seatRow, int seatCol) {
        String sql = "UPDATE allocations SET room_id = ?, seat_row = ?, seat_col = ? WHERE id = ?";
        int rows = JdbcSupport.executeUpdate(sql, ps -> {
            ps.setLong(1, roomId);
            ps.setInt(2, seatRow);
            ps.setInt(3, seatCol);
            ps.setLong(4, allocationId);
        });
        return rows > 0;
    }

    /**
     * Updates the attendance status ({@code PENDING}, {@code PRESENT}, or {@code ABSENT}) for an allocation.
     */
    public boolean updateAttendanceStatus(long allocationId, String status) {
        String normalized = normalizeStatus(status);
        String sql = "UPDATE allocations SET status = ? WHERE id = ?";
        int rows = JdbcSupport.executeUpdate(sql, ps -> {
            ps.setString(1, normalized);
            ps.setLong(2, allocationId);
        });
        return rows > 0;
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return Allocation.STATUS_PENDING;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid attendance status: " + status);
        }
        return normalized;
    }

    private Allocation mapRow(ResultSet rs) throws SQLException {
        return new Allocation(
                rs.getLong("id"),
                rs.getLong("exam_id"),
                rs.getLong("student_id"),
                rs.getLong("room_id"),
                rs.getInt("seat_row"),
                rs.getInt("seat_col"),
                rs.getString("status")
        );
    }
}
