package com.seatmap.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.seatmap.exception.ServiceException;
import com.seatmap.model.Allocation;
import com.seatmap.model.Room;
import com.seatmap.model.Student;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses uploaded student CSV files and generates CSV exports for students and allocations.
 */
public class CsvService {

    /**
     * Holds parsed students along with non-fatal warnings for skipped malformed rows.
     */
    public record CsvParseResult(List<Student> students, List<String> skippedRowWarnings) {
    }

    /**
     * Parses CSV text into a list of {@link Student} objects.
     * Expects columns: RollNumber, Name, Department, Semester (optional header row).
     */
    public List<Student> parseStudentsFromCsv(String csvContent) {
        if (csvContent == null || csvContent.isBlank()) {
            return List.of();
        }
        return parseStudentsWithReport(new StringReader(csvContent)).students();
    }

    /**
     * Parses a UTF-8 CSV file into a list of {@link Student} objects.
     */
    public List<Student> parseStudentsFromCsv(Path csvFile) {
        if (csvFile == null) {
            throw new IllegalArgumentException("CSV file path must not be null");
        }
        try (Reader reader = Files.newBufferedReader(csvFile, StandardCharsets.UTF_8)) {
            return parseStudentsWithReport(reader).students();
        } catch (IOException ex) {
            throw new ServiceException("Failed to read CSV file: " + csvFile, ex);
        }
    }

    /**
     * Parses CSV input from a {@link Reader} while gracefully skipping malformed rows.
     */
    public CsvParseResult parseStudentsWithReport(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("Reader must not be null");
        }

        List<Student> students = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try (CSVReader csvReader = new CSVReader(reader)) {
            int rowNumber = 0;
            int rollIdx = 0;
            int nameIdx = 1;
            int deptIdx = 2;
            int semIdx = 3;

            while (true) {
                rowNumber++;
                String[] row;
                try {
                    row = csvReader.readNext();
                } catch (CsvValidationException ex) {
                    warnings.add("Row " + rowNumber + ": malformed CSV syntax (" + ex.getMessage() + ")");
                    continue;
                }
                if (row == null) {
                    break;
                }
                if (isBlankRow(row)) {
                    continue;
                }

                if (students.isEmpty() && warnings.isEmpty() && isHeaderRow(row)) {
                    int[] mapped = resolveHeaderIndices(row);
                    rollIdx = mapped[0];
                    nameIdx = mapped[1];
                    deptIdx = mapped[2];
                    semIdx = mapped[3];
                    continue;
                }

                int maxIndex = Math.max(Math.max(rollIdx, nameIdx), Math.max(deptIdx, semIdx));
                if (row.length <= maxIndex) {
                    warnings.add("Row " + rowNumber + ": expected at least " + (maxIndex + 1)
                            + " columns, found " + row.length);
                    continue;
                }

                String roll = safeTrim(row[rollIdx]);
                String name = safeTrim(row[nameIdx]);
                String department = safeTrim(row[deptIdx]);
                String semester = safeTrim(row[semIdx]);

                if (roll.isEmpty() || name.isEmpty() || department.isEmpty() || semester.isEmpty()) {
                    warnings.add("Row " + rowNumber + ": missing required student field");
                    continue;
                }

                students.add(new Student(roll, name, department, semester));
            }
        } catch (IOException ex) {
            throw new ServiceException("Error reading CSV content: " + ex.getMessage(), ex);
        }

