package com.retention.platform.customer_retention_platform;

import com.retention.platform.customer_retention_platform.repository.UserRepository;
import com.retention.platform.customer_retention_platform.entity.Customer;
import com.retention.platform.customer_retention_platform.entity.RetentionAction;
import com.retention.platform.customer_retention_platform.repository.CustomerRepository;
import com.retention.platform.customer_retention_platform.repository.RetentionActionRepository;
import com.retention.platform.customer_retention_platform.repository.RetentionActionAuditRepository;
import com.retention.platform.customer_retention_platform.repository.RetentionBaselineSegmentRepository;
import com.retention.platform.customer_retention_platform.service.RetentionActionService;
import com.retention.platform.customer_retention_platform.dto.RetentionActionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.IntStream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:retentiontest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"security.jwt.secret=test-only-signing-key-0123456789abcdef"
})
@AutoConfigureMockMvc
class CustomerRetentionPlatformApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private RetentionActionRepository retentionActionRepository;

	@Autowired
	private RetentionActionAuditRepository retentionActionAuditRepository;

	@Autowired
	private RetentionBaselineSegmentRepository retentionBaselineSegmentRepository;

	@Autowired
	private RetentionActionService retentionActionService;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void clearCustomerAndRetentionData() {
		retentionBaselineSegmentRepository.deleteAllInBatch();
		retentionActionAuditRepository.deleteAllInBatch();
		retentionActionRepository.deleteAllInBatch();
		customerRepository.deleteAllInBatch();
	}

	@Test
	void authenticatedAccountCanReadItsRoleForDashboardPermissions() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/role")
				.with(user("operations").roles("OPERATIONS")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("operations"))
				.andExpect(jsonPath("$.role").value("OPERATIONS"));
	}

	@Test
	void historicalBaselineUploadIsSeparateAndInvalidReplacementPreservesPriorPeriod() throws Exception {
		String header = "Period_Start,Period_End,Customer_Segment,High_Risk_Customers,Churned_Within_90D\n";
		String validCsv = header
				+ "2025-01-01,2025-03-31,Value,100,20\n"
				+ "2025-01-01,2025-03-31,Premium,50,5\n";
		MockMultipartFile baseline = uploadFile(
				"baseline.csv", "text/csv", validCsv.getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/retention-cohorts/baseline")
				.file(baseline).with(user("operations").roles("OPERATIONS")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.segmentsLoaded").value(2))
				.andExpect(jsonPath("$.eligibleHighRiskCustomers").value(150))
				.andExpect(jsonPath("$.churnedCustomers").value(25));

		assertEquals(0, customerRepository.count(),
				"Historical baseline upload must not replace live customer records.");
		assertEquals(2, retentionBaselineSegmentRepository.count());

		MockMultipartFile invalidReplacement = uploadFile(
				"invalid.csv", "text/csv",
				(header + "2025-01-01,2025-03-31,Value,10,11\n")
					.getBytes(StandardCharsets.UTF_8));
		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/retention-cohorts/baseline")
				.file(invalidReplacement).with(user("admin").roles("ADMIN")))
				.andExpect(status().isBadRequest());

		assertEquals(2, retentionBaselineSegmentRepository.count(),
				"Invalid replacement must preserve the saved baseline period.");
		mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/retention-cohorts/baseline/latest")
				.with(user("retention-agent").roles("RETENTION_AGENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eligibleHighRiskCustomers").value(150))
				.andExpect(jsonPath("$.observedChurnRatePercent").value(16.666666666666668));
	}

	@Test
	void retentionAgentCannotUploadHistoricalBaseline() throws Exception {
		MockMultipartFile file = uploadFile("baseline.csv", "text/csv",
				"Period_Start,Period_End,Customer_Segment,High_Risk_Customers,Churned_Within_90D\n"
					.getBytes(StandardCharsets.UTF_8));
		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/retention-cohorts/baseline")
				.file(file).with(user("agent").roles("RETENTION_AGENT")))
				.andExpect(status().isForbidden());
	}

	@Test
	void completedInterventionCohortReturnsSegmentAndOverallRelativeReduction() throws Exception {
		String header = "Period_Start,Period_End,Customer_Segment,High_Risk_Customers,Churned_Within_90D\n";
		MockMultipartFile baseline = uploadFile("baseline.csv", "text/csv",
				(header + "2025-01-01,2025-03-31,Value,1,1\n"
						+ "2025-01-01,2025-03-31,Premium,1,0\n").getBytes(StandardCharsets.UTF_8));
		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/retention-cohorts/baseline")
				.file(baseline).with(user("operations").roles("OPERATIONS")))
				.andExpect(status().isOk());

		Instant interventionDate = Instant.parse("2025-04-01T12:00:00Z");
		RetentionAction churned = new RetentionAction("COHORT-VALUE", "Billing Assistance",
				"Review billing friction.", "agent", interventionDate, "Value",
				0.80, 80.0, interventionDate);
		churned.updateFollowUpOutcome("CHURNED", interventionDate.plus(90, ChronoUnit.DAYS));
		RetentionAction retained = new RetentionAction("COHORT-PREMIUM", "Engagement Check-In",
				"Review service goals.", "agent", interventionDate, "Premium",
				0.70, 70.0, interventionDate);
		retained.updateFollowUpOutcome("RETAINED", interventionDate.plus(90, ChronoUnit.DAYS));
		retentionActionRepository.saveAll(java.util.List.of(churned, retained));

		mockMvc.perform(MockMvcRequestBuilders.get(
					"/api/v1/retention-cohorts/baseline/comparison")
				.param("interventionStart", "2025-04-01")
				.with(user("admin").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("COMPLETE"))
				.andExpect(jsonPath("$.baselineChurnRatePercent").value(50.0))
				.andExpect(jsonPath("$.interventionChurnRatePercent").value(50.0))
				.andExpect(jsonPath("$.relativeReductionPercent").value(0.0))
				.andExpect(jsonPath("$.metFifteenPercentTarget").value(false))
				.andExpect(jsonPath("$.segments.length()").value(2));
	}

	@Test
	void interventionComparisonStaysIncompleteWhenCountsOrOutcomesDoNotMatch() throws Exception {
		String header = "Period_Start,Period_End,Customer_Segment,High_Risk_Customers,Churned_Within_90D\n";
		MockMultipartFile baseline = uploadFile("baseline.csv", "text/csv",
				(header + "2025-01-01,2025-03-31,Value,2,1\n").getBytes(StandardCharsets.UTF_8));
		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/retention-cohorts/baseline")
				.file(baseline).with(user("operations").roles("OPERATIONS")))
				.andExpect(status().isOk());

		mockMvc.perform(MockMvcRequestBuilders.get(
					"/api/v1/retention-cohorts/baseline/comparison")
				.param("interventionStart", "2025-04-01")
				.with(user("admin").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("INCOMPLETE"))
				.andExpect(jsonPath("$.relativeReductionPercent").doesNotExist());
	}


	@Test
	void contextLoads() {
	}

	@Test
	void healthEndpointIsPublicAndReturnsOnlyOverallStatus() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
	}

	@Test
	void predictionResponsePreservesNestedModelExplanation() throws Exception {
		String modelResponse = """
				{"churn_probability":0.64,"churn_risk_percent":64.0,
				 "predicted_churn":true,"targeting_capacity":0.3,
				 "model_explanation":{"method":"Signed model contributions",
				 "risk_increasing":[{"feature":"Region: North","log_odds_contribution":0.42}]}}
				""";

		var prediction = objectMapper.readValue(modelResponse,
				com.retention.platform.customer_retention_platform.dto.PredictionResponse.class);

		assertEquals("Signed model contributions",
				prediction.getModelExplanation().get("method"));
		assertTrue(objectMapper.writeValueAsString(prediction)
				.contains("\"model_explanation\""));
	}

	@Test
	void retentionAgentCannotUploadCustomerData() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "customers.csv", "text/csv", "invalid".getBytes());

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/customers/upload")
				.file(file)
				.with(user("agent").roles("RETENTION_AGENT")))
				.andExpect(status().isForbidden());
	}

	@Test
	void operationsUserCannotAssignRetentionAction() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.post(
					"/api/v1/customers/C00001/retention-actions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"actionName":"Billing Assistance","recommendation":"Review billing friction."}
						""")
				.with(user("operations").roles("OPERATIONS")))
				.andExpect(status().isForbidden());
	}

	@Test
	void operationsUserCannotRecordFollowUpOutcome() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.patch(
					"/api/v1/retention-actions/1/outcome")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"outcome\":\"CHURNED\"}")
				.with(user("operations").roles("OPERATIONS")))
				.andExpect(status().isForbidden());
	}

	@Test
	void registrationCannotSetAnElevatedRole() throws Exception {
		String username = "test-" + UUID.randomUUID();

		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"%s","password":"test-password","role":"ADMIN"}
						""".formatted(username)))
				.andExpect(status().isOk());

		assertEquals("RETENTION_AGENT",
				userRepository.findByUsername(username).orElseThrow().getRole());
	}

	@Test
	void csvUploadStoresCustomerAndReturnsLoadedCount() throws Exception {
		String customerId = uniqueId("CSV");
		MockMultipartFile file = uploadFile("customers.csv", "text/csv",
				(uploadHeader() + "\n" + uploadRow(customerId) + "\n").getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/customers/upload")
				.file(file).with(user("admin").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recordsLoaded").value(1));

		assertTrue(customerRepository.existsById(customerId));
	}

	@Test
	void suppliedCustomerWorkbookUploadsAllRowsAndObservedChurnLabels() throws Exception {
		Path workbookPath = Path.of("dataset", "Client Data Set for Student Project Workbook.xlsx");
		byte[] workbookBytes = Files.readAllBytes(workbookPath);
		MockMultipartFile file = uploadFile("customers.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/customers/upload")
				.file(file).with(user("admin").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recordsLoaded").value(2200));

		assertEquals(2200, customerRepository.count());
		var uploadedCustomers = customerRepository.findAll();
		assertEquals(643, uploadedCustomers.stream()
				.filter(customer -> "Yes".equalsIgnoreCase(customer.getChurned())).count());
		assertEquals(1557, uploadedCustomers.stream()
				.filter(customer -> "No".equalsIgnoreCase(customer.getChurned())).count());
		assertEquals(2200, uploadedCustomers.stream()
				.map(Customer::getCustomerId).distinct().count());
		var eastCustomers = uploadedCustomers.stream()
				.filter(customer -> "East".equalsIgnoreCase(customer.getRegion())).toList();
		assertEquals(439, eastCustomers.size());
		assertEquals(142, eastCustomers.stream()
				.filter(customer -> "Yes".equalsIgnoreCase(customer.getChurned())).count());
		var shortTenureCustomers = uploadedCustomers.stream()
				.filter(customer -> customer.getTenureMonths() <= 12).toList();
		assertEquals(421, shortTenureCustomers.size());
		assertEquals(141, shortTenureCustomers.stream()
				.filter(customer -> "Yes".equalsIgnoreCase(customer.getChurned())).count());
	}

	@Test
	void uploadReplacesCustomerDatasetAndKeepsActionsOnlyForRetainedIds() throws Exception {
		String removedId = uniqueId("OLD");
		String retainedId = uniqueId("KEEP");
		String addedId = uniqueId("NEW");
		Customer removed = performanceCustomer(1);
		removed.setCustomerId(removedId);
		Customer retained = performanceCustomer(2);
		retained.setCustomerId(retainedId);
		customerRepository.saveAll(java.util.List.of(removed, retained));

		var removedAction = retentionActionService.assign(removedId,
				new RetentionActionRequest("Billing Assistance", "Check previous billing concern."), "agent");
		var retainedAction = retentionActionService.assign(retainedId,
				new RetentionActionRequest("Engagement Check-In", "Continue the customer follow-up."), "agent");

		MockMultipartFile file = uploadFile("customers.csv", "text/csv",
				(uploadHeader() + "\n" + uploadRow(retainedId) + "\n" + uploadRow(addedId) + "\n")
					.getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/customers/upload")
				.file(file).with(user("admin").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recordsLoaded").value(2));

		assertEquals(2, customerRepository.count());
		assertTrue(!customerRepository.existsById(removedId));
		assertTrue(customerRepository.existsById(retainedId));
		assertTrue(customerRepository.existsById(addedId));
		assertTrue(retentionActionRepository.findById(removedAction.id()).isEmpty());
		assertTrue(retentionActionAuditRepository
				.findByActionIdOrderByCreatedAtAsc(removedAction.id()).isEmpty());
		assertTrue(retentionActionRepository.findById(retainedAction.id()).isPresent());
		assertEquals(1, retentionActionAuditRepository
				.findByActionIdOrderByCreatedAtAsc(retainedAction.id()).size());
	}

	@Test
	void duplicateIdsInCsvAreRejectedBeforeAnyRowsAreSaved() throws Exception {
		String customerId = uniqueId("DUP");
		MockMultipartFile file = uploadFile("customers.csv", "text/csv",
				(uploadHeader() + "\n" + uploadRow(customerId) + "\n" + uploadRow(customerId) + "\n")
					.getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/customers/upload")
				.file(file).with(user("admin").roles("ADMIN")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.recordsLoaded").value(0));

		assertTrue(!customerRepository.existsById(customerId));
	}

	@Test
	void incorrectCsvHeaderReturnsBadRequest() throws Exception {
		String incorrectHeader = uploadHeader().replace("Region", "Area");
		MockMultipartFile file = uploadFile("customers.csv", "text/csv",
				(incorrectHeader + "\n").getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/customers/upload")
				.file(file).with(user("admin").roles("ADMIN")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.recordsLoaded").value(0));
	}

	@Test
	void invalidCsvValueRejectsEntireUploadWithoutPartialSave() throws Exception {
		String existingId = uniqueId("EXIST");
		Customer existingCustomer = performanceCustomer(3);
		existingCustomer.setCustomerId(existingId);
		customerRepository.save(existingCustomer);
		java.util.List<String> customerIdsBefore = customerRepository.findAllCustomerIds()
				.stream().sorted().toList();
		String validId = uniqueId("GOOD");
		String invalidId = uniqueId("BAD");
		String invalidRow = uploadRow(invalidId).replace(",1524,", ",not-a-number,");
		MockMultipartFile file = uploadFile("customers.csv", "text/csv",
				(uploadHeader() + "\n" + uploadRow(validId) + "\n" + invalidRow + "\n")
					.getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/customers/upload")
				.file(file).with(user("admin").roles("ADMIN")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.recordsLoaded").value(0));

		assertTrue(!customerRepository.existsById(validId));
		assertTrue(!customerRepository.existsById(invalidId));
		assertEquals(customerIdsBefore, customerRepository.findAllCustomerIds().stream().sorted().toList());
		assertTrue(customerRepository.existsById(existingId));
	}

	@Test
	void csvUploadPreservesNewlinesInsideQuotedFields() throws Exception {
		String customerId = uniqueId("MULTI");
		String multilineRow = uploadRow(customerId).replace(
				"25-34,East,Value", "25-34,\"East\nZone\",Value");
		MockMultipartFile file = uploadFile("customers.csv", "text/csv",
				(uploadHeader() + "\n" + multilineRow + "\n").getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/customers/upload")
				.file(file).with(user("admin").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recordsLoaded").value(1));

		assertEquals("East\nZone", customerRepository.findById(customerId).orElseThrow().getRegion());
	}

	@Test
	void emptyUploadReturnsBadRequest() throws Exception {
		MockMultipartFile file = uploadFile("customers.csv", "text/csv", new byte[0]);

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/customers/upload")
				.file(file).with(user("admin").roles("ADMIN")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.recordsLoaded").value(0));
	}

	@Test
	void malformedXlsxReturnsBadRequest() throws Exception {
		MockMultipartFile file = uploadFile("customers.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
				new byte[] { 'P', 'K', 1, 2, 3, 4 });

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/customers/upload")
				.file(file).with(user("admin").roles("ADMIN")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.recordsLoaded").value(0));
	}

	@Test
	void xlsxUploadStoresCustomerAndReturnsLoadedCount() throws Exception {
		String customerId = uniqueId("XLS");
		byte[] workbookBytes;
		try (XSSFWorkbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Customers");
			writeXlsxRow(sheet.createRow(0), uploadHeader().split(","));
			writeXlsxRow(sheet.createRow(1), uploadRow(customerId).split(","));
			workbook.write(output);
			workbookBytes = output.toByteArray();
		}
		MockMultipartFile file = uploadFile("customers.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/customers/upload")
				.file(file).with(user("admin").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recordsLoaded").value(1));

		assertTrue(customerRepository.existsById(customerId));
	}

	@Test
	void xlsxWithIncorrectHeaderIsRejectedWithoutReplacingExistingDataset() throws Exception {
		String existingId = uniqueId("KEEP");
		String uploadedId = uniqueId("BADXLS");
		Customer existingCustomer = performanceCustomer(0);
		existingCustomer.setCustomerId(existingId);
		customerRepository.save(existingCustomer);

		byte[] workbookBytes;
		try (XSSFWorkbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Customers");
			String incorrectHeader = uploadHeader().replace("Region", "Area");
			writeXlsxRow(sheet.createRow(0), incorrectHeader.split(","));
			writeXlsxRow(sheet.createRow(1), uploadRow(uploadedId).split(","));
			workbook.write(output);
			workbookBytes = output.toByteArray();
		}
		MockMultipartFile file = uploadFile("invalid-header.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);

		mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/customers/upload")
				.file(file).with(user("admin").roles("ADMIN")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.recordsLoaded").value(0));

		assertEquals(1, customerRepository.count());
		assertTrue(customerRepository.existsById(existingId));
		assertTrue(!customerRepository.existsById(uploadedId));
	}

	@Test
	void retentionActionStatusUpdateAddsAuditEventAndActor() throws Exception {
		String customerId = uniqueId("ACT");
		Customer customer = performanceCustomer(0);
		customer.setCustomerId(customerId);
		customerRepository.save(customer);

		String response = mockMvc.perform(MockMvcRequestBuilders.post(
					"/api/v1/customers/{customerId}/retention-actions", customerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actionName\":\"Billing Assistance\",\"recommendation\":\"Review billing friction.\"}")
				.with(user("retention-agent").roles("RETENTION_AGENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ASSIGNED"))
				.andExpect(jsonPath("$.churnProbabilityAtAssignment").value(0.8))
				.andExpect(jsonPath("$.riskPercentAtAssignment").value(80.0))
				.andExpect(jsonPath("$.riskScoredAtAssignment").exists())
				.andExpect(jsonPath("$.history.length()").value(1))
				.andReturn().getResponse().getContentAsString();
		long actionId = objectMapper.readTree(response).path("id").asLong();

		mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/retention-actions/{actionId}/status", actionId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CONTACTED\"}")
				.with(user("retention-agent").roles("RETENTION_AGENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONTACTED"))
				.andExpect(jsonPath("$.history.length()").value(2))
				.andExpect(jsonPath("$.history[1].actor").value("retention-agent"));
	}

	@Test
	void retentionActionAssignmentRejectsCustomersBelowHighRiskThreshold() throws Exception {
		String customerId = uniqueId("LOWRISK");
		Customer customer = performanceCustomer(0);
		customer.setCustomerId(customerId);
		customer.recordPrediction(0.499, Instant.now());
		customerRepository.save(customer);

		mockMvc.perform(MockMvcRequestBuilders.post(
					"/api/v1/customers/{customerId}/retention-actions", customerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actionName\":\"Billing Assistance\",\"recommendation\":\"Review billing friction.\"}")
				.with(user("retention-agent").roles("RETENTION_AGENT")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"Retention actions can only be assigned to customers with model churn risk of at least 50%."));
		assertEquals(0, retentionActionRepository.count());

		String thresholdCustomerId = uniqueId("AT50");
		Customer thresholdCustomer = performanceCustomer(1);
		thresholdCustomer.setCustomerId(thresholdCustomerId);
		thresholdCustomer.recordPrediction(0.50, Instant.now());
		customerRepository.save(thresholdCustomer);
		mockMvc.perform(MockMvcRequestBuilders.post(
					"/api/v1/customers/{customerId}/retention-actions", thresholdCustomerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actionName\":\"Billing Assistance\",\"recommendation\":\"Review billing friction.\"}")
				.with(user("retention-agent").roles("RETENTION_AGENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.churnProbabilityAtAssignment").value(0.5))
				.andExpect(jsonPath("$.riskPercentAtAssignment").value(50.0));
		assertEquals(1, retentionActionRepository.count());
	}

	@Test
	void retentionActionAssignmentRejectsCustomersAlreadyMarkedChurned() throws Exception {
		String customerId = uniqueId("CHURNED");
		Customer customer = performanceCustomer(0);
		customer.setCustomerId(customerId);
		customer.setChurned("Yes");
		customer.recordPrediction(0.8, Instant.now());
		customerRepository.save(customer);

		mockMvc.perform(MockMvcRequestBuilders.post(
					"/api/v1/customers/{customerId}/retention-actions", customerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actionName\":\"Billing Assistance\",\"recommendation\":\"Review billing friction.\"}")
				.with(user("retention-agent").roles("RETENTION_AGENT")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"Retention actions can only be assigned to active customers who have not churned."));
		assertEquals(0, retentionActionRepository.count());
	}

	@Test
	void runRetentionPlaybooksAssignsOnlyUncoveredEligibleCustomersAndCanBeRepeatedSafely() throws Exception {
		String eligibleId = uniqueId("PLAYBOOK");
		Customer eligible = performanceCustomer(0);
		eligible.setCustomerId(eligibleId);
		eligible.recordPrediction(0.75, Instant.now());
		customerRepository.save(eligible);

		String coveredId = uniqueId("COVERED");
		Customer covered = performanceCustomer(1);
		covered.setCustomerId(coveredId);
		covered.recordPrediction(0.90, Instant.now());
		customerRepository.save(covered);
		retentionActionService.assign(coveredId,
				new RetentionActionRequest("Billing Assistance", "Existing intervention."), "retention-agent");

		String churnedId = uniqueId("PLCHURN");
		Customer churned = performanceCustomer(2);
		churned.setCustomerId(churnedId);
		churned.setChurned("Yes");
		customerRepository.save(churned);

		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/retention-actions/assign-high-risk")
				.with(user("retention-agent").roles("RETENTION_AGENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assignedCount").value(1));
		assertEquals(2, retentionActionRepository.count());

		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/retention-actions/assign-high-risk")
				.with(user("retention-agent").roles("RETENTION_AGENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assignedCount").value(0));
		assertEquals(2, retentionActionRepository.count());

		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/retention-actions/assign-high-risk")
				.with(user("operations").roles("OPERATIONS")))
				.andExpect(status().isForbidden());
	}

	@Test
	void retentionActionAssignmentRejectsStalePredictionSnapshot() throws Exception {
		String customerId = uniqueId("NOSCORE");
		Customer customer = performanceCustomer(0);
		customer.setCustomerId(customerId);
		customer.recordPrediction(0.8, Instant.now().minus(25, ChronoUnit.HOURS));
		customerRepository.save(customer);

		mockMvc.perform(MockMvcRequestBuilders.post(
					"/api/v1/customers/{customerId}/retention-actions", customerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actionName\":\"Billing Assistance\",\"recommendation\":\"Review billing friction.\"}")
				.with(user("retention-agent").roles("RETENTION_AGENT")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("older than 24 hours")));
		assertEquals(0, retentionActionRepository.count());
	}

	@Test
	void followUpOutcomeRequiresNinetyDaysForRetentionAndAddsAuditEvent() throws Exception {
		String customerId = uniqueId("OUT");
		Customer customer = performanceCustomer(0);
		customer.setCustomerId(customerId);
		customerRepository.save(customer);

		String response = mockMvc.perform(MockMvcRequestBuilders.post(
					"/api/v1/customers/{customerId}/retention-actions", customerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"actionName\":\"Billing Assistance\",\"recommendation\":\"Review billing friction.\"}")
				.with(user("retention-agent").roles("RETENTION_AGENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.followUpOutcome").value("PENDING"))
				.andExpect(jsonPath("$.customerSegmentAtAssignment").value("Value"))
				.andReturn().getResponse().getContentAsString();
		long actionId = objectMapper.readTree(response).path("id").asLong();

		mockMvc.perform(MockMvcRequestBuilders.patch(
					"/api/v1/retention-actions/{actionId}/outcome", actionId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"outcome\":\"RETAINED\"}")
				.with(user("retention-agent").roles("RETENTION_AGENT")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"Record Retained only after the 90-day follow-up period is complete."));

		mockMvc.perform(MockMvcRequestBuilders.patch(
					"/api/v1/retention-actions/{actionId}/outcome", actionId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"outcome\":\"CHURNED\"}")
				.with(user("retention-agent").roles("RETENTION_AGENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.followUpOutcome").value("CHURNED"))
				.andExpect(jsonPath("$.outcomeRecordedAt").exists())
				.andExpect(jsonPath("$.history.length()").value(2))
				.andExpect(jsonPath("$.history[1].eventType").value("FOLLOW_UP_OUTCOME"))
				.andExpect(jsonPath("$.history[1].actor").value("retention-agent"));

		mockMvc.perform(MockMvcRequestBuilders.get(
					"/api/v1/retention-actions/outcome-summary")
				.with(user("retention-agent").roles("RETENTION_AGENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalActions").value(1))
				.andExpect(jsonPath("$.outcomesRecorded").value(1))
				.andExpect(jsonPath("$.pendingOutcomes").value(0))
				.andExpect(jsonPath("$.churned").value(1))
				.andExpect(jsonPath("$.observedRetainedSharePercent").value(0.0));
	}

	@Test
	void retainedOutcomeIsAcceptedAfterNinetyDayFollowUp() throws Exception {
		String customerId = uniqueId("OLD");
		Customer customer = performanceCustomer(0);
		customer.setCustomerId(customerId);
		customerRepository.save(customer);
		RetentionAction action = retentionActionRepository.save(new RetentionAction(
				customerId, "Billing Assistance", "Review billing friction.",
				"retention-agent", Instant.now().minus(91, ChronoUnit.DAYS)));

		mockMvc.perform(MockMvcRequestBuilders.patch(
					"/api/v1/retention-actions/{actionId}/outcome", action.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"outcome\":\"RETAINED\"}")
				.with(user("retention-agent").roles("RETENTION_AGENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.followUpOutcome").value("RETAINED"))
				.andExpect(jsonPath("$.outcomeRecordedAt").exists())
				.andExpect(jsonPath("$.history[0].details").value("PENDING → RETAINED"));
	}

	private MockMultipartFile uploadFile(String filename, String contentType, byte[] content) {
		return new MockMultipartFile("file", filename, contentType, content);
	}

	private String uniqueId(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	private String uploadHeader() {
		return "Customer_ID,Age_Group,Region,Customer_Segment,Tenure_Months,Monthly_Spend_INR,"
				+ "Transactions_90D,Support_Tickets_90D,Avg_Response_Hours,Satisfaction_Score,"
				+ "App_Logins_30D,Days_Since_Last_Purchase,Discount_Usage_Pct,Payment_Failures_90D,Churned";
	}

	private String uploadRow(String customerId) {
		return customerId + ",25-34,East,Value,48,1524,11,6,20.1,2,29,61,57,1,No";
	}

	private void writeXlsxRow(Row row, String[] values) {
		for (int column = 0; column < values.length; column++) {
			row.createCell(column).setCellValue(values[column]);
		}
	}

	@Test
	@EnabledIfSystemProperty(named = "ml.integration", matches = "true")
	void scoresFiveThousandCustomersThroughSpringWithinTwoSeconds(
			TestReporter testReporter) throws Exception {
		customerRepository.saveAll(IntStream.range(0, 5_000)
				.mapToObj(this::performanceCustomer)
				.toList());

		long startedAt = System.nanoTime();
		String response = mockMvc.perform(MockMvcRequestBuilders.post(
					"/api/v1/predictions/bulk")
				.with(user("performance-admin").roles("ADMIN")))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		double elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
		testReporter.publishEntry("spring-bulk-scoring-5000-ms",
				String.format(java.util.Locale.ROOT, "%.1f", elapsedSeconds * 1000));
		int returnedCustomers = objectMapper.readTree(response)
				.path("totalCustomers").asInt();

		assertEquals(5_000, returnedCustomers);
		assertTrue(elapsedSeconds < 2.0,
				() -> "5,000-customer Spring bulk scoring took "
						+ String.format("%.3f", elapsedSeconds) + " seconds.");
	}

	private Customer performanceCustomer(int index) {
		Customer customer = new Customer();
		customer.setCustomerId("PERF" + String.format("%05d", index));
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
		customer.recordPrediction(0.8, Instant.now());
		return customer;
	}

}
