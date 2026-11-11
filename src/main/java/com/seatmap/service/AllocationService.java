package com.seatmap.service;

import com.seatmap.dao.AllocationDAO;
import com.seatmap.dao.RoomDAO;
import com.seatmap.dao.StudentDAO;
import com.seatmap.exception.DataAccessException;
import com.seatmap.exception.ServiceException;
import com.seatmap.model.Allocation;
import com.seatmap.model.Exam;
import com.seatmap.model.Room;
import com.seatmap.model.Student;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Automates exam seat allocation across available rooms with orthogonal
 * same-department anti-cheating constraints and backtracking search.
 */
public class AllocationService {

    private static final int MAX_BACKTRACK_STEPS = 200_000;

    /**
     * Summary returned by seat allocation containing generated allocations and any overflow students.
     */
    public record AllocationResult(List<Allocation> allocations, List<Student> unallocatedStudents) {
        public boolean hasUnallocatedStudents() {
            return !unallocatedStudents.isEmpty();
        }
    }

    private final StudentDAO studentDAO;
    private final RoomDAO roomDAO;
    private final AllocationDAO allocationDAO;

    public AllocationService() {
        this(new StudentDAO(), new RoomDAO(), new AllocationDAO());
    }

    public AllocationService(StudentDAO studentDAO, RoomDAO roomDAO, AllocationDAO allocationDAO) {
        this.studentDAO = studentDAO;
        this.roomDAO = roomDAO;
        this.allocationDAO = allocationDAO;
    }

    /**
     * Fetches eligible students for {@code exam} and available rooms, runs the anti-cheating
     * allocation algorithm, and atomically replaces the exam's allocations in the database.
     */
    public AllocationResult allocateAndSave(Exam exam) {
        if (exam == null) {
            throw new IllegalArgumentException("Exam must not be null");
        }
        return allocateAndSave(exam, exam.getTargetDepartment(), exam.getTargetSemester());
    }

    /**
     * Convenience overload returning only the persisted {@link Allocation} list.
     */
    public List<Allocation> allocateSeats(Exam exam) {
        return allocateAndSave(exam).allocations();
    }

    /**
     * Allocates seats for students filtered by explicit department/semester criteria
     * (supporting comma-separated departments or {@code null}/{@code "ALL"} for all departments)
     * and persists the result in a single transaction.
     */
    public AllocationResult allocateAndSave(Exam exam, String targetDepartment, String targetSemester) {
        if (exam == null || exam.getId() <= 0) {
            throw new IllegalArgumentException("Exam must be persisted with a positive id before saving allocations");
        }
        try {
            List<Student> eligibleStudents = fetchEligibleStudents(targetDepartment, targetSemester);
            List<Room> availableRooms = roomDAO.getAll();
            AllocationResult result = planAllocation(exam, eligibleStudents, availableRooms);
            allocationDAO.replaceAllocationsForExam(exam.getId(), result.allocations());
            return result;
        } catch (DataAccessException ex) {
            throw new ServiceException("Failed to allocate and persist exam seats: " + ex.getMessage(), ex);
        }
    }

    /**
     * Computes seat assignments in memory without touching the database.
     * Ensures no two orthogonally adjacent seats (left, right, front, back) in the same room
     * contain students from the same department.
     */
    public AllocationResult planAllocation(Exam exam, List<Student> students, List<Room> rooms) {
        if (exam == null) {
            throw new IllegalArgumentException("Exam must not be null");
        }

        List<Student> uniqueStudents = deduplicateStudents(students);
        if (uniqueStudents.isEmpty() || rooms == null || rooms.isEmpty()) {
            return new AllocationResult(List.of(), uniqueStudents);
        }

        Map<String, Deque<Student>> queuesByDept = groupStudentsByDepartment(uniqueStudents);
        List<Room> orderedRooms = new ArrayList<>(rooms);
        orderedRooms.sort(Comparator.comparing(
                room -> room.getRoomNumber() == null ? "" : room.getRoomNumber(),
                String.CASE_INSENSITIVE_ORDER));

        List<Allocation> allAllocations = new ArrayList<>();
        int remainingTotal = uniqueStudents.size();

        for (Room room : orderedRooms) {
            if (remainingTotal == 0) {
                break;
            }
            if (room.getRows() <= 0 || room.getColumns() <= 0 || room.getCapacity() <= 0) {
                continue;
            }

            int roomSeatLimit = Math.min(room.getCapacity(), room.getRows() * room.getColumns());
            int targetCount = Math.min(remainingTotal, roomSeatLimit);

            String[][] deptGrid = solveRoomLayout(room.getRows(), room.getColumns(), targetCount, queuesByDept);

            for (int r = 0; r < room.getRows(); r++) {
                for (int c = 0; c < room.getColumns(); c++) {
                    String deptKey = deptGrid[r][c];
                    if (deptKey != null) {
                        Student student = queuesByDept.get(deptKey).pollFirst();
                        if (student != null) {
                            allAllocations.add(new Allocation(
                                    exam.getId(), student.getId(), room.getId(), r, c));
                            remainingTotal--;
                        }
                    }
                }
            }
        }

        List<Student> unallocated = new ArrayList<>();
        for (Deque<Student> queue : queuesByDept.values()) {
            unallocated.addAll(queue);
        }
        return new AllocationResult(allAllocations, unallocated);
    }

