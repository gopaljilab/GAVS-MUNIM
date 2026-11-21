package org.munim.integration;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.munim.api.ApiDtos.*;
import org.munim.intelligence.SupplierRecommendationDtos.SupplierRecommendationList;
import org.munim.intelligence.SupplierRecommendationDtos.SupplierRecommendationView;
import org.munim.security.DemoAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("postgres-test")
@DisplayName("SupplierRecommendationIntegrationTest — AI Recommendations & Security")
public class SupplierRecommendationIntegrationTest {

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

    private UUID createTestRfq(UUID materialId, BigDecimal qty, OffsetDateTime deliveryDate) {
        return db.queryForObject(
                "INSERT INTO rfqs(material_id, specification, quantity, unit, required_procurement_delivery_at, reference_at, timezone, allow_split_sourcing, status) " +
                        "VALUES (?, '2 mm IS 2062 recommendation test sheet', ?, 'kg', ?, now(), 'Asia/Kolkata', false, 'OPEN') RETURNING id",
                UUID.class, materialId, qty, deliveryDate
        );
    }

    private UUID createTestSupplier(String name) {
        return db.queryForObject(
                "INSERT INTO suppliers(name, location, is_synthetic) VALUES (?, 'Recommendation Test Yard', true) RETURNING id",
                UUID.class, name
        );
    }

    private QuoteView createTestQuote(UUID rfqId, UUID supplierId, BigDecimal unitPrice, OffsetDateTime promisedDate) throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("unitPrice", unitPrice.toString());
        fields.put("quotedQuantity", "500");
        fields.put("unit", "kg");
        fields.put("priceBasis", "EXCLUSIVE");
        fields.put("materialTaxRate", "18");
        fields.put("materialItcStatus", "CREDITABLE");
        fields.put("freightBasis", "SEPARATE");
        fields.put("freightAmount", "1000.00");
        fields.put("freightTaxRate", "0");
        fields.put("freightPriceBasis", "EXCLUSIVE");
        fields.put("freightItcStatus", "CREDITABLE");
        fields.put("moq", "0");
        fields.put("purchaseIncrement", "1");
        fields.put("wastagePercent", "0");
        fields.put("quoteQuantityBasis", "INPUT_PURCHASED");
        fields.put("promisedDeliveryAt", promisedDate.toString());
        fields.put("validFrom", now.toString());
        fields.put("validUntil", now.plusDays(7).toString());

