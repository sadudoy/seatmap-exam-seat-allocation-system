package com.seatmap.model;

/**
 * Represents an invigilation assignment linking an exam, room, and teacher.
 */
public class RoomAssignment {

    private long id;
    private long examId;
    private long roomId;
    private long teacherId;

    public RoomAssignment() {
    }

    public RoomAssignment(long examId, long roomId, long teacherId) {
        this.examId = examId;
        this.roomId = roomId;
        this.teacherId = teacherId;
    }

    public RoomAssignment(long id, long examId, long roomId, long teacherId) {
        this.id = id;
        this.examId = examId;
        this.roomId = roomId;
        this.teacherId = teacherId;
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

    public long getRoomId() {
        return roomId;
    }

    public void setRoomId(long roomId) {
        this.roomId = roomId;
    }

    public long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(long teacherId) {
        this.teacherId = teacherId;
    }
}
