package org.munim.integration;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.munim.api.ApiDtos.*;
import org.munim.approval.ApprovalService;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("postgres-test")
public class ApprovalIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ApprovalService approvalService;

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

    private record EvaluationFixture(
            UUID rfqId,
            UUID supplierId,
            UUID quoteId,
            UUID quoteRevisionId,
            UUID evaluationId,
            String evaluationHash
    ) {}

    private EvaluationFixture createReadyEvaluation(String supplierName) throws Exception {
        UUID materialId = getSeededMaterialId();
        UUID rfqId = db.queryForObject(
                "INSERT INTO rfqs(material_id, specification, quantity, unit, required_procurement_delivery_at, reference_at, timezone, allow_split_sourcing, status) " +
                        "VALUES (?, '2 mm IS 2062 sheet', 500.0000, 'kg', now() + interval '3 days', now(), 'Asia/Kolkata', false, 'OPEN') RETURNING id",
                UUID.class, materialId
        );
        UUID supplierId = db.queryForObject(
                "INSERT INTO suppliers(name, location, is_synthetic) VALUES (?, 'Yard A', true) RETURNING id",
                UUID.class, supplierName + "-" + UUID.randomUUID().toString().substring(0, 8)
        );
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("unitPrice", "95.00");
        fields.put("quotedQuantity", "500");
        fields.put("unit", "kg");
        fields.put("priceBasis", "EXCLUSIVE");
        fields.put("materialTaxRate", "18");
        fields.put("materialItcStatus", "NON_CREDITABLE");
        fields.put("freightBasis", "SEPARATE");
        fields.put("freightAmount", "1500.00");
        fields.put("freightTaxRate", "0");
        fields.put("freightPriceBasis", "EXCLUSIVE");
        fields.put("freightItcStatus", "CREDITABLE");
        fields.put("moq", "0");
        fields.put("purchaseIncrement", "1");
        fields.put("wastagePercent", "0");
        fields.put("quoteQuantityBasis", "INPUT_PURCHASED");
        fields.put("promisedDeliveryAt", now.plusDays(2).toString());
        fields.put("validFrom", now.minusHours(1).toString());
        fields.put("validUntil", now.plusDays(7).toString());

        QuoteCreate quoteCreate = new QuoteCreate(supplierId, now, "[SYNTHETIC] Valid quote message", fields);
        MvcResult quoteRes = mockMvc.perform(post("/api/v1/rfqs/" + rfqId + "/quotes")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(quoteCreate)))
                .andExpect(status().isCreated())
                .andReturn();
        QuoteView qv = mapper.readValue(quoteRes.getResponse().getContentAsString(), QuoteView.class);
        UUID quoteRevisionId = qv.revisionId();

        EvaluationCreate evalCreate = new EvaluationCreate(List.of(quoteRevisionId), 1);
        MvcResult evalRes = mockMvc.perform(post("/api/v1/rfqs/" + rfqId + "/evaluations")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(evalCreate)))
                .andExpect(status().isCreated())
                .andReturn();
        EvaluationView ev = mapper.readValue(evalRes.getResponse().getContentAsString(), EvaluationView.class);

        return new EvaluationFixture(rfqId, supplierId, qv.id(), quoteRevisionId, ev.id(), ev.snapshotSha256());
    }

    private ProposalView createAwardProposal(EvaluationFixture fix) throws Exception {
        AwardProposalCreate ap = new AwardProposalCreate(fix.quoteRevisionId(), fix.evaluationHash());
        MvcResult res = mockMvc.perform(post("/api/v1/evaluations/" + fix.evaluationId() + "/award-proposals")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(ap)))
                .andExpect(status().isCreated())
                .andReturn();
        return mapper.readValue(res.getResponse().getContentAsString(), ProposalView.class);
    }

    @Test
    @DisplayName("Scenario 1: Authorized owner can approve proposal and update commercial state")
    void test01_authorizedOwnerCanApprove() throws Exception {
        EvaluationFixture fix = createReadyEvaluation("Scenario1-Supplier");
        ProposalView prop = createAwardProposal(fix);
        assertNotNull(prop.id());
        assertEquals("PENDING", prop.status());

        DecisionCreate decision = new DecisionCreate("APPROVE", "Optimal economic landed cost approved", prop.snapshotHash());

        MvcResult result = mockMvc.perform(post("/api/v1/proposals/" + prop.id() + "/decision")
                .header("Authorization", "Bearer dev-owner")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.proposalId").value(prop.id().toString()))
                .andReturn();

        // Verify database state: proposal is APPROVED
        String propStatus = db.queryForObject("SELECT status FROM commercial_proposals WHERE id = ?", String.class, prop.id());
        assertEquals("APPROVED", propStatus);

        // Verify commercial state: RFQ is AWARDED to the quote
        String rfqStatus = db.queryForObject("SELECT status FROM rfqs WHERE id = ?", String.class, fix.rfqId());
        UUID awardedQuoteId = db.queryForObject("SELECT awarded_quote_id FROM rfqs WHERE id = ?", UUID.class, fix.rfqId());
        assertEquals("AWARDED", rfqStatus);
        assertEquals(fix.quoteId(), awardedQuoteId);

        // Verify supplier quote has awarded_revision_id set
        UUID awardedRev = db.queryForObject("SELECT awarded_revision_id FROM supplier_quotes WHERE id = ?", UUID.class, fix.quoteId());
        assertEquals(fix.quoteRevisionId(), awardedRev);
    }

    @Test
    @DisplayName("Scenario 2: Unauthorized user cannot approve")
    void test02_unauthorizedUserCannotApprove() throws Exception {
        EvaluationFixture fix = createReadyEvaluation("Scenario2-Supplier");
        ProposalView prop = createAwardProposal(fix);
        DecisionCreate decision = new DecisionCreate("APPROVE", "Unauthorized attempt", prop.snapshotHash());

        // 1. Missing Authorization header -> 401
        mockMvc.perform(post("/api/v1/proposals/" + prop.id() + "/decision")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // 2. Invalid bearer token -> 401
        mockMvc.perform(post("/api/v1/proposals/" + prop.id() + "/decision")
                .header("Authorization", "Bearer invalid-token")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Scenario 3: Procurement operator cannot bypass owner approval")
    void test03_procurementOperatorCannotBypassOwnerApproval() throws Exception {
        EvaluationFixture fix = createReadyEvaluation("Scenario3-Supplier");
        ProposalView prop = createAwardProposal(fix);
        DecisionCreate decision = new DecisionCreate("APPROVE", "Operator bypass attempt", prop.snapshotHash());

        mockMvc.perform(post("/api/v1/proposals/" + prop.id() + "/decision")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Verify proposal remains PENDING and RFQ is NOT awarded
        String propStatus = db.queryForObject("SELECT status FROM commercial_proposals WHERE id = ?", String.class, prop.id());
        assertEquals("PENDING", propStatus);
        String rfqStatus = db.queryForObject("SELECT status FROM rfqs WHERE id = ?", String.class, fix.rfqId());
        assertEquals("OPEN", rfqStatus);
    }

    @Test
    @DisplayName("Scenario 4: Read-only evaluator cannot modify approval state")
    void test04_readOnlyEvaluatorCannotModifyApprovalState() throws Exception {
        EvaluationFixture fix = createReadyEvaluation("Scenario4-Supplier");
        ProposalView prop = createAwardProposal(fix);

        // 1. Evaluator cannot decide proposals -> 403
        DecisionCreate decision = new DecisionCreate("APPROVE", "Evaluator bypass", prop.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + prop.id() + "/decision")
                .header("Authorization", "Bearer dev-evaluator")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isForbidden());

        // 2. Evaluator cannot create award proposals -> 403
        AwardProposalCreate ap = new AwardProposalCreate(fix.quoteRevisionId(), fix.evaluationHash());
        mockMvc.perform(post("/api/v1/evaluations/" + fix.evaluationId() + "/award-proposals")
                .header("Authorization", "Bearer dev-evaluator")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(ap)))
                .andExpect(status().isForbidden());

        // 3. Evaluator cannot access owner approvals queue -> 403
        mockMvc.perform(get("/api/v1/approvals")
                .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Scenario 5: Missing decision reason is rejected when required")
    void test05_missingDecisionReasonRejectedWhenRequired() throws Exception {
        EvaluationFixture fix = createReadyEvaluation("Scenario5-Supplier");
        ProposalView prop = createAwardProposal(fix);

        // Rejection without reason -> 400 Bad Request
        DecisionCreate noReason = new DecisionCreate("REJECT", null, prop.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + prop.id() + "/decision")
                .header("Authorization", "Bearer dev-owner")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(noReason)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // Rejection with blank reason -> 400 Bad Request
        DecisionCreate blankReason = new DecisionCreate("REJECT", "   ", prop.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + prop.id() + "/decision")
                .header("Authorization", "Bearer dev-owner")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(blankReason)))
                .andExpect(status().isBadRequest());

        // Proposal remains PENDING
        String propStatus = db.queryForObject("SELECT status FROM commercial_proposals WHERE id = ?", String.class, prop.id());
        assertEquals("PENDING", propStatus);
    }

    @Test
    @DisplayName("Scenario 6: Modified proposal snapshot causes HTTP 409 Conflict")
    void test06_modifiedSnapshotCausesConflict() throws Exception {
        EvaluationFixture fix = createReadyEvaluation("Scenario6-Supplier");
        ProposalView prop = createAwardProposal(fix);

        String tamperedHash = "deadbeef00000000000000000000000000000000000000000000000000000000";
        DecisionCreate tampered = new DecisionCreate("APPROVE", "Tampered hash decision", tamperedHash);

        mockMvc.perform(post("/api/v1/proposals/" + prop.id() + "/decision")
                .header("Authorization", "Bearer dev-owner")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(tampered)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        // Proposal remains PENDING
        String propStatus = db.queryForObject("SELECT status FROM commercial_proposals WHERE id = ?", String.class, prop.id());
        assertEquals("PENDING", propStatus);
    }

    @Test
    @DisplayName("Scenario 7: Duplicate approval decisions handled safely and idempotently")
    void test07_duplicateDecisionsHandledSafely() throws Exception {
        EvaluationFixture fix = createReadyEvaluation("Scenario7-Supplier");
        ProposalView prop = createAwardProposal(fix);

        String key = idempotencyKey();
        DecisionCreate decision = new DecisionCreate("APPROVE", "Approved once", prop.snapshotHash());

        // First call -> 200 OK
        MvcResult first = mockMvc.perform(post("/api/v1/proposals/" + prop.id() + "/decision")
                .header("Authorization", "Bearer dev-owner")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andReturn();
        ApprovalView v1 = mapper.readValue(first.getResponse().getContentAsString(), ApprovalView.class);

        // Replay with identical idempotency key -> identical 200 OK
        MvcResult replay = mockMvc.perform(post("/api/v1/proposals/" + prop.id() + "/decision")
                .header("Authorization", "Bearer dev-owner")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andReturn();
        ApprovalView v2 = mapper.readValue(replay.getResponse().getContentAsString(), ApprovalView.class);
        assertEquals(v1.id(), v2.id());

        // Calling again with NEW idempotency key on already decided proposal -> 409 Conflict
        mockMvc.perform(post("/api/v1/proposals/" + prop.id() + "/decision")
                .header("Authorization", "Bearer dev-owner")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
    }

    @Test
    @DisplayName("Scenario 8: Rejected proposals cannot be treated as approved")
    void test08_rejectedProposalsCannotBeTreatedAsApproved() throws Exception {
        EvaluationFixture fix = createReadyEvaluation("Scenario8-Supplier");
        ProposalView prop = createAwardProposal(fix);

        DecisionCreate reject = new DecisionCreate("REJECT", "Quoted price is 20% higher than historical benchmark", prop.snapshotHash());

        mockMvc.perform(post("/api/v1/proposals/" + prop.id() + "/decision")
                .header("Authorization", "Bearer dev-owner")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(reject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REJECT"));

        // Verify DB: proposal is REJECTED
        String propStatus = db.queryForObject("SELECT status FROM commercial_proposals WHERE id = ?", String.class, prop.id());
        assertEquals("REJECTED", propStatus);

        // Commercial entity remains unawarded
        String rfqStatus = db.queryForObject("SELECT status FROM rfqs WHERE id = ?", String.class, fix.rfqId());
        assertEquals("OPEN", rfqStatus);
        UUID awarded = db.queryForObject("SELECT awarded_quote_id FROM rfqs WHERE id = ?", UUID.class, fix.rfqId());
        assertNull(awarded);

        // Subsequent attempt to approve rejected proposal -> 409 Conflict
        DecisionCreate tryApprove = new DecisionCreate("APPROVE", "Attempting to reverse rejection", prop.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + prop.id() + "/decision")
                .header("Authorization", "Bearer dev-owner")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(tryApprove)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Scenario 9: Only ApprovalService performs commercial state transitions")
    void test09_soleCommercialWriterEnforced() throws Exception {
        EvaluationFixture fix = createReadyEvaluation("Scenario9-Supplier");
        ProposalView prop = createAwardProposal(fix);

        DecisionCreate dec = new DecisionCreate("APPROVE", "Direct call", prop.snapshotHash());

        // Direct call to ApprovalService with PROCUREMENT_OPERATOR role throws SecurityException
        assertThrows(SecurityException.class, () ->
                approvalService.decide(prop.id(), dec, "PROCUREMENT_OPERATOR", "test-op")
        );

        // Direct call with READ_ONLY_EVALUATOR role throws SecurityException
        assertThrows(SecurityException.class, () ->
                approvalService.decide(prop.id(), dec, "READ_ONLY_EVALUATOR", "test-eval")
        );

        // Proposal remains untouched
        String propStatus = db.queryForObject("SELECT status FROM commercial_proposals WHERE id = ?", String.class, prop.id());
        assertEquals("PENDING", propStatus);
    }

    @Test
    @DisplayName("Scenario 10: Approval and audit records persist together atomically")
    void test10_approvalAndAuditPersistTogether() throws Exception {
        EvaluationFixture fix = createReadyEvaluation("Scenario10-Supplier");

        int initialAuditCount = db.queryForObject("SELECT count(*) FROM audit_events WHERE entity_id = ?", Integer.class, fix.evaluationId());

        ProposalView prop = createAwardProposal(fix);

        // Proposal creation produced an audit event
        int auditAfterProp = db.queryForObject("SELECT count(*) FROM audit_events WHERE entity_id = ?", Integer.class, prop.id());
        assertEquals(1, auditAfterProp);
        String action1 = db.queryForObject("SELECT action FROM audit_events WHERE entity_id = ?", String.class, prop.id());
        assertEquals("PROPOSAL_CREATED", action1);

        // Now decide APPROVE
        DecisionCreate decision = new DecisionCreate("APPROVE", "Audit verified approval", prop.snapshotHash());
        MvcResult res = mockMvc.perform(post("/api/v1/proposals/" + prop.id() + "/decision")
                .header("Authorization", "Bearer dev-owner")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andReturn();
        ApprovalView approvalView = mapper.readValue(res.getResponse().getContentAsString(), ApprovalView.class);

        // Verify approvals table has append-only row
        int approvalCount = db.queryForObject("SELECT count(*) FROM approvals WHERE proposal_id = ?", Integer.class, prop.id());
        assertEquals(1, approvalCount);
        String recordedStatus = db.queryForObject("SELECT decision_status FROM approvals WHERE proposal_id = ?", String.class, prop.id());
        assertEquals("APPROVED", recordedStatus);

        // Verify audit_events table has PROPOSAL_APPROVED record
        var auditRows = db.query(
                "SELECT action, after_hash FROM audit_events WHERE entity_id = ? ORDER BY occurred_at DESC",
                (rs, n) -> Map.of("action", rs.getString("action"), "after", rs.getString("after_hash")),
                prop.id()
        );
        assertEquals(2, auditRows.size());
        assertEquals("PROPOSAL_APPROVED", auditRows.get(0).get("action"));
        assertEquals(prop.snapshotHash(), auditRows.get(0).get("after"));
    }

    @Test
    @DisplayName("Scenario 11: Competing proposals are atomically invalidated to STALE")
    void test11_competingProposalsInvalidatedToStale() throws Exception {
        UUID materialId = getSeededMaterialId();
        UUID rfqId = db.queryForObject(
                "INSERT INTO rfqs(material_id, specification, quantity, unit, required_procurement_delivery_at, reference_at, timezone, allow_split_sourcing, status) " +
                        "VALUES (?, '2 mm IS 2062 sheet', 500.0000, 'kg', now() + interval '3 days', now(), 'Asia/Kolkata', false, 'OPEN') RETURNING id",
                UUID.class, materialId
        );

        // Create Supplier A and Quote A
        UUID suppA = db.queryForObject("INSERT INTO suppliers(name, location, is_synthetic) VALUES ('Supplier Alpha', 'Loc A', true) RETURNING id", UUID.class);
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> fieldsA = standardQuoteFields(now, "95.00", "1500.00");
        QuoteCreate qcA = new QuoteCreate(suppA, now, "Alpha quote", fieldsA);
        MvcResult resA = mockMvc.perform(post("/api/v1/rfqs/" + rfqId + "/quotes")
                .header("Authorization", "Bearer dev-procurement").header("Idempotency-Key", idempotencyKey()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(qcA)))
                .andExpect(status().isCreated()).andReturn();
        QuoteView qvA = mapper.readValue(resA.getResponse().getContentAsString(), QuoteView.class);

        // Create Supplier B and Quote B
        UUID suppB = db.queryForObject("INSERT INTO suppliers(name, location, is_synthetic) VALUES ('Supplier Beta', 'Loc B', true) RETURNING id", UUID.class);
        Map<String, Object> fieldsB = standardQuoteFields(now, "98.00", "1200.00");
        QuoteCreate qcB = new QuoteCreate(suppB, now, "Beta quote", fieldsB);
        MvcResult resB = mockMvc.perform(post("/api/v1/rfqs/" + rfqId + "/quotes")
                .header("Authorization", "Bearer dev-procurement").header("Idempotency-Key", idempotencyKey()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(qcB)))
                .andExpect(status().isCreated()).andReturn();
        QuoteView qvB = mapper.readValue(resB.getResponse().getContentAsString(), QuoteView.class);

        // Evaluate Quote A
        EvaluationCreate ecA = new EvaluationCreate(List.of(qvA.revisionId()), 1);
        MvcResult evalResA = mockMvc.perform(post("/api/v1/rfqs/" + rfqId + "/evaluations")
                .header("Authorization", "Bearer dev-procurement").header("Idempotency-Key", idempotencyKey()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(ecA)))
                .andExpect(status().isCreated()).andReturn();
        EvaluationView evA = mapper.readValue(evalResA.getResponse().getContentAsString(), EvaluationView.class);

        // Evaluate Quote B
        EvaluationCreate ecB = new EvaluationCreate(List.of(qvB.revisionId()), 1);
        MvcResult evalResB = mockMvc.perform(post("/api/v1/rfqs/" + rfqId + "/evaluations")
                .header("Authorization", "Bearer dev-procurement").header("Idempotency-Key", idempotencyKey()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(ecB)))
                .andExpect(status().isCreated()).andReturn();
        EvaluationView evB = mapper.readValue(evalResB.getResponse().getContentAsString(), EvaluationView.class);

        // Create Proposal A
        AwardProposalCreate apA = new AwardProposalCreate(qvA.revisionId(), evA.snapshotSha256());
        MvcResult propResA = mockMvc.perform(post("/api/v1/evaluations/" + evA.id() + "/award-proposals")
                .header("Authorization", "Bearer dev-procurement").header("Idempotency-Key", idempotencyKey()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(apA)))
                .andExpect(status().isCreated()).andReturn();
        ProposalView propA = mapper.readValue(propResA.getResponse().getContentAsString(), ProposalView.class);

        // Create Proposal B
        AwardProposalCreate apB = new AwardProposalCreate(qvB.revisionId(), evB.snapshotSha256());
        MvcResult propResB = mockMvc.perform(post("/api/v1/evaluations/" + evB.id() + "/award-proposals")
                .header("Authorization", "Bearer dev-procurement").header("Idempotency-Key", idempotencyKey()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(apB)))
                .andExpect(status().isCreated()).andReturn();
        ProposalView propB = mapper.readValue(propResB.getResponse().getContentAsString(), ProposalView.class);

        assertEquals("PENDING", propA.status());
        assertEquals("PENDING", propB.status());

        // Approve Proposal A
        DecisionCreate decA = new DecisionCreate("APPROVE", "Alpha chosen", propA.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + propA.id() + "/decision")
                .header("Authorization", "Bearer dev-owner").header("Idempotency-Key", idempotencyKey()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(decA)))
                .andExpect(status().isOk());

        // Verify Proposal A is APPROVED
        String statusA = db.queryForObject("SELECT status FROM commercial_proposals WHERE id = ?", String.class, propA.id());
        assertEquals("APPROVED", statusA);

        // Verify Proposal B was atomically marked STALE with reason COMPETING_SUPPLIER_AWARDED
        String statusB = db.queryForObject("SELECT status FROM commercial_proposals WHERE id = ?", String.class, propB.id());
        String reasonB = db.queryForObject("SELECT invalidation_reason FROM commercial_proposals WHERE id = ?", String.class, propB.id());
        assertEquals("STALE", statusB);
        assertEquals("COMPETING_SUPPLIER_AWARDED", reasonB);

        // Attempting to approve Proposal B returns 409 Conflict
        DecisionCreate decB = new DecisionCreate("APPROVE", "Beta cannot be approved", propB.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + propB.id() + "/decision")
                .header("Authorization", "Bearer dev-owner").header("Idempotency-Key", idempotencyKey()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(decB)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
    }

    @Test
    @DisplayName("Scenario 12: Failed approval transaction does not partially update commercial records")
    void test12_failedTransactionRollsBackCleanly() throws Exception {
        EvaluationFixture fix = createReadyEvaluation("Scenario12-Supplier");
        ProposalView prop = createAwardProposal(fix);

        // Simulate expired quote validity window in database
        OffsetDateTime past = OffsetDateTime.now().minusDays(2);
        OffsetDateTime pastUntil = OffsetDateTime.now().minusDays(1);
        db.update("UPDATE supplier_quote_revisions SET fields = jsonb_set(jsonb_set(fields, '{validFrom}', ?::jsonb), '{validUntil}', ?::jsonb) WHERE id = ?",
                "\"" + past + "\"", "\"" + pastUntil + "\"", fix.quoteRevisionId());

        DecisionCreate decision = new DecisionCreate("APPROVE", "Attempting approval of expired quote", prop.snapshotHash());

        // Attempt decision -> returns 409 Conflict due to expired validity window
        mockMvc.perform(post("/api/v1/proposals/" + prop.id() + "/decision")
                .header("Authorization", "Bearer dev-owner")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isConflict());

        // Verify atomicity / rollback:
        // Proposal remains PENDING
        String propStatus = db.queryForObject("SELECT status FROM commercial_proposals WHERE id = ?", String.class, prop.id());
        assertEquals("PENDING", propStatus);

        // RFQ remains OPEN, not AWARDED, and awarded_quote_id is NULL
        String rfqStatus = db.queryForObject("SELECT status FROM rfqs WHERE id = ?", String.class, fix.rfqId());
        assertEquals("OPEN", rfqStatus);
        UUID awardedQuote = db.queryForObject("SELECT awarded_quote_id FROM rfqs WHERE id = ?", UUID.class, fix.rfqId());
        assertNull(awardedQuote);

        // No record written to approvals table
        int approvalCount = db.queryForObject("SELECT count(*) FROM approvals WHERE proposal_id = ?", Integer.class, prop.id());
        assertEquals(0, approvalCount);
    }

    @Test
    @DisplayName("Scenario 13: Preflight OPTIONS request with allowed origin returns 200 OK and valid CORS headers")
    void test13_preflightOptionsWithAllowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/proposals")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().exists("Access-Control-Allow-Methods"))
                .andExpect(header().exists("Access-Control-Allow-Headers"));
    }

    @Test
    @DisplayName("Scenario 14: Preflight OPTIONS request with disallowed origin returns 403 Forbidden and denies CORS")
    void test14_preflightOptionsWithDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/proposals")
                .header("Origin", "https://unauthorized-attacker.example.com")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(jsonPath("$.code").value("CORS_ORIGIN_DENIED"));
    }

    @Test
    @DisplayName("Scenario 15: Unauthenticated 401 request with allowed origin retains RFC 9457 Problem Details and CORS headers")
    void test15_unauthenticatedRequestWithAllowedOriginRetainsProblemDetailsAndCors() throws Exception {
        mockMvc.perform(get("/api/v1/proposals")
                .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Bearer token required"));
    }

    @Test
    @DisplayName("Scenario 16: Forbidden 403 non-owner request retains RFC 9457 structure, and disallowed origin receives no CORS headers")
    void test16_forbiddenNonOwnerRequestAndDisallowedOriginProtection() throws Exception {
        UUID fakePropId = UUID.randomUUID();
        DecisionCreate decision = new DecisionCreate("APPROVE", "Unauthorized approval attempt", "fake-hash");

        // Disallowed origin without token -> 401 with no CORS header
        mockMvc.perform(get("/api/v1/materials")
                .header("Origin", "https://malicious-external-origin.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // Allowed origin with non-owner procurement token on owner decision -> 403 Forbidden with CORS header
        mockMvc.perform(post("/api/v1/proposals/" + fakePropId + "/decision")
                .header("Origin", "http://localhost:5173")
                .header("Authorization", "Bearer dev-procurement")
                .header("Idempotency-Key", idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Insufficient role"));
    }

    private Map<String, Object> standardQuoteFields(OffsetDateTime now, String unitPrice, String freight) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("unitPrice", unitPrice);
        f.put("quotedQuantity", "500");
        f.put("unit", "kg");
        f.put("priceBasis", "EXCLUSIVE");
        f.put("materialTaxRate", "18");
        f.put("materialItcStatus", "NON_CREDITABLE");
        f.put("freightBasis", "SEPARATE");
        f.put("freightAmount", freight);
        f.put("freightTaxRate", "0");
        f.put("freightPriceBasis", "EXCLUSIVE");
        f.put("freightItcStatus", "CREDITABLE");
        f.put("moq", "0");
        f.put("purchaseIncrement", "1");
        f.put("wastagePercent", "0");
        f.put("quoteQuantityBasis", "INPUT_PURCHASED");
        f.put("promisedDeliveryAt", now.plusDays(2).toString());
        f.put("validFrom", now.minusHours(1).toString());
        f.put("validUntil", now.plusDays(7).toString());
        return f;
    }
}
