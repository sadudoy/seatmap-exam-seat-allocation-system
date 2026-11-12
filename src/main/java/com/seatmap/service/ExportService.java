package com.seatmap.service;

import com.seatmap.dao.AllocationDAO;
import com.seatmap.dao.ExamDAO;
import com.seatmap.dao.RoomDAO;
import com.seatmap.dao.StudentDAO;
import com.seatmap.exception.DataAccessException;
import com.seatmap.exception.ServiceException;
import com.seatmap.model.Allocation;
import com.seatmap.model.Exam;
import com.seatmap.model.Room;
import com.seatmap.model.Student;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates formatted PDF seating charts using Apache PDFBox.
 */
public class ExportService {

    private static final float MARGIN = 36f;
    private static final float HEADER_HEIGHT = 70f;
    private static final float MIN_ROW_HEIGHT = 48f;

    private final ExamDAO examDAO;
    private final RoomDAO roomDAO;
    private final AllocationDAO allocationDAO;
    private final StudentDAO studentDAO;

    public ExportService() {
        this(new ExamDAO(), new RoomDAO(), new AllocationDAO(), new StudentDAO());
    }

    public ExportService(ExamDAO examDAO, RoomDAO roomDAO,
                         AllocationDAO allocationDAO, StudentDAO studentDAO) {
        this.examDAO = examDAO;
        this.roomDAO = roomDAO;
        this.allocationDAO = allocationDAO;
        this.studentDAO = studentDAO;
    }

    /**
     * Fetches exam, room, and allocation details from the database and writes a PDF seating chart.
     */
    public void exportRoomSeatingChart(long examId, long roomId, Path outputPath) {
        try {
            Exam exam = examDAO.getById(examId)
                    .orElseThrow(() -> new ServiceException("Exam not found with id: " + examId));
            Room room = roomDAO.getById(roomId)
                    .orElseThrow(() -> new ServiceException("Room not found with id: " + roomId));
            List<Allocation> allocations = allocationDAO.getByExamIdAndRoomId(examId, roomId);

            Map<Long, Student> studentsById = new HashMap<>();
            for (Student student : studentDAO.getAll()) {
                studentsById.put(student.getId(), student);
            }

            exportRoomSeatingChart(exam, room, allocations, studentsById, outputPath);
        } catch (DataAccessException ex) {
            throw new ServiceException("Failed to load data for PDF export: " + ex.getMessage(), ex);
        }
    }

    /**
     * Writes a formatted Teacher Attendance Sheet PDF for the given exam and room to {@code outputPath}.
     */
    public void exportAttendanceSheet(long examId, long roomId, Path outputPath) {
        try {
            Exam exam = examDAO.getById(examId)
                    .orElseThrow(() -> new ServiceException("Exam not found with id: " + examId));
            Room room = roomDAO.getById(roomId)
                    .orElseThrow(() -> new ServiceException("Room not found with id: " + roomId));
            List<Allocation> allocations = allocationDAO.getByExamIdAndRoomId(examId, roomId);

            Map<Long, Student> studentsById = new HashMap<>();
            for (Student student : studentDAO.getAll()) {
                studentsById.put(student.getId(), student);
            }

            exportAttendanceSheet(exam, room, allocations, studentsById, outputPath);
        } catch (DataAccessException ex) {
            throw new ServiceException("Failed to load data for attendance sheet PDF: " + ex.getMessage(), ex);
        }
    }

    /**
     * Writes a Teacher Attendance Sheet PDF from pre-loaded data to {@code outputPath}.
     */
    public void exportAttendanceSheet(Exam exam, Room room, List<Allocation> allocations,
                                      Map<Long, Student> studentsById, Path outputPath) {
        if (outputPath == null) {
            throw new IllegalArgumentException("Output path must not be null");
        }
        byte[] pdfBytes = generateAttendanceSheetPdf(exam, room, allocations, studentsById);
        writePdfBytes(pdfBytes, outputPath);
    }

