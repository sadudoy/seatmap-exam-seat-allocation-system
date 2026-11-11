package com.seatmap.service;

import com.seatmap.model.Allocation;
import com.seatmap.model.Exam;
import com.seatmap.model.Room;
import com.seatmap.model.Student;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ExportService")
class ExportServiceTest {

    private final ExportService exportService = new ExportService();

    @Test
    @DisplayName("generates a valid PDF seating chart file for an exam and room")
    void generatesValidPdfFile(@TempDir Path tempDir) throws IOException {
        Exam exam = new Exam(10L, "Computer Networks", LocalDate.of(2026, 12, 5), LocalTime.of(10, 0));
        Room room = new Room(20L, "B-402", 4, 2, 2);

        Student s1 = new Student(1L, "CSE-01", "Alice", "CSE", "5");
        Student s2 = new Student(2L, "EEE-02", "Bob", "EEE", "5");
        Map<Long, Student> studentsById = Map.of(1L, s1, 2L, s2);

        List<Allocation> allocations = List.of(
                new Allocation(1L, 10L, 1L, 20L, 0, 0),
                new Allocation(2L, 10L, 2L, 20L, 0, 1)
        );

        Path outputPdf = tempDir.resolve("seating-chart.pdf");
        exportService.exportRoomSeatingChart(exam, room, allocations, studentsById, outputPdf);

        assertTrue(Files.exists(outputPdf));
        byte[] bytes = Files.readAllBytes(outputPdf);
        assertTrue(bytes.length > 500, "Generated PDF should be non-trivial in size");
        String header = new String(bytes, 0, Math.min(5, bytes.length), StandardCharsets.US_ASCII);
        assertTrue(header.startsWith("%PDF-"), "Output should start with PDF magic header");
    }
}
