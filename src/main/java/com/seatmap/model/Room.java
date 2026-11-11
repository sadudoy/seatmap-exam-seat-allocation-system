package com.seatmap.model;

/**
 * Represents an examination room with a seating grid defined by rows and columns.
 */
public class Room {

    private long id;
    private String roomNumber;
    private int capacity;
    private int rows;
    private int columns;

    public Room() {
    }

    public Room(String roomNumber, int capacity, int rows, int columns) {
        this.roomNumber = roomNumber;
        this.capacity = capacity;
        this.rows = rows;
        this.columns = columns;
    }

    public Room(long id, String roomNumber, int capacity, int rows, int columns) {
        this.id = id;
        this.roomNumber = roomNumber;
        this.capacity = capacity;
        this.rows = rows;
        this.columns = columns;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        this.columns = columns;
    }
}
