package org.munim.integration;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.munim.api.ApiDtos.*;
import org.munim.profitability.CommercialDtos.*;
import org.munim.profitability.ProfitabilityDtos.OperationCostInput;
import org.munim.profitability.ProfitabilityDtos.SourcingBasis;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("postgres-test")
public class CustomerQuotationApprovalIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private ObjectMapper mapper;

    private MockMvc mockMvc;
    private UUID materialId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(new DemoAuthFilter())
                .build();

        materialId = db.queryForObject("SELECT id FROM materials LIMIT 1", UUID.class);
    }

    private String idempotencyKey() {
        return UUID.randomUUID().toString();
    }

    private record CommercialSliceFixture(
            UUID enquiryId,
            UUID bomId,
            UUID bomItemId,
            UUID costingSheetId,
            UUID quotationId
    ) {}

    private CommercialSliceFixture createDraftQuotationFixture(OffsetDateTime validUntil) throws Exception {
        // 1. Create Enquiry
        EnquiryCreate enquiryCreate = new EnquiryCreate(
                "Acme Defence Fabricators " + UUID.randomUUID().toString().substring(0, 6),
                "PO-ACME-" + UUID.randomUUID().toString().substring(0, 4),
                "procurement@acme.example.com",
                "+91-9876543210",
                OffsetDateTime.now().plusDays(45),
                "Armoured chassis assembly quote"
        );
        MvcResult enqRes = mockMvc.perform(post("/api/v1/customer-enquiries")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(enquiryCreate)))
                .andExpect(status().isCreated())
                .andReturn();
        CustomerEnquiryView enquiry = mapper.readValue(enqRes.getResponse().getContentAsString(), CustomerEnquiryView.class);

        // 2. Create BOM
        BomCreate bomCreate = new BomCreate(1, new BigDecimal("100.0000"));
        MvcResult bomRes = mockMvc.perform(post("/api/v1/customer-enquiries/" + enquiry.id() + "/boms")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bomCreate)))
                .andExpect(status().isCreated())
                .andReturn();
        BomView bom = mapper.readValue(bomRes.getResponse().getContentAsString(), BomView.class);

        // 3. Add BOM Item
        BomItemCreate itemCreate = new BomItemCreate(
                materialId,
                "Bracket Base 5mm",
                new BigDecimal("2.5000"),
                "kg",
                new BigDecimal("10.00"),
                true,
                new BigDecimal("25.00"),
                SourcingBasis.ESTIMATE,
                null,
                new BigDecimal("95.00"),
                "Market benchmark verified with local distributor"
        );
        MvcResult itemRes = mockMvc.perform(post("/api/v1/boms/" + bom.id() + "/items")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(itemCreate)))
                .andExpect(status().isCreated())
                .andReturn();
        BomItemView item = mapper.readValue(itemRes.getResponse().getContentAsString(), BomItemView.class);

        // 4. Costing Run
        CostingRunRequest costingRun = new CostingRunRequest(
                List.of(new OperationCostInput(
                        "CNC Laser Cut & Deburr",
                        new BigDecimal("1.5000"),
                        new BigDecimal("600.00"),
                        new BigDecimal("0.1000"),
                        new BigDecimal("900.00"),
                        new BigDecimal("0.1500"),
                        new BigDecimal("350.00")
                )),
                new BigDecimal("0.20"),
                new BigDecimal("15.00"),
                new BigDecimal("25.00"),
                new BigDecimal("0.2000"),
                new BigDecimal("0.1000"),
                new BigDecimal("650.00"),
                new BigDecimal("18.00"),
                List.of(new ItemTaxInput(item.id(), "CREDITABLE", new BigDecimal("18.00")))
        );
        MvcResult costRes = mockMvc.perform(post("/api/v1/boms/" + bom.id() + "/costings")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(costingRun)))
                .andExpect(status().isCreated())
                .andReturn();
        CostingSheetView costing = mapper.readValue(costRes.getResponse().getContentAsString(), CostingSheetView.class);

        // 5. Customer Quotation (status: DRAFT)
        OffsetDateTime from = OffsetDateTime.now().minusHours(1);
        OffsetDateTime until = validUntil != null ? validUntil : OffsetDateTime.now().plusDays(30);
        QuotationCreate quotationCreate = new QuotationCreate(
                costing.id(),
                from,
                until,
                "50% Advance, 50% on Delivery",
                new BigDecimal("650.00"),
                new BigDecimal("18.00")
        );
        MvcResult quoteRes = mockMvc.perform(post("/api/v1/customer-quotations")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(quotationCreate)))
                .andExpect(status().isCreated())
                .andReturn();
        CustomerQuotationView quotation = mapper.readValue(quoteRes.getResponse().getContentAsString(), CustomerQuotationView.class);

        return new CommercialSliceFixture(enquiry.id(), bom.id(), item.id(), costing.id(), quotation.id());
    }

    @Test
    @DisplayName("Safeguard 1 & 3: Customer quotation approval happy path workflow with immutable snapshot and audit trail")
    void customerQuotationApprovalHappyPathWorkflow() throws Exception {
        CommercialSliceFixture fix = createDraftQuotationFixture(null);

        // 1. Submit quotation for approval
        MvcResult subRes = mockMvc.perform(post("/api/v1/customer-quotations/" + fix.quotationId() + "/submit-approval")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();

        ProposalView proposal = mapper.readValue(subRes.getResponse().getContentAsString(), ProposalView.class);
        assertNotNull(proposal.id());
        assertEquals("CUSTOMER_QUOTE", proposal.type());
        assertEquals("PENDING", proposal.status());
        assertNotNull(proposal.snapshotHash());

        // Verify quotation status in DB transitioned to PENDING_APPROVAL and commercial_proposal_id set
        CustomerQuotationView qView = mapper.readValue(
                mockMvc.perform(get("/api/v1/customer-quotations/" + fix.quotationId())
                                .header("Authorization", "Bearer dev-procurement"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                CustomerQuotationView.class
        );
        assertEquals("PENDING_APPROVAL", qView.approvalStatus());
        assertEquals(proposal.id(), qView.commercialProposalId());

        // 2. Owner lists approvals queue
        MvcResult listRes = mockMvc.perform(get("/api/v1/approvals?status=PENDING")
                        .header("Authorization", "Bearer dev-owner"))
                .andExpect(status().isOk())
                .andReturn();
        ApprovalPage page = mapper.readValue(listRes.getResponse().getContentAsString(), ApprovalPage.class);
        assertTrue(page.total() > 0);
        ApprovalQueueItem queueItem = page.items().stream()
                .filter(i -> i.proposal().id().equals(proposal.id()))
                .findFirst()
                .orElseThrow();
        assertEquals("CUSTOMER_QUOTE", queueItem.proposal().type());
        assertTrue(queueItem.summary().containsKey("customerName"));
        assertTrue(queueItem.summary().containsKey("sellingPrice"));
        assertTrue(queueItem.summary().containsKey("marginClassification"));

        // 3. Owner gets approval detail
        MvcResult detailRes = mockMvc.perform(get("/api/v1/approvals/" + proposal.id())
                        .header("Authorization", "Bearer dev-owner"))
                .andExpect(status().isOk())
                .andReturn();
        ApprovalDetail detail = mapper.readValue(detailRes.getResponse().getContentAsString(), ApprovalDetail.class);
        assertEquals(proposal.id(), detail.proposal().id());
        Map<String, Object> snapshot = detail.snapshot();
        assertEquals("CUSTOMER_QUOTE", snapshot.get("proposalType"));
        assertEquals(fix.quotationId().toString(), snapshot.get("quotationId"));
        assertEquals(fix.costingSheetId().toString(), snapshot.get("costingSheetId"));
        assertNotNull(snapshot.get("sellingPrice"));
        assertNotNull(snapshot.get("totalCost"));
        assertNotNull(snapshot.get("customerPayable"));

        // 4. Owner approves proposal
        DecisionCreate approveDecision = new DecisionCreate(
                "APPROVE",
                "Approved for production under agreed margin",
                proposal.snapshotHash()
        );
        MvcResult decideRes = mockMvc.perform(post("/api/v1/proposals/" + proposal.id() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(approveDecision)))
                .andExpect(status().isOk())
                .andReturn();
        ApprovalView approval = mapper.readValue(decideRes.getResponse().getContentAsString(), ApprovalView.class);
        assertEquals("APPROVE", approval.decision());
        assertEquals(proposal.snapshotHash(), approval.snapshotHash());

        // 5. Verify database states after approval
        String proposalStatus = db.queryForObject(
                "SELECT status FROM commercial_proposals WHERE id = ?", String.class, proposal.id()
        );
        assertEquals("APPROVED", proposalStatus);

        String quotationStatus = db.queryForObject(
                "SELECT approval_status FROM customer_quotations WHERE id = ?", String.class, fix.quotationId()
        );
        assertEquals("APPROVED", quotationStatus);

        // Verify record in approvals table
        int approvalCount = db.queryForObject(
                "SELECT count(*) FROM approvals WHERE proposal_id = ? AND decision_status = 'APPROVED'",
                Integer.class, proposal.id()
        );
        assertEquals(1, approvalCount);

        // Verify immutable audit event
        int auditCount = db.queryForObject(
                "SELECT count(*) FROM audit_events WHERE entity_id = ? AND action = 'PROPOSAL_APPROVED'",
                Integer.class, proposal.id()
        );
        assertEquals(1, auditCount);
    }

    @Test
    @DisplayName("Safeguard 1 & 3: Customer quotation rejection workflow with mandatory documented reason")
    void customerQuotationRejectionWorkflow() throws Exception {
        CommercialSliceFixture fix = createDraftQuotationFixture(null);

        // Submit quotation for approval
        MvcResult subRes = mockMvc.perform(post("/api/v1/customer-quotations/" + fix.quotationId() + "/submit-approval")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        ProposalView proposal = mapper.readValue(subRes.getResponse().getContentAsString(), ProposalView.class);

        // Owner rejects with reason
        DecisionCreate rejectDecision = new DecisionCreate(
                "REJECT",
                "Tooling setup time too high for 100 batch quantity",
                proposal.snapshotHash()
        );
        MvcResult decideRes = mockMvc.perform(post("/api/v1/proposals/" + proposal.id() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(rejectDecision)))
                .andExpect(status().isOk())
                .andReturn();
        ApprovalView approval = mapper.readValue(decideRes.getResponse().getContentAsString(), ApprovalView.class);
        assertEquals("REJECT", approval.decision());

        // Verify proposal in DB is REJECTED
        String propStatus = db.queryForObject("SELECT status FROM commercial_proposals WHERE id = ?", String.class, proposal.id());
        assertEquals("REJECTED", propStatus);

        // Verify quotation in DB is REJECTED
        String qStatus = db.queryForObject("SELECT approval_status FROM customer_quotations WHERE id = ?", String.class, fix.quotationId());
        assertEquals("REJECTED", qStatus);

        // Verify audit event contains rejection reason
        String reason = db.queryForObject(
                "SELECT reason FROM audit_events WHERE entity_id = ? AND action = 'PROPOSAL_REJECTED'",
                String.class, proposal.id()
        );
        assertEquals("Tooling setup time too high for 100 batch quantity", reason);
    }

    @Test
    @DisplayName("Safeguard 3: Rejection without mandatory reason is rejected with 400 Bad Request")
    void rejectionWithoutReasonIsRejected() throws Exception {
        CommercialSliceFixture fix = createDraftQuotationFixture(null);

        MvcResult subRes = mockMvc.perform(post("/api/v1/customer-quotations/" + fix.quotationId() + "/submit-approval")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        ProposalView proposal = mapper.readValue(subRes.getResponse().getContentAsString(), ProposalView.class);

        // Rejection with null / blank reason
        DecisionCreate invalidDecision = new DecisionCreate("REJECT", "", proposal.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + proposal.id() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(invalidDecision)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Safeguard 3: Non-owner role cannot decide commercial proposal (403 Forbidden)")
    void nonOwnerCannotDecideProposal() throws Exception {
        CommercialSliceFixture fix = createDraftQuotationFixture(null);

        MvcResult subRes = mockMvc.perform(post("/api/v1/customer-quotations/" + fix.quotationId() + "/submit-approval")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        ProposalView proposal = mapper.readValue(subRes.getResponse().getContentAsString(), ProposalView.class);

        DecisionCreate decision = new DecisionCreate("APPROVE", "Attempted by procurement", proposal.snapshotHash());

        // Procurement operator rejected
        mockMvc.perform(post("/api/v1/proposals/" + proposal.id() + "/decision")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isForbidden());

        // Read-only evaluator rejected
        mockMvc.perform(post("/api/v1/proposals/" + proposal.id() + "/decision")
                        .header("Authorization", "Bearer dev-evaluator")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Safeguard 2: Stale detection rejects decision with hash mismatch (409 Conflict)")
    void staleDetectionRejectsHashMismatch() throws Exception {
        CommercialSliceFixture fix = createDraftQuotationFixture(null);

        MvcResult subRes = mockMvc.perform(post("/api/v1/customer-quotations/" + fix.quotationId() + "/submit-approval")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        ProposalView proposal = mapper.readValue(subRes.getResponse().getContentAsString(), ProposalView.class);

        // Incorrect expected snapshot hash
        DecisionCreate tampered = new DecisionCreate(
                "APPROVE",
                "Approved with stale hash",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        );
        mockMvc.perform(post("/api/v1/proposals/" + proposal.id() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(tampered)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Safeguard 2: Stale detection rejects expired quotation at decision time")
    void staleDetectionRejectsExpiredQuotation() throws Exception {
        // Create fixture with validity that expires in 1 second
        CommercialSliceFixture fix = createDraftQuotationFixture(OffsetDateTime.now().plusSeconds(1));

        MvcResult subRes = mockMvc.perform(post("/api/v1/customer-quotations/" + fix.quotationId() + "/submit-approval")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        ProposalView proposal = mapper.readValue(subRes.getResponse().getContentAsString(), ProposalView.class);

        // Update database valid_until to 1 hour in the past to simulate expiry
        db.update("UPDATE customer_quotations SET valid_from = now() - interval '2 hours', valid_until = now() - interval '1 hour' WHERE id = ?", fix.quotationId());

        DecisionCreate decision = new DecisionCreate("APPROVE", "Approved past deadline", proposal.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + proposal.id() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Safeguard 2: Cannot decide an already decided proposal (409 Conflict)")
    void cannotDecideAlreadyDecidedProposal() throws Exception {
        CommercialSliceFixture fix = createDraftQuotationFixture(null);

        MvcResult subRes = mockMvc.perform(post("/api/v1/customer-quotations/" + fix.quotationId() + "/submit-approval")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        ProposalView proposal = mapper.readValue(subRes.getResponse().getContentAsString(), ProposalView.class);

        DecisionCreate decision = new DecisionCreate("APPROVE", "First decision", proposal.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + proposal.id() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isOk());

        // Subsequent attempt must fail with 409 Conflict
        mockMvc.perform(post("/api/v1/proposals/" + proposal.id() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PRD & OpenAPI Parity: Create quote proposal from cost sheet endpoint")
    void costSheetQuoteProposalEndpointWorkflow() throws Exception {
        CommercialSliceFixture fix = createDraftQuotationFixture(null);

        // Submit proposal from costing sheet endpoint
        CustomerProposalCreate propCreate = new CustomerProposalCreate(
                1,
                "675.00",
                OffsetDateTime.now().plusDays(20)
        );

        MvcResult propRes = mockMvc.perform(post("/api/v1/cost-sheets/" + fix.costingSheetId() + "/quote-proposals")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(propCreate)))
                .andExpect(status().isCreated())
                .andReturn();

        ProposalView proposal = mapper.readValue(propRes.getResponse().getContentAsString(), ProposalView.class);
        assertEquals("CUSTOMER_QUOTE", proposal.type());
        assertEquals("PENDING", proposal.status());
        assertNotNull(proposal.snapshotHash());

        // Owner approves
        DecisionCreate approve = new DecisionCreate("APPROVE", "Approved via cost-sheet proposal flow", proposal.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + proposal.id() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(approve)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Safeguard 2: Submitting an already APPROVED quotation for approval is rejected (409 Conflict)")
    void submittingAlreadyApprovedQuotationIsRejected() throws Exception {
        CommercialSliceFixture fix = createDraftQuotationFixture(null);

        // Submit and approve quotation
        MvcResult subRes = mockMvc.perform(post("/api/v1/customer-quotations/" + fix.quotationId() + "/submit-approval")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        ProposalView proposal = mapper.readValue(subRes.getResponse().getContentAsString(), ProposalView.class);

        DecisionCreate approve = new DecisionCreate("APPROVE", "First approval", proposal.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + proposal.id() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(approve)))
                .andExpect(status().isOk());

        // Attempting to submit the approved quotation again
        mockMvc.perform(post("/api/v1/customer-quotations/" + fix.quotationId() + "/submit-approval")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Safeguard 2: Competing proposals for same quotation are marked STALE upon approval")
    void competingProposalsAreStaledOnApproval() throws Exception {
        CommercialSliceFixture fix = createDraftQuotationFixture(null);

        // Create first proposal
        MvcResult subRes1 = mockMvc.perform(post("/api/v1/customer-quotations/" + fix.quotationId() + "/submit-approval")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        ProposalView proposal1 = mapper.readValue(subRes1.getResponse().getContentAsString(), ProposalView.class);

        // Manually insert competing proposal referencing the same quotation
        UUID proposal2Id = db.queryForObject(
                "INSERT INTO commercial_proposals(" +
                        "type, entity_type, entity_id, cost_sheet_id, " +
                        "requested_action, requesting_user, status, " +
                        "snapshot_payload, snapshot_hash, policy_version" +
                        ") VALUES ('CUSTOMER_QUOTE', 'CUSTOMER_QUOTATION', ?, ?, 'APPROVE_CUSTOMER_QUOTE', 'dev-procurement', 'PENDING', " +
                        "'{\"test\":\"competing\"}'::jsonb, 'dummyhash', 1) RETURNING id",
                UUID.class,
                fix.quotationId(), fix.costingSheetId()
        );

        // Approve proposal 1
        DecisionCreate approve = new DecisionCreate("APPROVE", "Approved primary proposal", proposal1.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + proposal1.id() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(approve)))
                .andExpect(status().isOk());

        // Verify competing proposal 2 was marked STALE
        var p2Row = db.queryForMap("SELECT status, invalidation_reason FROM commercial_proposals WHERE id = ?", proposal2Id);
        assertEquals("STALE", p2Row.get("status"));
        assertEquals("SUPERSEDED_BY_APPROVED_PROPOSAL", p2Row.get("invalidation_reason"));
    }
}