    /**
     * Generates a formatted Teacher Attendance Sheet PDF listing students with checkboxes and signature lines.
     */
    public byte[] generateAttendanceSheetPdf(Exam exam, Room room, List<Allocation> allocations,
                                             Map<Long, Student> studentsById) {
        if (exam == null || room == null) {
            throw new IllegalArgumentException("Exam and Room must not be null");
        }

        List<Allocation> safeAllocations = allocations == null ? List.of() : allocations;
        long presentCount = safeAllocations.stream()
                .filter(a -> Allocation.STATUS_PRESENT.equalsIgnoreCase(a.getStatus()))
                .count();
        long absentCount = safeAllocations.stream()
                .filter(a -> Allocation.STATUS_ABSENT.equalsIgnoreCase(a.getStatus()))
                .count();
        long pendingCount = safeAllocations.size() - presentCount - absentCount;

        PDRectangle pageSize = PDRectangle.A4;
        float rowHeight = 22f;
        float tableHeaderHeight = 24f;
        float topHeaderSpace = 88f;
        float usableHeight = pageSize.getHeight() - (2 * MARGIN) - topHeaderSpace - tableHeaderHeight;
        int rowsPerPage = Math.max(1, (int) Math.floor(usableHeight / rowHeight));
        int totalPages = Math.max(1, (int) Math.ceil((double) safeAllocations.size() / rowsPerPage));

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (int pageIdx = 0; pageIdx < totalPages; pageIdx++) {
                int start = pageIdx * rowsPerPage;
                int end = Math.min(safeAllocations.size(), start + rowsPerPage);

                PDPage page = new PDPage(pageSize);
                document.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    drawAttendanceHeader(cs, pageSize, exam, room, safeAllocations.size(),
                            presentCount, absentCount, pendingCount, pageIdx + 1, totalPages);
                    drawAttendanceTable(cs, pageSize, safeAllocations, studentsById,
                            start, end, rowHeight, tableHeaderHeight, topHeaderSpace);
                }
            }

            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ServiceException("Failed to generate attendance PDF: " + ex.getMessage(), ex);
        }
    }

    /**
     * Writes a formatted PDF seating chart for the given exam and room to {@code outputPath}.
     */
    public void exportRoomSeatingChart(Exam exam, Room room, List<Allocation> allocations,
                                       Map<Long, Student> studentsById, Path outputPath) {
        if (outputPath == null) {
            throw new IllegalArgumentException("Output path must not be null");
        }
        byte[] pdfBytes = generateRoomSeatingChartPdf(exam, room, allocations, studentsById);
        writePdfBytes(pdfBytes, outputPath);
    }

    private void writePdfBytes(byte[] pdfBytes, Path outputPath) {
        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(outputPath, pdfBytes);
        } catch (IOException ex) {
            throw new ServiceException("Failed to write PDF file: " + outputPath, ex);
        }
    }

    /**
     * Generates a PDF seating chart as an in-memory byte array.
     */
    public byte[] generateRoomSeatingChartPdf(Exam exam, Room room, List<Allocation> allocations,
                                              Map<Long, Student> studentsById) {
        if (exam == null || room == null) {
            throw new IllegalArgumentException("Exam and Room must not be null");
        }
        if (room.getRows() <= 0 || room.getColumns() <= 0) {
            throw new IllegalArgumentException("Room dimensions must be positive");
        }

        Student[][] grid = buildSeatGrid(room, allocations, studentsById);
        PDRectangle pageSize = room.getColumns() > 4
                ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
                : PDRectangle.A4;

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            float usableWidth = pageSize.getWidth() - (2 * MARGIN);
            float usableGridHeight = pageSize.getHeight() - (2 * MARGIN) - HEADER_HEIGHT;
            int rowsPerPage = Math.max(1, (int) Math.floor(usableGridHeight / MIN_ROW_HEIGHT));
            float colWidth = usableWidth / room.getColumns();

            for (int startRow = 0; startRow < room.getRows(); startRow += rowsPerPage) {
                int endRow = Math.min(room.getRows(), startRow + rowsPerPage);
                int pageRowCount = endRow - startRow;
                float rowHeight = Math.min(82f, usableGridHeight / pageRowCount);

                PDPage page = new PDPage(pageSize);
                document.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    drawHeader(cs, pageSize, exam, room, startRow, endRow);
                    drawSeatGrid(cs, pageSize, room, grid, startRow, endRow, colWidth, rowHeight);
                }
            }

            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ServiceException("Failed to generate PDF seating chart: " + ex.getMessage(), ex);
        }
    }

    private Student[][] buildSeatGrid(Room room, List<Allocation> allocations,
                                      Map<Long, Student> studentsById) {
        Student[][] grid = new Student[room.getRows()][room.getColumns()];
        if (allocations == null || studentsById == null) {
            return grid;
        }
        for (Allocation allocation : allocations) {
            if (allocation.getRoomId() != room.getId()) {
                continue;
            }
            int r = allocation.getSeatRow();
            int c = allocation.getSeatCol();
            if (r >= 0 && r < room.getRows() && c >= 0 && c < room.getColumns()) {
                grid[r][c] = studentsById.get(allocation.getStudentId());
            }
        }
        return grid;
    }

    private void drawHeader(PDPageContentStream cs, PDRectangle pageSize, Exam exam, Room room,
                            int startRow, int endRow) throws IOException {
        float topY = pageSize.getHeight() - MARGIN;

        drawText(cs, PDType1Font.HELVETICA_BOLD, 16, MARGIN, topY - 14,
                "Seatmap - Exam Seating Chart");

        String examDetails = String.format("Subject: %s   |   Date: %s   |   Time: %s",
                safeText(exam.getSubject()),
                exam.getExamDate() != null ? exam.getExamDate().toString() : "N/A",
                exam.getStartTime() != null ? exam.getStartTime().toString() : "N/A");
        drawText(cs, PDType1Font.HELVETICA, 10, MARGIN, topY - 34, examDetails);

        String roomDetails = String.format("Room: %s   |   Grid: %dx%d (Capacity: %d)   |   Rows %d-%d",
                safeText(room.getRoomNumber()),
                room.getRows(), room.getColumns(), room.getCapacity(),
                startRow + 1, endRow);
        drawText(cs, PDType1Font.HELVETICA, 10, MARGIN, topY - 50, roomDetails);
    }

    private void drawSeatGrid(PDPageContentStream cs, PDRectangle pageSize, Room room,
                              Student[][] grid, int startRow, int endRow,
                              float colWidth, float rowHeight) throws IOException {
        float gridTopY = pageSize.getHeight() - MARGIN - HEADER_HEIGHT;

        cs.setStrokingColor(new Color(80, 90, 110));
        cs.setLineWidth(0.75f);

        for (int r = startRow; r < endRow; r++) {
            int localRow = r - startRow;
            float cellY = gridTopY - ((localRow + 1) * rowHeight);

            for (int c = 0; c < room.getColumns(); c++) {
                float cellX = MARGIN + (c * colWidth);
                cs.addRect(cellX, cellY, colWidth, rowHeight);
                cs.stroke();

                String seatLabel = "R" + (r + 1) + "-C" + (c + 1);
                drawText(cs, PDType1Font.HELVETICA_BOLD, 8, cellX + 5, cellY + rowHeight - 12, seatLabel);

                Student student = grid[r][c];
                if (student == null) {
                    drawText(cs, PDType1Font.HELVETICA, 8, cellX + 5, cellY + rowHeight - 26, "[Empty]");
                } else {
                    String roll = truncate(safeText(student.getRollNumber()), 16);
                    String name = truncate(safeText(student.getName()), 18);
                    String dept = truncate(safeText(student.getDepartment()), 16);

                    drawText(cs, PDType1Font.HELVETICA_BOLD, 9, cellX + 5, cellY + rowHeight - 24, roll);
                    drawText(cs, PDType1Font.HELVETICA, 8, cellX + 5, cellY + rowHeight - 35, name);
                    drawText(cs, PDType1Font.HELVETICA, 8, cellX + 5, cellY + rowHeight - 45, dept);
                }
            }
        }
    }

    private void drawAttendanceHeader(PDPageContentStream cs, PDRectangle pageSize, Exam exam, Room room,
                                      int total, long present, long absent, long pending,
                                      int pageNum, int totalPages) throws IOException {
        float topY = pageSize.getHeight() - MARGIN;
        drawText(cs, PDType1Font.HELVETICA_BOLD, 16, MARGIN, topY - 14,
                "Seatmap - Teacher Attendance Sheet");

        String examDetails = String.format("Subject: %s   |   Date: %s   |   Time: %s",
                safeText(exam.getSubject()),
                exam.getExamDate() != null ? exam.getExamDate().toString() : "N/A",
                exam.getStartTime() != null ? exam.getStartTime().toString() : "N/A");
        drawText(cs, PDType1Font.HELVETICA, 10, MARGIN, topY - 34, examDetails);

        String roomSummary = String.format(
                "Room: %s   |   Total: %d   |   Present: %d   |   Absent: %d   |   Pending: %d   |   Page %d/%d",
                safeText(room.getRoomNumber()), total, present, absent, pending, pageNum, totalPages);
        drawText(cs, PDType1Font.HELVETICA, 9.5f, MARGIN, topY - 50, roomSummary);

        drawText(cs, PDType1Font.HELVETICA, 9, MARGIN, topY - 70,
                "Invigilator Signature: ______________________________          Date: _______________");
    }

    private void drawAttendanceTable(PDPageContentStream cs, PDRectangle pageSize,
                                     List<Allocation> allocations, Map<Long, Student> studentsById,
                                     int start, int end, float rowHeight,
                                     float headerHeight, float topHeaderSpace) throws IOException {
        float tableWidth = pageSize.getWidth() - (2 * MARGIN);
        float[] colWidths = {28f, 52f, 92f, 165f, 50f, 65f, tableWidth - 452f};
        String[] headers = {"#", "Seat", "Roll Number", "Student Name", "Dept", "Status", "Signature"};

        float yTop = pageSize.getHeight() - MARGIN - topHeaderSpace;

        cs.setStrokingColor(new Color(80, 90, 110));
        cs.setLineWidth(0.75f);

        // Draw table header row
        float headerY = yTop - headerHeight;
        float x = MARGIN;
        for (int i = 0; i < headers.length; i++) {
            cs.addRect(x, headerY, colWidths[i], headerHeight);
            cs.stroke();
            drawText(cs, PDType1Font.HELVETICA_BOLD, 9, x + 5, headerY + 8, headers[i]);
            x += colWidths[i];
        }

        // Draw student rows
        float currentY = headerY;
        for (int idx = start; idx < end; idx++) {
            Allocation allocation = allocations.get(idx);
            Student student = studentsById == null ? null : studentsById.get(allocation.getStudentId());
            currentY -= rowHeight;

            String seatCode = "R" + (allocation.getSeatRow() + 1) + "-C" + (allocation.getSeatCol() + 1);
            String roll = student != null ? truncate(safeText(student.getRollNumber()), 15) : "-";
            String name = student != null ? truncate(safeText(student.getName()), 28) : "-";
            String dept = student != null ? truncate(safeText(student.getDepartment()), 8) : "-";
            String status = safeText(allocation.getStatus());
            if (status.isEmpty()) {
                status = Allocation.STATUS_PENDING;
            }

            String[] cells = {String.valueOf(idx + 1), seatCode, roll, name, dept, status, ""};
            x = MARGIN;
            for (int col = 0; col < cells.length; col++) {
                cs.addRect(x, currentY, colWidths[col], rowHeight);
                cs.stroke();
                if (col == cells.length - 1) {
                    // Draw a signature checkbox [ ] on the left of the Signature cell
                    cs.addRect(x + 6, currentY + 6, 10, 10);
                    cs.stroke();
                } else {
                    drawText(cs, PDType1Font.HELVETICA, 8.5f, x + 5, currentY + 7, cells[col]);
                }
                x += colWidths[col];
            }
        }
    }

    private void drawText(PDPageContentStream cs, PDFont font, float size,
                          float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitizeForWinAnsi(text));
        cs.endText();
    }

    private static String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private static String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLen - 3)) + "...";
    }

    private static String sanitizeForWinAnsi(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 32 && ch <= 255) {
                sb.append(ch);
            } else {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
