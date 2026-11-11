package com.seatmap.dao;

import com.seatmap.model.Room;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * JDBC Data Access Object for {@link Room} records.
 */
public class RoomDAO {

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
