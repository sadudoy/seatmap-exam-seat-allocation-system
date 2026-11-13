package com.seatmap.dao;

import com.seatmap.config.DatabaseConnector;
import com.seatmap.exception.DataAccessException;
import com.seatmap.model.Room;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC Data Access Object for {@link Room} records.
 */
public class RoomDAO {

    public static final int INDEX_TOTAL_CAPACITY = 0;
    public static final int INDEX_ALLOCATED_SEATS = 1;

    public List<Room> getAll() {
        String sql = "SELECT id, room_number, capacity, rows, columns FROM rooms ORDER BY room_number";
        return JdbcSupport.queryList(sql, ps -> { }, this::mapRow);
    }

    public Optional<Room> getById(long id) {
        String sql = "SELECT id, room_number, capacity, rows, columns FROM rooms WHERE id = ?";
        return JdbcSupport.queryOptional(sql, ps -> ps.setLong(1, id), this::mapRow);
    }

    public Room add(Room room) {
        String sql = "INSERT INTO rooms (room_number, capacity, rows, columns) VALUES (?, ?, ?, ?)";
        long id = JdbcSupport.insertAndReturnId(sql, ps -> {
            ps.setString(1, room.getRoomNumber());
            ps.setInt(2, room.getCapacity());
            ps.setInt(3, room.getRows());
            ps.setInt(4, room.getColumns());
        });
        room.setId(id);
        return room;
    }

    public boolean update(Room room) {
        String sql = "UPDATE rooms SET room_number = ?, capacity = ?, rows = ?, columns = ? WHERE id = ?";
        int rows = JdbcSupport.executeUpdate(sql, ps -> {
            ps.setString(1, room.getRoomNumber());
            ps.setInt(2, room.getCapacity());
            ps.setInt(3, room.getRows());
            ps.setInt(4, room.getColumns());
            ps.setLong(5, room.getId());
        });
        return rows > 0;
    }

    public boolean delete(long id) {
        String sql = "DELETE FROM rooms WHERE id = ?";
        return JdbcSupport.executeUpdate(sql, ps -> ps.setLong(1, id)) > 0;
    }

    /**
     * Aggregates total capacity versus allocated seats for each room across all current allocations.
     *
     * @return map of room number to Integer[] array where:
     *         index 0 ({@link #INDEX_TOTAL_CAPACITY}) = Total Capacity
     *         index 1 ({@link #INDEX_ALLOCATED_SEATS}) = Allocated Seats
     */
    public Map<String, Integer[]> getRoomCapacityStats() {
        return getRoomCapacityStats(null);
    }

    /**
     * Aggregates total capacity versus allocated seats for each room for an optional examination.
     *
     * @param examId optional exam id; if null, counts all current allocations
     * @return map of room number to Integer[] array where:
     *         index 0 = Total Capacity
     *         index 1 = Allocated Seats
     */
    public Map<String, Integer[]> getRoomCapacityStats(Long examId) {
        String sql = examId == null
                ? """
                SELECT r.room_number, r.capacity, COUNT(a.id) AS allocated_count
                FROM rooms r
                LEFT JOIN allocations a ON r.id = a.room_id
                GROUP BY r.id, r.room_number, r.capacity
                ORDER BY r.room_number
                """
                : """
                SELECT r.room_number, r.capacity, COUNT(a.id) AS allocated_count
                FROM rooms r
                LEFT JOIN allocations a ON r.id = a.room_id AND a.exam_id = ?
                GROUP BY r.id, r.room_number, r.capacity
                ORDER BY r.room_number
                """;

        Map<String, Integer[]> stats = new LinkedHashMap<>();
        try (Connection conn = DatabaseConnector.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (examId != null) {
                ps.setLong(1, examId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String roomNumber = rs.getString("room_number");
                    int capacity = rs.getInt("capacity");
                    int allocated = rs.getInt("allocated_count");
                    stats.put(roomNumber, new Integer[]{capacity, allocated});
                }
            }
            return stats;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to fetch room capacity stats: " + ex.getMessage(), ex.getSQLState(), ex);
        }
    }

    private Room mapRow(ResultSet rs) throws SQLException {
        return new Room(
                rs.getLong("id"),
                rs.getString("room_number"),
                rs.getInt("capacity"),
                rs.getInt("rows"),
                rs.getInt("columns")
        );
    }
}