    private List<Student> fetchEligibleStudents(String targetDepartment, String targetSemester) {
        boolean hasDept = targetDepartment != null && !targetDepartment.isBlank()
                && !targetDepartment.trim().equalsIgnoreCase("ALL");
        boolean hasSem = targetSemester != null && !targetSemester.isBlank();

        if (!hasDept && !hasSem) {
            return studentDAO.getAll();
        }
        if (!hasDept) {
            return studentDAO.getBySemester(targetSemester.trim());
        }

        Set<String> requestedDepts = parseDepartmentFilter(targetDepartment);
        if (hasSem) {
            List<Student> matched = new ArrayList<>();
            for (String dept : requestedDepts) {
                matched.addAll(studentDAO.getByDepartmentAndSemester(dept, targetSemester.trim()));
            }
            return matched;
        }

        List<Student> filtered = new ArrayList<>();
        for (Student student : studentDAO.getAll()) {
            if (requestedDepts.contains(normalizeDept(student.getDepartment()))) {
                filtered.add(student);
            }
        }
        return filtered;
    }

    private Set<String> parseDepartmentFilter(String targetDepartment) {
        Set<String> departments = new HashSet<>();
        for (String token : targetDepartment.split(",")) {
            String normalized = normalizeDept(token);
            if (!normalized.isEmpty()) {
                departments.add(normalized);
            }
        }
        return departments;
    }

    private String[][] solveRoomLayout(int rows, int cols, int targetCount,
                                       Map<String, Deque<Student>> queuesByDept) {
        List<String> deptKeys = new ArrayList<>();
        int[] initialCounts = buildActiveDeptCounts(queuesByDept, deptKeys);
        if (deptKeys.isEmpty() || targetCount <= 0) {
            return new String[rows][cols];
        }

        // First attempt exact/full targetCount placement via backtracking (no unnecessary skips).
        int totalCells = rows * cols;
        int allowedSkips = totalCells - targetCount;
        BacktrackState exactState = new BacktrackState(rows, cols, deptKeys, initialCounts, targetCount, allowedSkips);
        if (exactState.searchExact(0, 0)) {
            return exactState.toDeptGrid();
        }

        // Fallback: maximize placed students when full placement is mathematically impossible
        // (e.g., dominant single department exceeding checkerboard limit).
        BacktrackState maximalState = new BacktrackState(rows, cols, deptKeys, initialCounts, targetCount, totalCells);
        maximalState.searchMaximal(0, 0);
        return maximalState.bestToDeptGrid();
    }

    private int[] buildActiveDeptCounts(Map<String, Deque<Student>> queuesByDept, List<String> deptKeys) {
        List<Map.Entry<String, Deque<Student>>> active = new ArrayList<>();
        for (Map.Entry<String, Deque<Student>> entry : queuesByDept.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                active.add(entry);
            }
        }
        active.sort((a, b) -> {
            int cmp = Integer.compare(b.getValue().size(), a.getValue().size());
            return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
        });

