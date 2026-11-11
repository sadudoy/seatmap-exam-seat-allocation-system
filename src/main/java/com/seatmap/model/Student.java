package com.seatmap.model;

/**
 * Represents a student eligible for exam seat allocation.
 */
public class Student {

    private long id;
    private String rollNumber;
    private String name;
    private String department;
    private String semester;

    public Student() {
    }

    public Student(String rollNumber, String name, String department, String semester) {
        this.rollNumber = rollNumber;
        this.name = name;
        this.department = department;
        this.semester = semester;
    }

    public Student(long id, String rollNumber, String name, String department, String semester) {
        this.id = id;
        this.rollNumber = rollNumber;
        this.name = name;
        this.department = department;
        this.semester = semester;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = rollNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }
}
