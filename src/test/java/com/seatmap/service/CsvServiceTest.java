package com.seatmap.service;

import com.seatmap.model.Student;
import com.seatmap.service.CsvService.CsvParseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CsvService")
class CsvServiceTest {

    private final CsvService csvService = new CsvService();

    @Test
    @DisplayName("parses valid CSV string with header into Student list")
    void parsesValidCsvString() {
        String csv = """
                RollNumber,Name,Department,Semester
                CSE-101,Alice Rahman,CSE,4
                "EEE-202","Bob, Jr.",EEE,4
                ME-303,Charlie Karim,ME,6
                """;

        List<Student> students = csvService.parseStudentsFromCsv(csv);

        assertEquals(3, students.size());
        assertEquals("CSE-101", students.get(0).getRollNumber());
        assertEquals("Alice Rahman", students.get(0).getName());
        assertEquals("CSE", students.get(0).getDepartment());
        assertEquals("4", students.get(0).getSemester());
        assertEquals("Bob, Jr.", students.get(1).getName());
    }

    @Test
    @DisplayName("skips malformed rows gracefully without throwing")
    void skipsMalformedRowsGracefully() {
        String csv = """
                RollNumber,Name,Department,Semester
                CSE-001,Valid Student,CSE,3

                ONLY_TWO_COLUMNS,Bad Row
                CSE-002,,CSE,3
                EEE-003,Second Valid,EEE,3
                """;

        CsvParseResult result = csvService.parseStudentsWithReport(new StringReader(csv));

        assertEquals(2, result.students().size());
        assertEquals(2, result.skippedRowWarnings().size());
        assertEquals("CSE-001", result.students().get(0).getRollNumber());
        assertEquals("EEE-003", result.students().get(1).getRollNumber());
    }

    @Test
    @DisplayName("exports students to CSV and parses back identically")
    void exportsAndRoundTripsStudents() {
        List<Student> input = List.of(
                new Student("R-1", "Dana", "CSE", "2"),
                new Student("R-2", "Evan", "BBA", "2")
        );

        String exported = csvService.exportStudentsToCsv(input);
        List<Student> reparsed = csvService.parseStudentsFromCsv(exported);

        assertTrue(exported.contains("RollNumber"));
        assertEquals(2, reparsed.size());
        assertEquals("Dana", reparsed.get(0).getName());
        assertEquals("BBA", reparsed.get(1).getDepartment());
    }
}
