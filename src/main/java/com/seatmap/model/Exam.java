package com.seatmap.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Represents an examination session and optional department/semester targeting criteria.
 */
public class Exam {

    private long id;
    private String subject;
    private LocalDate examDate;
    private LocalTime startTime;
    private String targetDepartment;
    private String targetSemester;

    public Exam() {
    }

    public Exam(String subject, LocalDate examDate, LocalTime startTime) {
        this.subject = subject;
        this.examDate = examDate;
        this.startTime = startTime;
    }

    public Exam(long id, String subject, LocalDate examDate, LocalTime startTime) {
        this.id = id;
        this.subject = subject;
        this.examDate = examDate;
        this.startTime = startTime;
    }

    public Exam(String subject, LocalDate examDate, LocalTime startTime,
                String targetDepartment, String targetSemester) {
        this.subject = subject;
        this.examDate = examDate;
        this.startTime = startTime;
        this.targetDepartment = targetDepartment;
        this.targetSemester = targetSemester;
    }

    public Exam(long id, String subject, LocalDate examDate, LocalTime startTime,
                String targetDepartment, String targetSemester) {
        this.id = id;
        this.subject = subject;
        this.examDate = examDate;
        this.startTime = startTime;
        this.targetDepartment = targetDepartment;
        this.targetSemester = targetSemester;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDate examDate) {
        this.examDate = examDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public String getTargetDepartment() {
        return targetDepartment;
    }

    public void setTargetDepartment(String targetDepartment) {
        this.targetDepartment = targetDepartment;
    }

    public String getTargetSemester() {
        return targetSemester;
    }

    public void setTargetSemester(String targetSemester) {
        this.targetSemester = targetSemester;
    }
}
