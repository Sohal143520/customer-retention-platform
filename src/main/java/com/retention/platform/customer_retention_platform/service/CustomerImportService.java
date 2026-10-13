package com.retention.platform.customer_retention_platform.service;

import com.retention.platform.customer_retention_platform.entity.Customer;
import com.retention.platform.customer_retention_platform.entity.RetentionAction;
import com.retention.platform.customer_retention_platform.repository.CustomerRepository;
import com.retention.platform.customer_retention_platform.repository.RetentionActionAuditRepository;
import com.retention.platform.customer_retention_platform.repository.RetentionActionRepository;
import com.retention.platform.customer_retention_platform.validation.CustomerDataValidator;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class CustomerImportService {

    private static final List<String> EXPECTED_HEADERS = List.of(
            "Customer_ID",
            "Age_Group",
            "Region",
            "Customer_Segment",
            "Tenure_Months",
            "Monthly_Spend_INR",
            "Transactions_90D",
            "Support_Tickets_90D",
            "Avg_Response_Hours",
            "Satisfaction_Score",
            "App_Logins_30D",
            "Days_Since_Last_Purchase",
            "Discount_Usage_Pct",
            "Payment_Failures_90D",
            "Churned"
    );

    private final CustomerRepository customerRepository;
    private final RetentionActionRepository retentionActionRepository;
    private final RetentionActionAuditRepository retentionActionAuditRepository;
    private final CustomerDataValidator validator;

    public CustomerImportService(
            CustomerRepository customerRepository,
            RetentionActionRepository retentionActionRepository,
            RetentionActionAuditRepository retentionActionAuditRepository,
            CustomerDataValidator validator) {

        this.customerRepository = customerRepository;
        this.retentionActionRepository = retentionActionRepository;
        this.retentionActionAuditRepository = retentionActionAuditRepository;
        this.validator = validator;
    }

    @Transactional
    public int importFile(MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        byte[] data = file.getBytes();

        if (isXlsx(data)) {
            return importXlsx(data);
        }

        if (isCsv(data)) {
            return importCsv(data);
        }

        throw new IllegalArgumentException(
                "Unsupported file format. Upload CSV or XLSX.");
    }

    /*
     * XLSX files are ZIP archives and normally begin with PK.
     * This also handles the baseline issue where a file can have
     * a .csv name but actually contain XLSX data.
     */
    private boolean isXlsx(byte[] data) {
        return data.length >= 2
                && data[0] == 'P'
                && data[1] == 'K';
    }

    private boolean isCsv(byte[] data) {

        if (data.length == 0) {
            return false;
        }

        String content = new String(data, StandardCharsets.UTF_8);

        return content.contains(",")
                && content.contains("Customer_ID");
    }

    private int importXlsx(byte[] data) {

        List<Customer> customers = new ArrayList<>();

        try (InputStream inputStream = new ByteArrayInputStream(data);
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException(
                        "XLSX file does not contain a worksheet");
            }

            Sheet sheet = workbook.getSheetAt(0);

            validateXlsxHeaders(sheet);

            DataFormatter formatter = new DataFormatter();

            for (int rowIndex = 1;
                 rowIndex <= sheet.getLastRowNum();
                 rowIndex++) {

                Row row = sheet.getRow(rowIndex);

                if (row == null || isEmptyRow(row, formatter)) {
                    continue;
                }

                Customer customer = createCustomerFromXlsxRow(
                        row,
                        formatter,
                        rowIndex + 1
                );

                validator.validate(customer);
                customers.add(customer);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Unable to read XLSX file. Upload a valid, unencrypted workbook.", e);
        }

        replaceDataset(customers);

        return customers.size();
    }

    private void validateXlsxHeaders(Sheet sheet) {

        Row headerRow = sheet.getRow(0);

        if (headerRow == null) {
            throw new IllegalArgumentException(
                    "XLSX file does not contain a header row");
        }

        DataFormatter formatter = new DataFormatter();

        if (headerRow.getLastCellNum() != EXPECTED_HEADERS.size()) {
            throw new IllegalArgumentException(
                    "Invalid schema. Expected exactly "
                            + EXPECTED_HEADERS.size()
                            + " columns.");
        }

        for (int i = 0; i < EXPECTED_HEADERS.size(); i++) {

            String actual = formatter
                    .formatCellValue(headerRow.getCell(i))
                    .trim();

            String expected = EXPECTED_HEADERS.get(i);

            if (!expected.equals(actual)) {
                throw new IllegalArgumentException(
                        "Invalid column at position "
                                + (i + 1)
                                + ". Expected '"
                                + expected
                                + "' but found '"
                                + actual
                                + "'.");
            }
        }
    }

    private Customer createCustomerFromXlsxRow(
            Row row,
            DataFormatter formatter,
            int rowNumber) {

        Customer customer = new Customer();

        customer.setCustomerId(
                getString(row, 0, formatter, rowNumber));

        customer.setAgeGroup(
                getString(row, 1, formatter, rowNumber));

        customer.setRegion(
                getString(row, 2, formatter, rowNumber));

        customer.setCustomerSegment(
                getString(row, 3, formatter, rowNumber));

        customer.setTenureMonths(
                getInteger(row, 4, formatter, rowNumber));

        customer.setMonthlySpendInr(
                getInteger(row, 5, formatter, rowNumber));

        customer.setTransactions90d(
                getInteger(row, 6, formatter, rowNumber));

        customer.setSupportTickets90d(
                getInteger(row, 7, formatter, rowNumber));

        customer.setAvgResponseHours(
                getDouble(row, 8, formatter, rowNumber));

        customer.setSatisfactionScore(
                getInteger(row, 9, formatter, rowNumber));

        customer.setAppLogins30d(
                getInteger(row, 10, formatter, rowNumber));

        customer.setDaysSinceLastPurchase(
                getInteger(row, 11, formatter, rowNumber));

        customer.setDiscountUsagePct(
                getInteger(row, 12, formatter, rowNumber));

        customer.setPaymentFailures90d(
                getInteger(row, 13, formatter, rowNumber));

        customer.setChurned(
                getString(row, 14, formatter, rowNumber));

        return customer;
    }

    private String getString(
            Row row,
            int column,
            DataFormatter formatter,
            int rowNumber) {

        Cell cell = row.getCell(column);

        if (cell == null) {
            throw new IllegalArgumentException(
                    "Missing value at row "
                            + rowNumber
                            + ", column "
                            + EXPECTED_HEADERS.get(column));
        }

        String value = formatter.formatCellValue(cell).trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    "Empty value at row "
                            + rowNumber
                            + ", column "
                            + EXPECTED_HEADERS.get(column));
        }

        return value;
    }

    private Integer getInteger(
            Row row,
            int column,
            DataFormatter formatter,
            int rowNumber) {

        String value = getString(
                row, column, formatter, rowNumber);

        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid integer at row "
                            + rowNumber
                            + ", column "
                            + EXPECTED_HEADERS.get(column)
                            + ": "
                            + value);
        }
    }

    private Double getDouble(
            Row row,
            int column,
            DataFormatter formatter,
            int rowNumber) {

        String value = getString(
                row, column, formatter, rowNumber);

        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid number at row "
                            + rowNumber
                            + ", column "
                            + EXPECTED_HEADERS.get(column)
                            + ": "
                            + value);
        }
    }

    private boolean isEmptyRow(
            Row row,
            DataFormatter formatter) {

        for (int i = 0; i < EXPECTED_HEADERS.size(); i++) {

            Cell cell = row.getCell(i);

            if (cell != null &&
                    !formatter.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private int importCsv(byte[] data) throws IOException {

        List<Customer> customers = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new ByteArrayInputStream(data),
                        StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();

            if (headerLine == null) {
                throw new IllegalArgumentException(
                        "CSV file is empty");
            }

            validateCsvHeaders(headerLine);

            String line;
            int rowNumber = 1;
            StringBuilder csvRecord = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                if (csvRecord.isEmpty() && line.trim().isEmpty()) {
                    continue;
                }

                if (!csvRecord.isEmpty()) {
                    csvRecord.append('\n');
                }
                csvRecord.append(line);

                if (hasOpenCsvQuote(csvRecord)) {
                    continue;
                }

                rowNumber++;
                List<String> values = parseCsvLine(csvRecord.toString());
                csvRecord.setLength(0);

                if (values.size() != EXPECTED_HEADERS.size()) {
                    throw new IllegalArgumentException(
                            "Invalid number of columns at CSV row "
                                    + rowNumber);
                }

                Customer customer = createCustomerFromCsvRow(
                        values,
                        rowNumber);

                validator.validate(customer);
                customers.add(customer);
            }

            if (!csvRecord.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid CSV: unclosed quotation at row " + (rowNumber + 1));
            }
        }

        replaceDataset(customers);

        return customers.size();
    }

    private void validateCsvHeaders(String headerLine) {

        List<String> headers = parseCsvLine(headerLine);

        if (!headers.equals(EXPECTED_HEADERS)) {
            throw new IllegalArgumentException(
                    "Invalid CSV schema. Expected columns: "
                            + EXPECTED_HEADERS);
        }
    }

    private Customer createCustomerFromCsvRow(
            List<String> values,
            int rowNumber) {

        Customer customer = new Customer();

        customer.setCustomerId(
                required(values.get(0), 0, rowNumber));

        customer.setAgeGroup(
                required(values.get(1), 1, rowNumber));

        customer.setRegion(
                required(values.get(2), 2, rowNumber));

        customer.setCustomerSegment(
                required(values.get(3), 3, rowNumber));

        customer.setTenureMonths(
                parseInteger(values.get(4), 4, rowNumber));

        customer.setMonthlySpendInr(
                parseInteger(values.get(5), 5, rowNumber));

        customer.setTransactions90d(
                parseInteger(values.get(6), 6, rowNumber));

        customer.setSupportTickets90d(
                parseInteger(values.get(7), 7, rowNumber));

        customer.setAvgResponseHours(
                parseDouble(values.get(8), 8, rowNumber));

        customer.setSatisfactionScore(
                parseInteger(values.get(9), 9, rowNumber));

        customer.setAppLogins30d(
                parseInteger(values.get(10), 10, rowNumber));

        customer.setDaysSinceLastPurchase(
                parseInteger(values.get(11), 11, rowNumber));

        customer.setDiscountUsagePct(
                parseInteger(values.get(12), 12, rowNumber));

        customer.setPaymentFailures90d(
                parseInteger(values.get(13), 13, rowNumber));

        customer.setChurned(
                required(values.get(14), 14, rowNumber));

        return customer;
    }

    private String required(
            String value,
            int column,
            int rowNumber) {

        String cleaned = value.trim();

        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException(
                    "Empty value at CSV row "
                            + rowNumber
                            + ", column "
                            + EXPECTED_HEADERS.get(column));
        }

        return cleaned;
    }

    private Integer parseInteger(
            String value,
            int column,
            int rowNumber) {

        String cleaned = required(
                value, column, rowNumber);

        try {
            return Integer.valueOf(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid integer at CSV row "
                            + rowNumber
                            + ", column "
                            + EXPECTED_HEADERS.get(column)
                            + ": "
                            + cleaned);
        }
    }

    private Double parseDouble(
            String value,
            int column,
            int rowNumber) {

        String cleaned = required(
                value, column, rowNumber);

        try {
            return Double.valueOf(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid number at CSV row "
                            + rowNumber
                            + ", column "
                            + EXPECTED_HEADERS.get(column)
                            + ": "
                            + cleaned);
        }
    }

    private List<String> parseCsvLine(String line) {

        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {

            char c = line.charAt(i);

            if (c == '"') {

                if (insideQuotes &&
                        i + 1 < line.length() &&
                        line.charAt(i + 1) == '"') {

                    current.append('"');
                    i++;

                } else {
                    insideQuotes = !insideQuotes;
                }

            } else if (c == ',' && !insideQuotes) {

                values.add(current.toString());
                current.setLength(0);

            } else {
                current.append(c);
            }
        }

        if (insideQuotes) {
            throw new IllegalArgumentException(
                    "Invalid CSV: unclosed quotation");
        }

        values.add(current.toString());

        return values;
    }

    private boolean hasOpenCsvQuote(CharSequence record) {
        boolean insideQuotes = false;

        for (int i = 0; i < record.length(); i++) {
            if (record.charAt(i) != '"') {
                continue;
            }

            if (insideQuotes && i + 1 < record.length()
                    && record.charAt(i + 1) == '"') {
                i++;
            } else {
                insideQuotes = !insideQuotes;
            }
        }

        return insideQuotes;
    }

    private void replaceDataset(List<Customer> customers) {

        if (customers.isEmpty()) {
            throw new IllegalArgumentException(
                    "No customer records found in uploaded file");
        }

        Set<String> ids = new HashSet<>();

        for (Customer customer : customers) {

            if (!ids.add(customer.getCustomerId())) {
                throw new IllegalArgumentException(
                        "Duplicate Customer_ID in uploaded file: "
                                + customer.getCustomerId());
            }
        }

        Set<String> incomingIds = customers.stream()
                .map(Customer::getCustomerId)
                .collect(java.util.stream.Collectors.toSet());

        List<RetentionAction> actionsForRemovedCustomers =
                retentionActionRepository.findByCustomerIdNotIn(incomingIds);
        List<Long> removedActionIds = actionsForRemovedCustomers.stream()
                .map(RetentionAction::getId)
                .toList();

        if (!removedActionIds.isEmpty()) {
            retentionActionAuditRepository.deleteByActionIdIn(removedActionIds);
            retentionActionRepository.deleteAllInBatch(actionsForRemovedCustomers);
        }

        customerRepository.deleteAllInBatch();
        customerRepository.saveAll(customers);
    }
}