        int[] counts = new int[active.size()];
        for (int i = 0; i < active.size(); i++) {
            deptKeys.add(active.get(i).getKey());
            counts[i] = active.get(i).getValue().size();
        }
        return counts;
    }

    private static List<Student> deduplicateStudents(List<Student> students) {
        if (students == null || students.isEmpty()) {
            return List.of();
        }
        List<Student> result = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        Set<String> seenRolls = new HashSet<>();

        for (Student student : students) {
            if (student == null) {
                continue;
            }
            if (student.getId() > 0 && !seenIds.add(student.getId())) {
                continue;
            }
            if (student.getId() <= 0 && student.getRollNumber() != null
                    && !seenRolls.add(student.getRollNumber().trim())) {
                continue;
            }
            result.add(student);
        }
        return result;
    }

    private static Map<String, Deque<Student>> groupStudentsByDepartment(List<Student> students) {
        List<Student> sorted = new ArrayList<>(students);
        sorted.sort(Comparator.comparing(
                s -> s.getRollNumber() == null ? "" : s.getRollNumber(),
                String.CASE_INSENSITIVE_ORDER));

        Map<String, Deque<Student>> grouped = new LinkedHashMap<>();
        for (Student student : sorted) {
            String key = normalizeDept(student.getDepartment());
            grouped.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(student);
        }
        return grouped;
    }

    private static String normalizeDept(String department) {
        return department == null ? "" : department.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Bounded backtracking solver operating on department indices rather than individual students
     * so identical-department permutations do not cause combinatorial blowup.
     */
    private static final class BacktrackState {
        private final int rows;
        private final int cols;
        private final int totalCells;
        private final List<String> deptKeys;
        private final int[] counts;
        private final int targetCount;
        private final int[][] grid;
        private final int[][] bestGrid;
        private int bestPlaced = 0;
        private int steps = 0;

        BacktrackState(int rows, int cols, List<String> deptKeys,
                       int[] initialCounts, int targetCount, int allowedSkips) {
            this.rows = rows;
            this.cols = cols;
            this.totalCells = rows * cols;
            this.deptKeys = deptKeys;
            this.counts = initialCounts.clone();
            this.targetCount = targetCount;
            this.grid = new int[rows][cols];
            this.bestGrid = new int[rows][cols];
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    this.grid[r][c] = -1;
                    this.bestGrid[r][c] = -1;
                }
            }
            this.remainingSkipBudget = allowedSkips;
        }

        private int remainingSkipBudget;

        boolean searchExact(int cellIdx, int placed) {
            if (placed == targetCount) {
                return true;
            }
            if (cellIdx >= totalCells || ++steps > MAX_BACKTRACK_STEPS) {
                return false;
            }
            int remainingCells = totalCells - cellIdx;
            if (placed + remainingCells < targetCount) {
                return false;
            }

            int r = cellIdx / cols;
            int c = cellIdx % cols;

            for (int deptIdx : orderedValidDepartments(r, c)) {
                grid[r][c] = deptIdx;
                counts[deptIdx]--;
                if (searchExact(cellIdx + 1, placed + 1)) {
                    return true;
                }
                counts[deptIdx]++;
                grid[r][c] = -1;
            }

            if (remainingSkipBudget > 0) {
                remainingSkipBudget--;
                if (searchExact(cellIdx + 1, placed)) {
                    return true;
                }
                remainingSkipBudget++;
            }

            return false;
        }

        void searchMaximal(int cellIdx, int placed) {
            if (placed > bestPlaced) {
                bestPlaced = placed;
                copyGrid(grid, bestGrid);
            }
            if (bestPlaced == targetCount || cellIdx >= totalCells || ++steps > MAX_BACKTRACK_STEPS) {
                return;
            }

            int remainingCells = totalCells - cellIdx;
            if (placed + remainingCells <= bestPlaced) {
                return;
            }

            int r = cellIdx / cols;
            int c = cellIdx % cols;

            for (int deptIdx : orderedValidDepartments(r, c)) {
                grid[r][c] = deptIdx;
                counts[deptIdx]--;
                searchMaximal(cellIdx + 1, placed + 1);
                counts[deptIdx]++;
                grid[r][c] = -1;
                if (bestPlaced == targetCount || steps > MAX_BACKTRACK_STEPS) {
                    return;
                }
            }

            // Try leaving this seat empty only if skipping could still beat bestPlaced.
            if (placed + (remainingCells - 1) > bestPlaced) {
                searchMaximal(cellIdx + 1, placed);
            }
        }

        private List<Integer> orderedValidDepartments(int r, int c) {
            int frontDept = (r > 0) ? grid[r - 1][c] : -1;
            int leftDept = (c > 0) ? grid[r][c - 1] : -1;

            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < counts.length; i++) {
                if (counts[i] > 0 && i != frontDept && i != leftDept) {
                    candidates.add(i);
                }
            }
            candidates.sort((a, b) -> Integer.compare(counts[b], counts[a]));
            return candidates;
        }

        String[][] toDeptGrid() {
            return mapGrid(grid);
        }

        String[][] bestToDeptGrid() {
            return mapGrid(bestGrid);
        }

        private String[][] mapGrid(int[][] source) {
            String[][] result = new String[rows][cols];
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int idx = source[r][c];
                    result[r][c] = idx >= 0 ? deptKeys.get(idx) : null;
                }
            }
            return result;
        }

        private static void copyGrid(int[][] from, int[][] to) {
            for (int r = 0; r < from.length; r++) {
                System.arraycopy(from[r], 0, to[r], 0, from[r].length);
            }
        }
    }
}
