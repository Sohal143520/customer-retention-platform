package com.retention.platform.customer_retention_platform.service;

import com.retention.platform.customer_retention_platform.dto.RetentionBaselineResponse;
import com.retention.platform.customer_retention_platform.dto.RetentionBaselineUploadResponse;
import com.retention.platform.customer_retention_platform.entity.RetentionBaselineSegment;
import com.retention.platform.customer_retention_platform.repository.RetentionBaselineSegmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class RetentionBaselineService {

    private static final List<String> HEADERS = List.of(
            "Period_Start", "Period_End", "Customer_Segment",
            "High_Risk_Customers", "Churned_Within_90D");

    private final RetentionBaselineSegmentRepository repository;

    public RetentionBaselineService(RetentionBaselineSegmentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public RetentionBaselineUploadResponse replacePeriod(MultipartFile file, String actor)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a non-empty baseline CSV file.");
        }
        if (actor == null || actor.isBlank() || actor.length() > 100) {
            throw new IllegalArgumentException("A valid uploading user is required.");
        }

        List<ParsedRow> parsed = parse(file);
        LocalDate periodStart = parsed.getFirst().periodStart();
        LocalDate periodEnd = parsed.getFirst().periodEnd();
        if (!periodEnd.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("The historical baseline period must end before today.");
        }
        if (ChronoUnit.DAYS.between(periodStart, periodEnd) != 89) {
            throw new IllegalArgumentException(
                    "The historical baseline period must cover exactly 90 days, inclusive.");
        }

        for (ParsedRow row : parsed) {
            if (!row.periodStart().equals(periodStart) || !row.periodEnd().equals(periodEnd)) {
                throw new IllegalArgumentException("All rows must use the same 90-day period.");
            }
        }

        Instant importedAt = Instant.now();
        List<RetentionBaselineSegment> records = parsed.stream()
                .map(row -> new RetentionBaselineSegment(
                        periodStart, periodEnd, row.segment(), row.eligible(), row.churned(),
                        actor, importedAt))
                .toList();

        List<RetentionBaselineSegment> existing =
                repository.findByPeriodStartAndPeriodEnd(periodStart, periodEnd);
        if (!existing.isEmpty()) {
            repository.deleteAll(existing);
            repository.flush();
        }
        repository.saveAll(records);

        long eligible = parsed.stream().mapToLong(ParsedRow::eligible).sum();
        long churned = parsed.stream().mapToLong(ParsedRow::churned).sum();
        return new RetentionBaselineUploadResponse(
                "Historical baseline saved separately from live customer data.",
                periodStart, periodEnd, records.size(), eligible, churned);
    }

    @Transactional(readOnly = true)
    public Optional<RetentionBaselineResponse> latest() {
        return repository.findTopByOrderByPeriodEndDesc().map(latest -> {
            List<RetentionBaselineSegment> rows = repository.findByPeriodStartAndPeriodEnd(
                    latest.getPeriodStart(), latest.getPeriodEnd());
            long eligible = rows.stream()
                    .mapToLong(RetentionBaselineSegment::getHighRiskCustomers).sum();
            long churned = rows.stream()
                    .mapToLong(RetentionBaselineSegment::getChurnedWithin90Days).sum();
            Double rate = eligible == 0 ? null : churned * 100.0 / eligible;
            List<RetentionBaselineResponse.Segment> segments = rows.stream()
                    .sorted((left, right) -> left.getCustomerSegment()
                            .compareToIgnoreCase(right.getCustomerSegment()))
                    .map(row -> new RetentionBaselineResponse.Segment(
                            row.getCustomerSegment(), row.getHighRiskCustomers(),
                            row.getChurnedWithin90Days(),
                            row.getChurnedWithin90Days() * 100.0 / row.getHighRiskCustomers()))
                    .toList();
            return new RetentionBaselineResponse(
                    latest.getPeriodStart(), latest.getPeriodEnd(), latest.getImportedAt(),
                    latest.getImportedBy(), eligible, churned, rate, segments);
        });
    }

    private List<ParsedRow> parse(MultipartFile file) throws IOException {
        List<ParsedRow> rows = new ArrayList<>();
        Set<String> segments = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("The baseline CSV is empty.");
            }
            if (!headerLine.isEmpty() && headerLine.charAt(0) == '\uFEFF') {
                headerLine = headerLine.substring(1);
            }
            if (!parseLine(headerLine).equals(HEADERS)) {
                throw new IllegalArgumentException("Invalid baseline CSV headers. Expected: " + HEADERS);
            }

            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) continue;
                List<String> cells = parseLine(line);
                if (cells.size() != HEADERS.size()) {
                    throw new IllegalArgumentException("Invalid number of columns at CSV row " + rowNumber + ".");
                }
                try {
                    LocalDate start = LocalDate.parse(cells.get(0).trim());
                    LocalDate end = LocalDate.parse(cells.get(1).trim());
                    String segment = cells.get(2).trim();
                    long eligible = Long.parseLong(cells.get(3).trim());
                    long churned = Long.parseLong(cells.get(4).trim());
                    if (segment.isEmpty() || segment.length() > 30) {
                        throw new IllegalArgumentException("Customer_Segment must be 1–30 characters.");
                    }
                    if (eligible <= 0 || churned < 0 || churned > eligible) {
                        throw new IllegalArgumentException(
                                "High_Risk_Customers must be positive and Churned_Within_90D cannot exceed it.");
                    }
                    if (!segments.add(segment.toLowerCase(java.util.Locale.ROOT))) {
                        throw new IllegalArgumentException("Each segment may appear only once per baseline period.");
                    }
                    rows.add(new ParsedRow(start, end, segment, eligible, churned));
                } catch (java.time.DateTimeException | NumberFormatException exception) {
                    throw new IllegalArgumentException("Invalid date or customer count at CSV row " + rowNumber + ".");
                }
            }
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The baseline CSV must contain at least one segment row.");
        }
        return rows;
    }

    private List<String> parseLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(character);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Baseline CSV contains an unclosed quotation mark.");
        }
        cells.add(cell.toString());
        return cells;
    }

    private record ParsedRow(
            LocalDate periodStart,
            LocalDate periodEnd,
            String segment,
            long eligible,
            long churned) {
    }
}