        QuoteCreate quoteCreate = new QuoteCreate(
                supplierId,
                now,
                "[SYNTHETIC] Recommendation quote",
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

    private void evaluateQuotes(UUID rfqId, List<UUID> revisionIds) throws Exception {
        for (UUID revId : revisionIds) {
            EvaluationCreate evalCreate = new EvaluationCreate(List.of(revId), 1);
            mockMvc.perform(post("/api/v1/rfqs/" + rfqId + "/evaluations")
                    .header("Authorization", "Bearer dev-procurement")
                    .header("Idempotency-Key", idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(evalCreate)))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    @DisplayName("1. OWNER can retrieve recommendations and POs/status are NOT automatically changed")
    void testOwnerRetrievesRecommendationsWithoutAutonomousSideEffects() throws Exception {
        UUID materialId = getSeededMaterialId();
        OffsetDateTime deadline = OffsetDateTime.now().plusDays(5);
        UUID rfqId = createTestRfq(materialId, new BigDecimal("500.0000"), deadline);

        UUID s1 = createTestSupplier("Vendor Alpha " + UUID.randomUUID().toString().substring(0, 6));
        UUID s2 = createTestSupplier("Vendor Beta " + UUID.randomUUID().toString().substring(0, 6));

        QuoteView q1 = createTestQuote(rfqId, s1, new BigDecimal("92.00"), deadline.minusDays(1));
        QuoteView q2 = createTestQuote(rfqId, s2, new BigDecimal("88.00"), deadline.plusDays(2)); // Late delivery

        evaluateQuotes(rfqId, List.of(q1.revisionId()));

        // Call recommendation endpoint with OWNER role
        MvcResult result = mockMvc.perform(get("/api/v1/rfqs/" + rfqId + "/supplier-recommendations")
                .header("Authorization", "Bearer dev-owner"))
                .andExpect(status().isOk())
                .andReturn();

        SupplierRecommendationList recList = mapper.readValue(
                result.getResponse().getContentAsString(),
                SupplierRecommendationList.class
        );

        assertNotNull(recList);
        assertEquals(rfqId, recList.rfqId());
        assertNotNull(recList.rfqMaterialName());
        assertEquals(2, recList.recommendations().size());
        assertNotNull(recList.overallProcurementBrief());
        assertFalse(recList.overallProcurementBrief().isBlank());

        // Verify Human-In-The-Loop: RFQ remains OPEN, NO purchase orders created, NO autonomous awards
        String rfqStatus = db.queryForObject("SELECT status FROM rfqs WHERE id = ?", String.class, rfqId);
        assertEquals("OPEN", rfqStatus, "RFQ status must NOT be modified by recommendation generation");

        Integer poCount = db.queryForObject("SELECT count(*) FROM purchase_orders WHERE rfq_id = ?", Integer.class, rfqId);
        assertEquals(0, poCount, "Strictly NO purchase orders must be automatically created");

        Integer proposalCount = db.queryForObject(
                "SELECT count(*) FROM commercial_proposals cp JOIN quote_evaluations qe ON qe.id = cp.evaluation_id WHERE qe.rfq_id = ?",
                Integer.class, rfqId
        );
        assertEquals(0, proposalCount, "Strictly NO award proposals must be automatically created");
    }

    @Test
    @DisplayName("2. PROCUREMENT_OPERATOR can access recommendations")
    void testProcurementOperatorCanAccess() throws Exception {
        UUID materialId = getSeededMaterialId();
        OffsetDateTime deadline = OffsetDateTime.now().plusDays(4);
        UUID rfqId = createTestRfq(materialId, new BigDecimal("200.0000"), deadline);

        mockMvc.perform(get("/api/v1/rfqs/" + rfqId + "/supplier-recommendations")
                .header("Authorization", "Bearer dev-procurement"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("3. READ_ONLY_EVALUATOR can view recommendations but is blocked from commercial actions")
    void testReadOnlyEvaluatorCanViewRecommendationsButCannotMakeDecisions() throws Exception {
        UUID materialId = getSeededMaterialId();
        OffsetDateTime deadline = OffsetDateTime.now().plusDays(4);
        UUID rfqId = createTestRfq(materialId, new BigDecimal("200.0000"), deadline);

        // Read-only evaluator can view recommendations
        mockMvc.perform(get("/api/v1/rfqs/" + rfqId + "/supplier-recommendations")
                .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk());

        // But is strictly blocked (403 Forbidden) from commercial actions such as issuing purchase orders
        mockMvc.perform(post("/api/v1/purchase-orders")
                .header("Authorization", "Bearer dev-evaluator")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rfqId\":\"" + rfqId + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4. Unauthenticated request is rejected with 401 Unauthorized")
    void testUnauthenticatedIsUnauthorized() throws Exception {
        UUID materialId = getSeededMaterialId();
        OffsetDateTime deadline = OffsetDateTime.now().plusDays(4);
        UUID rfqId = createTestRfq(materialId, new BigDecimal("200.0000"), deadline);

        mockMvc.perform(get("/api/v1/rfqs/" + rfqId + "/supplier-recommendations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("5. Non-existent RFQ UUID returns 404 Not Found")
    void testNonExistentRfqReturns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/rfqs/" + missingId + "/supplier-recommendations")
                .header("Authorization", "Bearer dev-owner"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("6. Recommendations contain correct ranks, warnings, and missing evidence preservation")
    void testRecommendationDetailsAndWarnings() throws Exception {
        UUID materialId = getSeededMaterialId();
        OffsetDateTime deadline = OffsetDateTime.now().plusDays(3);
        UUID rfqId = createTestRfq(materialId, new BigDecimal("500.0000"), deadline);

        UUID s1 = createTestSupplier("Vendor On-Time " + UUID.randomUUID().toString().substring(0, 6));
        UUID s2 = createTestSupplier("Vendor Late " + UUID.randomUUID().toString().substring(0, 6));

        QuoteView q1 = createTestQuote(rfqId, s1, new BigDecimal("100.00"), deadline.minusDays(1));
        QuoteView q2 = createTestQuote(rfqId, s2, new BigDecimal("90.00"), deadline.plusDays(1)); // Promised late

        evaluateQuotes(rfqId, List.of(q1.revisionId()));

        MvcResult result = mockMvc.perform(get("/api/v1/rfqs/" + rfqId + "/supplier-recommendations")
                .header("Authorization", "Bearer dev-owner"))
                .andExpect(status().isOk())
                .andReturn();

        SupplierRecommendationList recList = mapper.readValue(
                result.getResponse().getContentAsString(),
                SupplierRecommendationList.class
        );

        assertEquals(2, recList.recommendations().size());

        // Check the late vendor has deliveryRiskWarning = true
        SupplierRecommendationView lateVendorRec = recList.recommendations().stream()
                .filter(r -> r.supplierId().equals(s2))
                .findFirst()
                .orElseThrow();
        assertTrue(lateVendorRec.deliveryRiskWarning(), "Vendor promising after deadline must have delivery risk warning");
        assertEquals(Boolean.FALSE, lateVendorRec.meetsRequiredDeliveryDate());
        assertEquals("CAUTION_RISK", lateVendorRec.recommendationTier());

        // Check on-time vendor meets deadline
        SupplierRecommendationView onTimeVendorRec = recList.recommendations().stream()
                .filter(r -> r.supplierId().equals(s1))
                .findFirst()
                .orElseThrow();
        assertEquals(Boolean.TRUE, onTimeVendorRec.meetsRequiredDeliveryDate());

        // Check unrated supplier retains null historical metrics rather than artificial defaults
        assertNull(onTimeVendorRec.historicalScorecard().contractOtdRate());
        assertTrue(onTimeVendorRec.missingEvidence().stream().anyMatch(m -> m.contains("Zero verified purchase orders on record")));
    }
}
