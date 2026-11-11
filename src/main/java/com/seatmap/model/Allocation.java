package com.seatmap.model;

/**
 * Represents a seat assignment linking a student and room for a specific exam.
 */
public class Allocation {

    private long id;
    private long examId;
    private long studentId;
    private long roomId;
    private int seatRow;
    private int seatCol;

    public Allocation() {
    }

    public Allocation(long examId, long studentId, long roomId, int seatRow, int seatCol) {
        this.examId = examId;
        this.studentId = studentId;
        this.roomId = roomId;
        this.seatRow = seatRow;
        this.seatCol = seatCol;
    }

    public Allocation(long id, long examId, long studentId, long roomId, int seatRow, int seatCol) {
        this.id = id;
        this.examId = examId;
        this.studentId = studentId;
        this.roomId = roomId;
        this.seatRow = seatRow;
        this.seatCol = seatCol;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getExamId() {
        return examId;
    }

    public void setExamId(long examId) {
        this.examId = examId;
    }

    public long getStudentId() {
        return studentId;
    }

    public void setStudentId(long studentId) {
        this.studentId = studentId;
    }

    public long getRoomId() {
        return roomId;
    }

    public void setRoomId(long roomId) {
        this.roomId = roomId;
    }

    public int getSeatRow() {
        return seatRow;
    }

    public void setSeatRow(int seatRow) {
        this.seatRow = seatRow;
    }

    public int getSeatCol() {
        return seatCol;
    }

    public void setSeatCol(int seatCol) {
        this.seatCol = seatCol;
    }
}
