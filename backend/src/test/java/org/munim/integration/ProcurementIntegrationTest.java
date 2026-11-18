package org.munim.integration;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.munim.api.ApiDtos.*;
import org.munim.security.DemoAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("postgres-test")
public class ProcurementIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private ObjectMapper mapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(new DemoAuthFilter())
                .build();
    }

    private String idempotencyKey() {
        return UUID.randomUUID().toString();
    }

    private UUID getSeededMaterialId() {
        return db.queryForObject("SELECT id FROM materials WHERE active LIMIT 1", UUID.class);
    }

    private Map<String, Object> standardQuoteFields() {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("unitPrice", "95.00");
        f.put("quotedQuantity", "500");
        f.put("unit", "kg");
        f.put("priceBasis", "EXCLUSIVE");
        f.put("materialTaxRate", "18");
        f.put("materialItcStatus", "NON_CREDITABLE");
        f.put("freightBasis", "SEPARATE");
        f.put("freightAmount", "1500.00");
        f.put("freightTaxRate", "0");
        f.put("freightPriceBasis", "EXCLUSIVE");
        f.put("freightItcStatus", "CREDITABLE");
        f.put("moq", "0");
        f.put("purchaseIncrement", "1");
        f.put("wastagePercent", "0");
        f.put("quoteQuantityBasis", "INPUT_PURCHASED");
        f.put("promisedDeliveryAt", now.plusDays(2).toString());
        f.put("validFrom", now.toString());
        f.put("validUntil", now.plusDays(7).toString());
        return f;
    }

    // Scenario 1: Create RFQ
    @Test
    @DisplayName("Scenario 1: Create RFQ against PostgreSQL")
    void test01_createRfq() throws Exception {
        UUID materialId = getSeededMaterialId();
        RfqCreate rfqCreate = new RfqCreate(
                materialId,
                "2 mm IS 2062 mild-steel sheet",
                new BigDecimal("500.0000"),
                "kg",
                null,
                null,
                OffsetDateTime.now().plusDays(3),
                "Asia/Kolkata",
                false
        );

        MvcResult result = mockMvc.perform(post("/api/v1/rfqs")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(rfqCreate)))
                .andExpect(status().isCreated())
                .andReturn();

        RfqView view = mapper.readValue(result.getResponse().getContentAsString(), RfqView.class);
        assertNotNull(view.id());
        assertEquals("500.0000", view.quantity());
        assertEquals("OPEN", view.status());

        // Verify persisted in PostgreSQL
        Integer count = db.queryForObject("SELECT count(*) FROM rfqs WHERE id = ?", Integer.class, view.id());
        assertEquals(1, count);
    }

    // Scenario 2: Retrieve RFQ
    @Test
    @DisplayName("Scenario 2: Retrieve RFQ by ID")
    void test02_retrieveRfq() throws Exception {
        UUID materialId = getSeededMaterialId();
        RfqCreate rfqCreate = new RfqCreate(
                materialId,
                "2 mm IS 2062 retrieval test sheet",
                new BigDecimal("250.0000"),
                "kg",
                null,
                null,
                OffsetDateTime.now().plusDays(4),
                "Asia/Kolkata",
                false
        );

        MvcResult postResult = mockMvc.perform(post("/api/v1/rfqs")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(rfqCreate)))
                .andExpect(status().isCreated())
                .andReturn();

        RfqView created = mapper.readValue(postResult.getResponse().getContentAsString(), RfqView.class);

        // Retrieve with dev-evaluator role
        MvcResult getResult = mockMvc.perform(get("/api/v1/rfqs/" + created.id())
                .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk())
                .andReturn();

        RfqView retrieved = mapper.readValue(getResult.getResponse().getContentAsString(), RfqView.class);
        assertEquals(created.id(), retrieved.id());
        assertEquals("250.0000", retrieved.quantity());
        assertEquals("2 mm IS 2062 retrieval test sheet", retrieved.specification());
    }

    // Scenario 3: Add Supplier
    @Test
    @DisplayName("Scenario 3: Add synthetic supplier")
    void test03_addSupplier() throws Exception {
        SupplierCreate supplierCreate = new SupplierCreate("Integration Test Supplier", "Synthetic Test Yard", true);

        MvcResult result = mockMvc.perform(post("/api/v1/suppliers")
                .header("Authorization", "Bearer dev-owner")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(supplierCreate)))
                .andExpect(status().isCreated())
                .andReturn();

        SupplierView view = mapper.readValue(result.getResponse().getContentAsString(), SupplierView.class);
        assertNotNull(view.id());
        assertEquals("Integration Test Supplier", view.name());
        assertTrue(view.isSynthetic());
        assertTrue(view.active());

        // Verify in database
        Boolean inDb = db.queryForObject("SELECT is_synthetic FROM suppliers WHERE id = ?", Boolean.class, view.id());
        assertTrue(inDb);
    }

    // Scenario 4: Enter Supplier Quote
    @Test
    @DisplayName("Scenario 4: Enter supplier quote")
    void test04_enterSupplierQuote() throws Exception {
        UUID materialId = getSeededMaterialId();
        UUID rfqId = createTestRfq(materialId, new BigDecimal("500.0000"));
        UUID supplierId = createTestSupplier("Quote Supplier A");

        QuoteCreate quoteCreate = new QuoteCreate(
                supplierId,
                OffsetDateTime.now(),
                "[SYNTHETIC] 500 kg MS sheet @ 95/kg + 18% GST",
                standardQuoteFields()
        );

        MvcResult result = mockMvc.perform(post("/api/v1/rfqs/" + rfqId + "/quotes")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(quoteCreate)))
                .andExpect(status().isCreated())
                .andReturn();

        QuoteView view = mapper.readValue(result.getResponse().getContentAsString(), QuoteView.class);
        assertNotNull(view.id());
        assertNotNull(view.revisionId());
        assertEquals(1, view.revision());
        assertEquals("RECEIVED", view.state());
        assertEquals("COMPLETE", view.completenessStatus());
        assertTrue(view.missingFields().isEmpty());

        // Verify quote and revision rows in database
        assertEquals(1, db.queryForObject("SELECT count(*) FROM supplier_quotes WHERE id = ?", Integer.class, view.id()));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM supplier_quote_revisions WHERE id = ?", Integer.class, view.revisionId()));
    }

    // Scenario 5: Retrieve Stored Quote
    @Test
    @DisplayName("Scenario 5: Retrieve stored quote and revision")
    void test05_retrieveStoredQuote() throws Exception {
        UUID materialId = getSeededMaterialId();
        UUID rfqId = createTestRfq(materialId, new BigDecimal("500.0000"));
        UUID supplierId = createTestSupplier("Quote Supplier B");

        String raw = "[SYNTHETIC] Raw quotation text for verification";
        QuoteCreate quoteCreate = new QuoteCreate(supplierId, OffsetDateTime.now(), raw, standardQuoteFields());

        MvcResult postResult = mockMvc.perform(post("/api/v1/rfqs/" + rfqId + "/quotes")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(quoteCreate)))
                .andExpect(status().isCreated())
                .andReturn();

        QuoteView created = mapper.readValue(postResult.getResponse().getContentAsString(), QuoteView.class);

        MvcResult getResult = mockMvc.perform(get("/api/v1/quotes/" + created.id() + "/revisions/1")
                .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk())
                .andReturn();

        QuoteView retrieved = mapper.readValue(getResult.getResponse().getContentAsString(), QuoteView.class);
        assertEquals(created.id(), retrieved.id());
        assertEquals(raw, retrieved.rawMessage());
        assertEquals("COMPLETE", retrieved.completenessStatus());
    }

    // Scenario 6: Validate Missing Fields
    @Test
    @DisplayName("Scenario 6: Validate missing and unknown fields in quote")
    void test06_validateMissingFields() throws Exception {
        UUID materialId = getSeededMaterialId();
        UUID rfqId = createTestRfq(materialId, new BigDecimal("500.0000"));
        UUID supplierId = createTestSupplier("Incomplete Supplier");

        Map<String, Object> incompleteFields = standardQuoteFields();
        incompleteFields.put("unitPrice", "UNKNOWN");
        incompleteFields.put("materialItcStatus", "UNKNOWN");

        QuoteCreate quoteCreate = new QuoteCreate(
                supplierId,
                OffsetDateTime.now(),
                "[SYNTHETIC] Incomplete quote message",
                incompleteFields
        );

        MvcResult postResult = mockMvc.perform(post("/api/v1/rfqs/" + rfqId + "/quotes")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(quoteCreate)))
                .andExpect(status().isCreated())
                .andReturn();

        QuoteView quoteView = mapper.readValue(postResult.getResponse().getContentAsString(), QuoteView.class);
        assertEquals("INCOMPLETE", quoteView.completenessStatus());
        assertTrue(quoteView.missingFields().contains("unitPrice"));
        assertTrue(quoteView.missingFields().contains("materialItcStatus"));

        // Call completeness endpoint directly
        MvcResult compResult = mockMvc.perform(get("/api/v1/rfqs/" + rfqId + "/completeness")
                .param("quoteRevisionId", quoteView.revisionId().toString())
                .header("Authorization", "Bearer dev-procurement"))
                .andExpect(status().isOk())
                .andReturn();

        Completeness completeness = mapper.readValue(compResult.getResponse().getContentAsString(), Completeness.class);
        assertFalse(completeness.complete());
        assertTrue(completeness.missingFields().contains("unitPrice"));
        assertTrue(completeness.missingFields().contains("materialItcStatus"));
    }

    // Scenario 7: Evaluate Complete Quote
    @Test
    @DisplayName("Scenario 7: Evaluate a complete quote deterministically")
    void test07_evaluateCompleteQuote() throws Exception {
        UUID materialId = getSeededMaterialId();
        UUID rfqId = createTestRfq(materialId, new BigDecimal("500.0000"));
        UUID supplierId = createTestSupplier("Eval Supplier");

        QuoteView quote = createTestQuote(rfqId, supplierId, standardQuoteFields());

        EvaluationCreate evalCreate = new EvaluationCreate(List.of(quote.revisionId()), 1);

        MvcResult result = mockMvc.perform(post("/api/v1/rfqs/" + rfqId + "/evaluations")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(evalCreate)))
                .andExpect(status().isCreated())
                .andReturn();

        EvaluationView eval = mapper.readValue(result.getResponse().getContentAsString(), EvaluationView.class);
        assertNotNull(eval.id());
        assertEquals("COMPLETE", eval.status());
        assertEquals("1.1", eval.calculationVersion());
        assertNotNull(eval.snapshotSha256());
        assertEquals(64, eval.snapshotSha256().length());
        assertEquals("57550.00", eval.result().get("economicLandedCost"));
        assertEquals("57550.00", eval.result().get("cashPayable"));
    }

    // Scenario 8: Persist Evaluation
    @Test
    @DisplayName("Scenario 8: Verify evaluation is persisted in PostgreSQL database")
    void test08_persistEvaluation() throws Exception {
        UUID materialId = getSeededMaterialId();
        UUID rfqId = createTestRfq(materialId, new BigDecimal("500.0000"));
        UUID supplierId = createTestSupplier("Persist Eval Supplier");

        QuoteView quote = createTestQuote(rfqId, supplierId, standardQuoteFields());

        EvaluationCreate evalCreate = new EvaluationCreate(List.of(quote.revisionId()), 1);

        MvcResult result = mockMvc.perform(post("/api/v1/rfqs/" + rfqId + "/evaluations")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(evalCreate)))
                .andExpect(status().isCreated())
                .andReturn();

        EvaluationView eval = mapper.readValue(result.getResponse().getContentAsString(), EvaluationView.class);

        // Verify quote_evaluations row
        Integer evalCount = db.queryForObject("SELECT count(*) FROM quote_evaluations WHERE id = ? AND status = 'COMPLETE'", Integer.class, eval.id());
        assertEquals(1, evalCount);

        // Verify evaluation_lines row
        BigDecimal landedCost = db.queryForObject("SELECT economic_landed_cost FROM evaluation_lines WHERE evaluation_id = ?", BigDecimal.class, eval.id());
        BigDecimal cashPayable = db.queryForObject("SELECT cash_payable FROM evaluation_lines WHERE evaluation_id = ?", BigDecimal.class, eval.id());
        assertEquals(new BigDecimal("57550.00"), landedCost);
        assertEquals(new BigDecimal("57550.00"), cashPayable);
    }

    // Scenario 9: Retrieve Evaluation
    @Test
    @DisplayName("Scenario 9: Retrieve persisted evaluation through API")
    void test09_retrieveEvaluation() throws Exception {
        UUID materialId = getSeededMaterialId();
        UUID rfqId = createTestRfq(materialId, new BigDecimal("500.0000"));
        UUID supplierId = createTestSupplier("Retrieve Eval Supplier");

        QuoteView quote = createTestQuote(rfqId, supplierId, standardQuoteFields());
        EvaluationCreate evalCreate = new EvaluationCreate(List.of(quote.revisionId()), 1);

        MvcResult postResult = mockMvc.perform(post("/api/v1/rfqs/" + rfqId + "/evaluations")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(evalCreate)))
                .andExpect(status().isCreated())
                .andReturn();

        EvaluationView created = mapper.readValue(postResult.getResponse().getContentAsString(), EvaluationView.class);

        MvcResult getResult = mockMvc.perform(get("/api/v1/evaluations/" + created.id())
                .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk())
                .andReturn();

        EvaluationView retrieved = mapper.readValue(getResult.getResponse().getContentAsString(), EvaluationView.class);
        assertEquals(created.id(), retrieved.id());
        assertEquals(created.snapshotSha256(), retrieved.snapshotSha256());
        assertEquals("57550.00", retrieved.result().get("economicLandedCost"));
    }

    // Scenario 10: Reject Invalid or Incomplete Input
    @Test
    @DisplayName("Scenario 10: Reject invalid or incomplete inputs")
    void test10_rejectInvalidOrIncompleteInput() throws Exception {
        UUID materialId = getSeededMaterialId();

        // 1. Reject non-synthetic supplier
        SupplierCreate nonSynthetic = new SupplierCreate("Real Supplier", "Real Town", false);
        mockMvc.perform(post("/api/v1/suppliers")
                .header("Authorization", "Bearer dev-owner")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(nonSynthetic)))
                .andExpect(status().isBadRequest());

        // 2. Reject unsupported unit (only kg supported)
        RfqCreate badUnitRfq = new RfqCreate(
                materialId,
                "2 mm IS 2062",
                new BigDecimal("500.0000"),
                "tonne",
                null, null,
                OffsetDateTime.now().plusDays(3),
                "Asia/Kolkata",
                false
        );
        mockMvc.perform(post("/api/v1/rfqs")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(badUnitRfq)))
                .andExpect(status().isBadRequest());

        // 3. Reject split sourcing
        RfqCreate splitRfq = new RfqCreate(
                materialId,
                "2 mm IS 2062",
                new BigDecimal("500.0000"),
                "kg",
                null, null,
                OffsetDateTime.now().plusDays(3),
                "Asia/Kolkata",
                true
        );
        mockMvc.perform(post("/api/v1/rfqs")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(splitRfq)))
                .andExpect(status().isBadRequest());

        // 4. Reject evaluation of incomplete quote
        UUID rfqId = createTestRfq(materialId, new BigDecimal("500.0000"));
        UUID supplierId = createTestSupplier("Incomplete Eval Supplier");
        Map<String, Object> incomplete = standardQuoteFields();
        incomplete.put("unitPrice", "UNKNOWN");
        QuoteView incompleteQuote = createTestQuote(rfqId, supplierId, incomplete);

        EvaluationCreate evalIncomplete = new EvaluationCreate(List.of(incompleteQuote.revisionId()), 1);
        mockMvc.perform(post("/api/v1/rfqs/" + rfqId + "/evaluations")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(evalIncomplete)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUOTE_INCOMPLETE"));
    }

    // Scenario 11: Verify Foreign-Key Enforcement
    @Test
    @DisplayName("Scenario 11: Verify composite foreign-key enforcement in PostgreSQL")
    void test11_verifyForeignKeyEnforcement() {
        UUID materialId = getSeededMaterialId();
        UUID rfq1 = createTestRfq(materialId, new BigDecimal("500.0000"));
        UUID rfq2 = createTestRfq(materialId, new BigDecimal("300.0000"));
        UUID supplierId = createTestSupplier("FK Supplier");

        // Insert quote for RFQ 1
        UUID quoteId = db.queryForObject(
                "INSERT INTO supplier_quotes(rfq_id, supplier_id) VALUES (?,?) RETURNING id",
                UUID.class, rfq1, supplierId
        );

        // Attempt to insert quote revision with mismatched RFQ ID (rfq2 instead of rfq1)
        // Must fail due to composite foreign key: (supplier_quote_id, rfq_id) REFERENCES supplier_quotes(id, rfq_id)
        assertThrows(DataIntegrityViolationException.class, () -> {
            db.update(
                    "INSERT INTO supplier_quote_revisions(supplier_quote_id, rfq_id, revision_no, state, raw_message, raw_message_sha256, fields, completeness_status) " +
                            "VALUES (?, ?, 1, 'RECEIVED', 'test', 'hash', '{}'::jsonb, 'INCOMPLETE')",
                    quoteId, rfq2
            );
        });

        // Attempt to insert quote for non-existent RFQ
        assertThrows(DataIntegrityViolationException.class, () -> {
            db.update(
                    "INSERT INTO supplier_quotes(rfq_id, supplier_id) VALUES (?,?)",
                    UUID.randomUUID(), supplierId
            );
        });
    }

    // Scenario 12: Verify Role-Based Access Restrictions
    @Test
    @DisplayName("Scenario 12: Verify role-based access restrictions")
    void test12_verifyRoleBasedAccessRestrictions() throws Exception {
        UUID materialId = getSeededMaterialId();
        SupplierCreate sup = new SupplierCreate("Role Check Supplier", "Yard", true);

        // 1. Missing Authorization header -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/suppliers")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(sup)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // 2. Invalid bearer token -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/suppliers")
                .header("Authorization", "Bearer invalid-token")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(sup)))
                .andExpect(status().isUnauthorized());

        // 3. READ_ONLY_EVALUATOR attempting to POST /rfqs -> 403 Forbidden
        RfqCreate rfq = new RfqCreate(
                materialId,
                "2 mm IS 2062",
                new BigDecimal("500.0000"),
                "kg",
                null, null,
                OffsetDateTime.now().plusDays(3),
                "Asia/Kolkata",
                false
        );
        mockMvc.perform(post("/api/v1/rfqs")
                .header("Authorization", "Bearer dev-evaluator")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(rfq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 4. READ_ONLY_EVALUATOR forbidden on GET /materials (requires OWNER or PROCUREMENT_OPERATOR)
        mockMvc.perform(get("/api/v1/materials")
                .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 5. READ_ONLY_EVALUATOR allowed on GET /rfqs/{id}
        UUID rfqId = createTestRfq(materialId, new BigDecimal("100.0000"));
        mockMvc.perform(get("/api/v1/rfqs/" + rfqId)
                .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk());
    }

    // Helpers
    private UUID createTestRfq(UUID materialId, BigDecimal qty) {
        return db.queryForObject(
                "INSERT INTO rfqs(material_id, specification, quantity, unit, required_procurement_delivery_at, reference_at, timezone, allow_split_sourcing, status) " +
                        "VALUES (?, '2 mm IS 2062 sheet', ?, 'kg', now() + interval '3 days', now(), 'Asia/Kolkata', false, 'OPEN') RETURNING id",
                UUID.class, materialId, qty
        );
    }

    private UUID createTestSupplier(String name) {
        return db.queryForObject(
                "INSERT INTO suppliers(name, location, is_synthetic) VALUES (?, 'Test Yard', true) RETURNING id",
                UUID.class, name
        );
    }

    private QuoteView createTestQuote(UUID rfqId, UUID supplierId, Map<String, Object> fields) throws Exception {
        QuoteCreate quoteCreate = new QuoteCreate(
                supplierId,
                OffsetDateTime.now(),
                "[SYNTHETIC] Test quote message",
                fields
        );
        MvcResult res = mockMvc.perform(post("/api/v1/rfqs/" + rfqId + "/quotes")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(quoteCreate)))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readValue(res.getResponse().getContentAsString(), QuoteView.class);
    }
}