        return new CsvParseResult(students, warnings);
    }

    /**
     * Serializes a list of students to a CSV string.
     */
    public String exportStudentsToCsv(List<Student> students) {
        StringWriter writer = new StringWriter();
        writeStudentsCsv(students, writer);
        return writer.toString();
    }

    /**
     * Writes a list of students to a UTF-8 CSV file.
     */
    public void exportStudentsToCsv(List<Student> students, Path outputPath) {
        if (outputPath == null) {
            throw new IllegalArgumentException("Output path must not be null");
        }
        try (Writer writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writeStudentsCsv(students, writer);
        } catch (IOException ex) {
            throw new ServiceException("Failed to export students CSV to " + outputPath, ex);
        }
    }

    /**
     * Serializes exam allocations to a CSV string.
     */
    public String exportAllocationsToCsv(List<Allocation> allocations,
                                         Map<Long, Student> studentsById,
                                         Map<Long, Room> roomsById) {
        StringWriter writer = new StringWriter();
        writeAllocationsCsv(allocations, studentsById, roomsById, writer);
        return writer.toString();
    }

    /**
     * Writes exam allocations to a UTF-8 CSV file.
     */
    public void exportAllocationsToCsv(List<Allocation> allocations,
                                       Map<Long, Student> studentsById,
                                       Map<Long, Room> roomsById,
                                       Path outputPath) {
        if (outputPath == null) {
            throw new IllegalArgumentException("Output path must not be null");
        }
        try (Writer writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writeAllocationsCsv(allocations, studentsById, roomsById, writer);
        } catch (IOException ex) {
            throw new ServiceException("Failed to export allocations CSV to " + outputPath, ex);
        }
    }

    private void writeStudentsCsv(List<Student> students, Writer target) {
        try (CSVWriter csvWriter = new CSVWriter(target)) {
            csvWriter.writeNext(new String[]{"RollNumber", "Name", "Department", "Semester"});
            if (students != null) {
                for (Student student : students) {
                    csvWriter.writeNext(new String[]{
                            nullToEmpty(student.getRollNumber()),
                            nullToEmpty(student.getName()),
                            nullToEmpty(student.getDepartment()),
                            nullToEmpty(student.getSemester())
                    });
                }
            }
        } catch (IOException ex) {
            throw new ServiceException("Failed to write students CSV: " + ex.getMessage(), ex);
        }
    }

    private void writeAllocationsCsv(List<Allocation> allocations,
                                     Map<Long, Student> studentsById,
                                     Map<Long, Room> roomsById,
                                     Writer target) {
        try (CSVWriter csvWriter = new CSVWriter(target)) {
            csvWriter.writeNext(new String[]{
                    "Room", "SeatRow", "SeatCol", "RollNumber", "StudentName", "Department", "Semester"
            });
            if (allocations != null) {
                for (Allocation allocation : allocations) {
                    Room room = roomsById == null ? null : roomsById.get(allocation.getRoomId());
                    Student student = studentsById == null ? null : studentsById.get(allocation.getStudentId());
                    csvWriter.writeNext(new String[]{
                            room != null ? nullToEmpty(room.getRoomNumber()) : String.valueOf(allocation.getRoomId()),
                            String.valueOf(allocation.getSeatRow()),
                            String.valueOf(allocation.getSeatCol()),
                            student != null ? nullToEmpty(student.getRollNumber()) : "",
                            student != null ? nullToEmpty(student.getName()) : "",
                            student != null ? nullToEmpty(student.getDepartment()) : "",
                            student != null ? nullToEmpty(student.getSemester()) : ""
                    });
                }
            }
        } catch (IOException ex) {
            throw new ServiceException("Failed to write allocations CSV: " + ex.getMessage(), ex);
        }
    }

    private static boolean isBlankRow(String[] row) {
        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHeaderRow(String[] row) {
        for (String cell : row) {
            String normalized = normalizeHeader(cell);
            if (normalized.equals("rollnumber") || normalized.equals("roll")) {
                return true;
            }
        }
        return false;
    }

    private static int[] resolveHeaderIndices(String[] headerRow) {
        int rollIdx = 0;
        int nameIdx = 1;
        int deptIdx = 2;
        int semIdx = 3;

        for (int i = 0; i < headerRow.length; i++) {
            String header = normalizeHeader(headerRow[i]);
            switch (header) {
                case "rollnumber", "roll" -> rollIdx = i;
                case "name", "studentname" -> nameIdx = i;
                case "department", "dept" -> deptIdx = i;
                case "semester", "sem" -> semIdx = i;
                default -> {
                    // Ignore unknown extra columns.
                }
            }
        }
        return new int[]{rollIdx, nameIdx, deptIdx, semIdx};
    }

    private static String normalizeHeader(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\uFEFF", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-]+", "");
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
