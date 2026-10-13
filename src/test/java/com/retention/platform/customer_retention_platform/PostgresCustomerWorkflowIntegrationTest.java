package com.retention.platform.customer_retention_platform;

import com.retention.platform.customer_retention_platform.dto.RetentionActionRequest;
import com.retention.platform.customer_retention_platform.entity.Customer;
import com.retention.platform.customer_retention_platform.repository.CustomerRepository;
import com.retention.platform.customer_retention_platform.repository.RetentionActionAuditRepository;
import com.retention.platform.customer_retention_platform.repository.RetentionActionRepository;
import com.retention.platform.customer_retention_platform.service.RetentionActionService;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.stream.IntStream;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfSystemProperty(named = "postgres.integration", matches = "true")
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.open-in-view=false",
        "security.jwt.secret=postgres-integration-test-signing-key-0123456789abcdef"
})
@AutoConfigureMockMvc
class PostgresCustomerWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private RetentionActionRepository retentionActionRepository;

    @Autowired
    private RetentionActionAuditRepository retentionActionAuditRepository;

    @Autowired
    private RetentionActionService retentionActionService;

    @BeforeEach
    void clearDisposableTestData() {
        retentionActionAuditRepository.deleteAllInBatch();
        retentionActionRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        String databaseUrl = requiredEnvironmentValue("PG_TEST_DB_URL");
        String databaseUsername = requiredEnvironmentValue("PG_TEST_DB_USERNAME");
        String databasePassword = requiredEnvironmentValue("PG_TEST_DB_PASSWORD");
        requireDisposableDatabaseName(databaseUrl);
        verifyPostgresTestDatabaseConnection(databaseUrl, databaseUsername, databasePassword);
        registry.add("spring.datasource.url", () -> databaseUrl);
        registry.add("spring.datasource.username", () -> databaseUsername);
        registry.add("spring.datasource.password", () -> databasePassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void postgresUploadReplacementAndInvalidUploadRollbackPreserveActionHistory() throws Exception {
        String removedId = uniqueId("PGOLD");
        String retainedId = uniqueId("PGKEEP");
        String addedId = uniqueId("PGNEW");

        uploadCsv(uploadRow(removedId) + uploadRow(retainedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsLoaded").value(2));

        recordRecentHighRiskScore(removedId);
        recordRecentHighRiskScore(retainedId);

        var removedAction = retentionActionService.assign(
                removedId,
                new RetentionActionRequest("Billing Assistance", "Review billing friction."),
                "postgres-integration-agent");
        var retainedAction = retentionActionService.assign(
                retainedId,
                new RetentionActionRequest("Engagement Check-In", "Continue the follow-up."),
                "postgres-integration-agent");
        retentionActionService.updateStatus(
                retainedAction.id(), "CONTACTED", "postgres-integration-agent");

        uploadCsv(uploadRow(retainedId) + uploadRow(addedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordsLoaded").value(2));

        assertEquals(2, customerRepository.count());
        assertFalse(customerRepository.existsById(removedId));
        assertTrue(customerRepository.existsById(retainedId));
        assertTrue(customerRepository.existsById(addedId));
        assertTrue(retentionActionRepository.findById(removedAction.id()).isEmpty());
        assertTrue(retentionActionAuditRepository
                .findByActionIdOrderByCreatedAtAsc(removedAction.id()).isEmpty());
        assertTrue(retentionActionRepository.findById(retainedAction.id()).isPresent());
        assertEquals(0.8, retentionActionRepository.findById(retainedAction.id())
                .orElseThrow().getChurnProbabilityAtAssignment());
        assertEquals(80.0, retentionActionRepository.findById(retainedAction.id())
                .orElseThrow().getRiskPercentAtAssignment());
        assertEquals(2, retentionActionAuditRepository
                .findByActionIdOrderByCreatedAtAsc(retainedAction.id()).size());

        String invalidId = uniqueId("PGBAD");
        String invalidRow = uploadRow(invalidId).replace(",1524,", ",not-a-number,");
        uploadCsv(uploadRow(uniqueId("PGVALID")) + invalidRow)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.recordsLoaded").value(0));

        assertEquals(2, customerRepository.count());
        assertFalse(customerRepository.existsById(invalidId));
        assertTrue(customerRepository.existsById(retainedId));
        assertTrue(customerRepository.existsById(addedId));
        assertEquals(2, retentionActionAuditRepository
                .findByActionIdOrderByCreatedAtAsc(retainedAction.id()).size());
    }

    @Test
    void postgresBulkPlaybookAssignmentOnlyAssignsUncoveredActiveHighRiskCustomers() throws Exception {
        String eligibleId = uniqueId("PGPLAY");
        String coveredId = uniqueId("PGCOVER");
        String churnedId = uniqueId("PGCHURN");
        String lowRiskId = uniqueId("PGLOW");

        uploadCsv(uploadRow(eligibleId) + uploadRow(coveredId)
                + uploadRow(churnedId).replace(",No\n", ",Yes\n")
                + uploadRow(lowRiskId));
        recordRecentHighRiskScore(eligibleId);
        recordRecentHighRiskScore(coveredId);
        recordRecentHighRiskScore(churnedId);
        Customer lowRisk = customerRepository.findById(lowRiskId).orElseThrow();
        lowRisk.recordPrediction(0.3, Instant.now());
        customerRepository.save(lowRisk);
        retentionActionService.assign(coveredId,
                new RetentionActionRequest("Billing Assistance", "Existing intervention."),
                "postgres-integration-agent");

        mockMvc.perform(post("/api/v1/retention-actions/assign-high-risk")
                        .with(user("postgres-integration-agent").roles("RETENTION_AGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedCount").value(1));

        assertEquals(2, retentionActionRepository.count());
        var assigned = retentionActionRepository
                .findByCustomerIdOrderByAssignedAtDesc(eligibleId).getFirst();
        assertEquals(0.8, assigned.getChurnProbabilityAtAssignment());
        assertEquals("postgres-integration-agent", assigned.getAssignedBy());
        assertTrue(retentionActionRepository
                .findByCustomerIdOrderByAssignedAtDesc(churnedId).isEmpty());
        assertTrue(retentionActionRepository
                .findByCustomerIdOrderByAssignedAtDesc(lowRiskId).isEmpty());

        mockMvc.perform(post("/api/v1/retention-actions/assign-high-risk")
                        .with(user("postgres-integration-agent").roles("RETENTION_AGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedCount").value(0));
        assertEquals(2, retentionActionRepository.count());
    }

    @Test
    @EnabledIfSystemProperty(named = "ml.integration", matches = "true")
    void scoresFiveThousandCustomersThroughPostgresAndSpringWithinTwoSeconds(
            TestReporter testReporter) throws Exception {
        customerRepository.saveAll(IntStream.range(0, 5_000)
                .mapToObj(this::performanceCustomer)
                .toList());

        long startedAt = System.nanoTime();
        mockMvc.perform(post("/api/v1/predictions/bulk")
                .with(user("postgres-performance-admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCustomers").value(5_000));
        double elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
        String elapsedMilliseconds = String.format(java.util.Locale.ROOT, "%.1f", elapsedSeconds * 1000);
        testReporter.publishEntry("postgres-spring-bulk-scoring-5000-ms", elapsedMilliseconds);
        System.out.println("PostgreSQL 5,000-customer bulk scoring: " + elapsedMilliseconds + " ms");

        assertTrue(elapsedSeconds < 2.0,
                () -> "5,000-customer PostgreSQL bulk scoring took "
                        + String.format(java.util.Locale.ROOT, "%.3f", elapsedSeconds) + " seconds.");
    }

    @Test
    @EnabledIfSystemProperty(named = "ml.integration", matches = "true")
    void scoresSingleCustomerUnderFiveHundredMillis(TestReporter testReporter) throws Exception {
        String requestBody = """
                {
                  "age_group": "25-34",
                  "region": "East",
                  "customer_segment": "Value",
                  "tenure_months": 48,
                  "monthly_spend_inr": 1524,
                  "transactions_90d": 11,
                  "support_tickets_90d": 6,
                  "avg_response_hours": 20.1,
                  "satisfaction_score": 2,
                  "app_logins_30d": 29,
                  "days_since_last_purchase": 61,
                  "discount_usage_pct": 57,
                  "payment_failures_90d": 1
                }
                """;

        for (int i = 0; i < 3; i++) {
            scoreOneCustomer(requestBody);
        }

        long[] elapsedMilliseconds = new long[10];
        for (int i = 0; i < elapsedMilliseconds.length; i++) {
            long startedAt = System.nanoTime();
            scoreOneCustomer(requestBody);
            elapsedMilliseconds[i] = (System.nanoTime() - startedAt) / 1_000_000;
        }
        java.util.Arrays.sort(elapsedMilliseconds);
        double medianMilliseconds = (elapsedMilliseconds[4] + elapsedMilliseconds[5]) / 2.0;
        String median = String.format(java.util.Locale.ROOT, "%.1f", medianMilliseconds);
        testReporter.publishEntry("single-customer-scoring-median-ms", median);
        System.out.println("Single-customer scoring median: " + median + " ms");

        assertTrue(medianMilliseconds < 500,
                () -> "Single-customer scoring median took " + median + " ms; expected under 500 ms.");
    }

    private void scoreOneCustomer(String requestBody) throws Exception {
        mockMvc.perform(post("/api/v1/predictions/churn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .with(user("postgres-performance-admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    private ResultActions uploadCsv(String rows) throws Exception {
        String csv = "Customer_ID,Age_Group,Region,Customer_Segment,Tenure_Months,Monthly_Spend_INR,"
                + "Transactions_90D,Support_Tickets_90D,Avg_Response_Hours,Satisfaction_Score,"
                + "App_Logins_30D,Days_Since_Last_Purchase,Discount_Usage_Pct,Payment_Failures_90D,Churned\n"
                + rows;
        MockMultipartFile file = new MockMultipartFile(
                "file", "postgres-test-customers.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/customers/upload")
                .file(file)
                .with(user("postgres-test-admin").roles("ADMIN")));
    }

    private String uploadRow(String customerId) {
        return customerId + ",25-34,East,Value,48,1524,11,6,20.1,2,29,61,57,1,No\n";
    }

    private void recordRecentHighRiskScore(String customerId) {
        Customer customer = customerRepository.findById(customerId).orElseThrow();
        customer.recordPrediction(0.8, Instant.now());
        customerRepository.save(customer);
    }

    private Customer performanceCustomer(int index) {
        Customer customer = new Customer();
        customer.setCustomerId("PGPERF" + String.format(java.util.Locale.ROOT, "%05d", index));
        customer.setAgeGroup("25-34");
        customer.setRegion("East");
        customer.setCustomerSegment("Value");
        customer.setTenureMonths(48);
        customer.setMonthlySpendInr(1524);
        customer.setTransactions90d(11);
        customer.setSupportTickets90d(6);
        customer.setAvgResponseHours(20.1);
        customer.setSatisfactionScore(2);
        customer.setAppLogins30d(29);
        customer.setDaysSinceLastPurchase(61);
        customer.setDiscountUsagePct(57);
        customer.setPaymentFailures90d(1);
        customer.setChurned("No");
        return customer;
    }

    private static String uniqueId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String requiredEnvironmentValue(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for the PostgreSQL integration test.");
        }
        return value;
    }

    private static void requireDisposableDatabaseName(String jdbcUrl) {
        var matcher = java.util.regex.Pattern
                .compile("^jdbc:postgresql://[^/]+/([A-Za-z0-9_]+)(?:\\?.*)?$")
                .matcher(jdbcUrl);
        if (!matcher.matches() || !matcher.group(1).toLowerCase().endsWith("_test")) {
            throw new IllegalStateException(
                    "PG_TEST_DB_URL must point to a dedicated PostgreSQL database whose name ends in _test.");
        }
    }

    private static void verifyPostgresTestDatabaseConnection(
            String jdbcUrl,
            String username,
            String password) {
        try (var connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            String connectedDatabase = connection.getCatalog();
            if (connectedDatabase == null || !connectedDatabase.toLowerCase().endsWith("_test")) {
                throw new IllegalStateException(
                        "The connected PostgreSQL database name must end in _test.");
            }
        } catch (SQLException exception) {
            String sqlState = exception.getSQLState() == null ? "unavailable" : exception.getSQLState();
            throw new IllegalStateException(
                    "Cannot connect to the disposable PostgreSQL test database (SQLSTATE " + sqlState
                            + "). Check that PostgreSQL is running, the _test database exists, and the "
                            + "PG_TEST_DB credentials are valid.");
        }
    }
}
