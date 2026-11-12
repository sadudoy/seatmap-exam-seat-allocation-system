package com.seatmap.model;

/**
 * Represents a seat assignment linking a student, room, exam, and attendance status.
 */
public class Allocation {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PRESENT = "PRESENT";
    public static final String STATUS_ABSENT = "ABSENT";

    private long id;
    private long examId;
    private long studentId;
    private long roomId;
    private int seatRow;
    private int seatCol;
    private String status = STATUS_PENDING;

    public Allocation() {
    }

    public Allocation(long examId, long studentId, long roomId, int seatRow, int seatCol) {
        this(0L, examId, studentId, roomId, seatRow, seatCol, STATUS_PENDING);
    }

    public Allocation(long id, long examId, long studentId, long roomId, int seatRow, int seatCol) {
        this(id, examId, studentId, roomId, seatRow, seatCol, STATUS_PENDING);
    }

    public Allocation(long id, long examId, long studentId, long roomId,
                      int seatRow, int seatCol, String status) {
        this.id = id;
        this.examId = examId;
        this.studentId = studentId;
        this.roomId = roomId;
        this.seatRow = seatRow;
        this.seatCol = seatCol;
        this.status = (status == null || status.isBlank()) ? STATUS_PENDING : status;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = (status == null || status.isBlank()) ? STATUS_PENDING : status;
    }
}
