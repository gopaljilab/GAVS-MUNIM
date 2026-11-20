package org.munim.integration;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.munim.api.ApiDtos.ApprovalView;
import org.munim.api.ApiDtos.DecisionCreate;
import org.munim.api.ApiDtos.ProposalView;
import org.munim.api.ApiDtos.QuotationApprovalSubmission;
import org.munim.intelligence.SupplierIntelligenceDtos.*;
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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("postgres-test")
public class PurchaseOutcomeIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private ObjectMapper mapper;

    private MockMvc mockMvc;

    private UUID materialId;
    private UUID supplierId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(new DemoAuthFilter())
                .build();

        materialId = db.queryForObject("SELECT id FROM materials LIMIT 1", UUID.class);

        supplierId = db.queryForObject(
                "INSERT INTO suppliers(name, location, is_synthetic) VALUES ('Jindal Aluminium Works', 'Bangalore', true) RETURNING id",
                UUID.class
        );
    }

    private AwardFixture setupEvaluatedRfqAndAwardProposal(BigDecimal rfqQty, BigDecimal unitPrice, BigDecimal freight) {
        UUID rfqId = db.queryForObject(
                "INSERT INTO rfqs(material_id, specification, quantity, unit, production_lead_days, required_procurement_delivery_at, reference_at, timezone, allow_split_sourcing, status) " +
                        "VALUES (?, 'Aluminium Alloy 6061-T6', ?, 'kg', 5, now() + interval '15 days', now(), 'Asia/Kolkata', false, 'EVALUATED') " +
                        "RETURNING id",
                UUID.class, materialId, rfqQty
        );

        UUID quoteId = db.queryForObject(
                "INSERT INTO supplier_quotes(rfq_id, supplier_id) VALUES (?, ?) RETURNING id",
                UUID.class, rfqId, supplierId
        );

        OffsetDateTime validUntil = OffsetDateTime.now().plusDays(30);
        Map<String, Object> fields = Map.ofEntries(
                Map.entry("unitPrice", unitPrice.setScale(2, RoundingMode.HALF_UP).toPlainString()),
                Map.entry("priceBasis", "EXCLUSIVE"),
                Map.entry("materialTaxRate", "18.00"),
                Map.entry("materialItcStatus", "CREDITABLE"),
                Map.entry("wastagePercent", "0"),
                Map.entry("quotedQuantity", rfqQty.setScale(4, RoundingMode.HALF_UP).toPlainString()),
                Map.entry("purchaseIncrement", "1"),
                Map.entry("moq", "100"),
                Map.entry("freightAmount", freight.setScale(2, RoundingMode.HALF_UP).toPlainString()),
                Map.entry("freightBasis", "SEPARATE"),
                Map.entry("freightPriceBasis", "EXCLUSIVE"),
                Map.entry("freightTaxRate", "18.00"),
                Map.entry("freightItcStatus", "CREDITABLE"),
                Map.entry("quoteQuantityBasis", "INPUT_PURCHASED"),
                Map.entry("validFrom", OffsetDateTime.now().minusDays(1).toString()),
                Map.entry("validUntil", validUntil.toString()),
                Map.entry("promisedDeliveryAt", OffsetDateTime.now().plusDays(5).toString())
        );

        String fieldsJson;
        try {
            fieldsJson = mapper.writeValueAsString(fields);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        UUID quoteRevId = db.queryForObject(
                "INSERT INTO supplier_quote_revisions(supplier_quote_id, rfq_id, revision_no, state, received_at, raw_message, raw_message_sha256, fields, completeness_status) " +
                        "VALUES (?, ?, 1, 'VERIFIED', now(), 'Raw Quote', 'sha256raw', ?::jsonb, 'COMPLETE') " +
                        "RETURNING id",
                UUID.class, quoteId, rfqId, fieldsJson
        );

        db.update("UPDATE supplier_quotes SET current_revision_id = ? WHERE id = ?", quoteRevId, quoteId);

        BigDecimal matBase = unitPrice.multiply(rfqQty).setScale(2, RoundingMode.HALF_UP);
        BigDecimal matGst = matBase.multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal frtGst = freight.multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal econ = matBase.add(freight);
        BigDecimal cash = matBase.add(matGst).add(freight).add(frtGst);

        UUID evalId = db.queryForObject(
                "INSERT INTO quote_evaluations(rfq_id, quote_revision_id, reference_at, calculation_version, input_snapshot, snapshot_sha256, status, result) " +
                        "VALUES (?, ?, now(), '1.1', '{}'::jsonb, 'evalhash123', 'COMPLETE', '{}'::jsonb) RETURNING id",
                UUID.class, rfqId, quoteRevId
        );

        db.queryForObject(
                "INSERT INTO evaluation_lines(evaluation_id, quote_revision_id, material_base, material_gst, freight_base, freight_gst, other_charges, economic_landed_cost, cash_payable, currency) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 0.00, ?, ?, 'INR') RETURNING id",
                UUID.class, evalId, quoteRevId, matBase, matGst, freight, frtGst, econ, cash
        );

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("rfqId", rfqId.toString());
        snapshot.put("quoteRevisionId", quoteRevId.toString());
        snapshot.put("evaluationId", evalId.toString());
        snapshot.put("economicLandedCost", econ.toPlainString());
        String snapshotText;
        try {
            snapshotText = mapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String snapshotHash = sha256Hex(snapshotText);

        UUID proposalId = db.queryForObject(
                "INSERT INTO commercial_proposals(type, entity_type, entity_id, evaluation_id, quote_revision_id, requested_action, requesting_user, status, snapshot_payload, snapshot_hash, policy_version) " +
                        "VALUES ('SUPPLIER_AWARD', 'RFQ', ?, ?, ?, 'APPROVE_SUPPLIER_AWARD', 'dev-procurement', 'PENDING', ?::jsonb, ?, 1) RETURNING id",
                UUID.class, rfqId, evalId, quoteRevId, snapshotText, snapshotHash
        );

        return new AwardFixture(rfqId, quoteId, quoteRevId, evalId, proposalId, snapshotHash);
    }

    private record AwardFixture(UUID rfqId, UUID quoteId, UUID quoteRevId, UUID evalId, UUID proposalId, String snapshotHash) {}

    // =========================================================================
    // Mandatory Test 1: testAwardApprovalSuccessCreatesPo
    // =========================================================================
    @Test
    @DisplayName("1. Owner award approval succeeds: proposal APPROVED, RFQ AWARDED, PO created in ISSUED status")
    void testAwardApprovalSuccessCreatesPo() throws Exception {
        AwardFixture f = setupEvaluatedRfqAndAwardProposal(new BigDecimal("1000.0000"), new BigDecimal("120.00"), new BigDecimal("1500.00"));

        DecisionCreate input = new DecisionCreate("APPROVE", "L1 evaluated vendor approved", f.snapshotHash());

        MvcResult res = mockMvc.perform(post("/api/v1/proposals/" + f.proposalId() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", "idemp-po-create-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andReturn();

        // Verify RFQ status is AWARDED
        String rfqStatus = db.queryForObject("SELECT status FROM rfqs WHERE id = ?", String.class, f.rfqId());
        assertEquals("AWARDED", rfqStatus);

        // Verify PO created in ISSUED status
        var poRows = db.queryForList("SELECT id, po_number, ordered_quantity, agreed_unit_price, agreed_freight_amount, status, po_snapshot_hash FROM purchase_orders WHERE rfq_id = ?", f.rfqId());
        assertEquals(1, poRows.size(), "Exactly 1 PO must be created for awarded RFQ");
        Map<String, Object> po = poRows.getFirst();
        assertEquals("ISSUED", po.get("status"));
        assertTrue(po.get("po_number").toString().startsWith("PO-"));
        assertEquals(0, new BigDecimal("1000.0000").compareTo((BigDecimal) po.get("ordered_quantity")));
        assertEquals(0, new BigDecimal("120.00").compareTo((BigDecimal) po.get("agreed_unit_price")));
        assertEquals(0, new BigDecimal("1500.00").compareTo((BigDecimal) po.get("agreed_freight_amount")));
        assertNotNull(po.get("po_snapshot_hash"));
    }

    // =========================================================================
    // Mandatory Test 2: testCustomerQuoteApprovalDoesNotCreatePo
    // =========================================================================
    @Test
    @DisplayName("2. Customer quote approval does NOT create any Purchase Order")
    void testCustomerQuoteApprovalDoesNotCreatePo() throws Exception {
        EnquiryCreate enquiryReq = new EnquiryCreate(
                "Boeing India Commercial",
                "PO-BOEING-001",
                "purchase@boeing.com",
                "+91-9876543210",
                OffsetDateTime.now().plusDays(45),
                "Titanium brackets"
        );
        MvcResult enqRes = mockMvc.perform(post("/api/v1/customer-enquiries")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(enquiryReq)))
                .andExpect(status().isCreated())
                .andReturn();
        CustomerEnquiryView enq = mapper.readValue(enqRes.getResponse().getContentAsString(), CustomerEnquiryView.class);

        BomCreate bomReq = new BomCreate(1, new BigDecimal("100.0000"));
        MvcResult bomRes = mockMvc.perform(post("/api/v1/customer-enquiries/" + enq.id() + "/boms")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bomReq)))
                .andExpect(status().isCreated())
                .andReturn();
        BomView bom = mapper.readValue(bomRes.getResponse().getContentAsString(), BomView.class);

        BomItemCreate itemReq = new BomItemCreate(
                materialId, "Bracket Part A", new BigDecimal("2.0000"), "kg", BigDecimal.ZERO,
                false, null, SourcingBasis.ESTIMATE, null, new BigDecimal("100.00"), "Vendor preliminary estimate"
        );
        MvcResult itemRes = mockMvc.perform(post("/api/v1/boms/" + bom.id() + "/items")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(itemReq)))
                .andExpect(status().isCreated())
                .andReturn();
        BomItemView bomItem = mapper.readValue(itemRes.getResponse().getContentAsString(), BomItemView.class);

        CostingRunRequest costReq = new CostingRunRequest(
                List.of(new OperationCostInput("Machining", new BigDecimal("1.0000"), new BigDecimal("500.00"), new BigDecimal("0.0500"), new BigDecimal("700.00"), new BigDecimal("0.0800"), new BigDecimal("250.00"))),
                new BigDecimal("0.2000"), new BigDecimal("10.00"), new BigDecimal("15.00"), new BigDecimal("0.1000"), new BigDecimal("0.0500"),
                null, new BigDecimal("18.00"), List.of(new ItemTaxInput(bomItem.id(), "CREDITABLE", new BigDecimal("18.00")))
        );
        MvcResult costRes = mockMvc.perform(post("/api/v1/boms/" + bom.id() + "/costings")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(costReq)))
                .andExpect(status().isCreated())
                .andReturn();
        CostingSheetView costing = mapper.readValue(costRes.getResponse().getContentAsString(), CostingSheetView.class);

        QuotationCreate quoteReq = new QuotationCreate(
                costing.id(), OffsetDateTime.now().minusHours(1), OffsetDateTime.now().plusDays(30),
                "Net 30", costing.targetPrice(), new BigDecimal("18.00")
        );
        MvcResult quoteRes = mockMvc.perform(post("/api/v1/customer-quotations")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(quoteReq)))
                .andExpect(status().isCreated())
                .andReturn();
        CustomerQuotationView quotation = mapper.readValue(quoteRes.getResponse().getContentAsString(), CustomerQuotationView.class);

        QuotationApprovalSubmission submissionReq = new QuotationApprovalSubmission(costing.snapshotSha256(), "Submit for owner review");
        MvcResult propRes = mockMvc.perform(post("/api/v1/customer-quotations/" + quotation.id() + "/submit-approval")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(submissionReq)))
                .andExpect(status().isCreated())
                .andReturn();
        ProposalView proposal = mapper.readValue(propRes.getResponse().getContentAsString(), ProposalView.class);

        int posBefore = db.queryForObject("SELECT count(*) FROM purchase_orders", Integer.class);

        DecisionCreate decision = new DecisionCreate("APPROVE", "Approved customer margin", proposal.snapshotHash());
        mockMvc.perform(post("/api/v1/approvals/" + proposal.id() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isOk());

        int posAfter = db.queryForObject("SELECT count(*) FROM purchase_orders", Integer.class);
        assertEquals(posBefore, posAfter, "Customer quote approvals must NEVER create a Purchase Order");
    }

    // =========================================================================
    // Mandatory Test 3: testPoCreationFailureRollsBackAwardApproval
    // =========================================================================
    @Test
    @DisplayName("3. Failure during PO creation rolls back award approval atomically")
    void testPoCreationFailureRollsBackAwardApproval() throws Exception {
        AwardFixture f = setupEvaluatedRfqAndAwardProposal(new BigDecimal("500.0000"), new BigDecimal("100.00"), new BigDecimal("1000.00"));

        // Corrupt unitPrice in fields to a non-numeric string after approval proposal creation
        // This causes supplierIntelligenceService.createPurchaseOrderForAwardedRfq to fail on parseDecimal,
        // triggering rollback of the AwardApprovalFacade transaction.
        db.update("UPDATE supplier_quote_revisions SET fields = jsonb_set(fields, '{unitPrice}', '\"INVALID_PRICE\"') WHERE id = ?", f.quoteRevId());

        DecisionCreate input = new DecisionCreate("APPROVE", "Will fail on PO creation unit price parse", f.snapshotHash());

        mockMvc.perform(post("/api/v1/proposals/" + f.proposalId() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", "idemp-rollback-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isBadRequest());

        // Proposal must still be PENDING due to atomic rollback
        String propStatus = db.queryForObject("SELECT status FROM commercial_proposals WHERE id = ?", String.class, f.proposalId());
        assertEquals("PENDING", propStatus);

        // RFQ must remain EVALUATED due to atomic rollback
        String rfqStatus = db.queryForObject("SELECT status FROM rfqs WHERE id = ?", String.class, f.rfqId());
        assertEquals("EVALUATED", rfqStatus);

        // Zero POs created
        int poCount = db.queryForObject("SELECT count(*) FROM purchase_orders WHERE rfq_id = ?", Integer.class, f.rfqId());
        assertEquals(0, poCount);
    }

    // =========================================================================
    // Mandatory Test 4: testDuplicateApprovalRequestIsIdempotent
    // =========================================================================
    @Test
    @DisplayName("4. Duplicate approval request with same Idempotency-Key returns original response without creating second PO")
    void testDuplicateApprovalRequestIsIdempotent() throws Exception {
        AwardFixture f = setupEvaluatedRfqAndAwardProposal(new BigDecimal("800.0000"), new BigDecimal("95.00"), new BigDecimal("800.00"));
        DecisionCreate input = new DecisionCreate("APPROVE", "Idempotent award test", f.snapshotHash());

        String key = "idemp-dup-approval-999";

        // First call
        MvcResult res1 = mockMvc.perform(post("/api/v1/proposals/" + f.proposalId() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andReturn();

        // Second call with same key
        MvcResult res2 = mockMvc.perform(post("/api/v1/proposals/" + f.proposalId() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andReturn();

        ApprovalView v1 = mapper.readValue(res1.getResponse().getContentAsString(), ApprovalView.class);
        ApprovalView v2 = mapper.readValue(res2.getResponse().getContentAsString(), ApprovalView.class);
        assertEquals(v1.id(), v2.id());
        assertEquals(v1.proposalId(), v2.proposalId());
        assertEquals(v1.decision(), v2.decision());
        assertEquals(v1.snapshotHash(), v2.snapshotHash());
        assertEquals(v1.decidedAt().toInstant(), v2.decidedAt().toInstant());
        int poCount = db.queryForObject("SELECT count(*) FROM purchase_orders WHERE rfq_id = ?", Integer.class, f.rfqId());
        assertEquals(1, poCount, "Idempotent retry must not create duplicate POs");
    }

    // =========================================================================
    // Mandatory Test 5: testDuplicatePoPreventionViaDbConstraint
    // =========================================================================
    @Test
    @DisplayName("5. Calling PO creation twice for already awarded RFQ returns existing PO idempotently")
    void testDuplicatePoPreventionViaDbConstraint() throws Exception {
        AwardFixture f = setupEvaluatedRfqAndAwardProposal(new BigDecimal("500.0000"), new BigDecimal("200.00"), new BigDecimal("500.00"));

        // Approve proposal first
        DecisionCreate input = new DecisionCreate("APPROVE", "Awarded", f.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + f.proposalId() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", "idemp-pre-award-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk());

        // Now call POST /api/v1/purchase-orders directly
        PurchaseOrderCreate poCreate = new PurchaseOrderCreate(f.rfqId(), "Direct PO issue");
        MvcResult res1 = mockMvc.perform(post("/api/v1/purchase-orders")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", "idemp-po-direct-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(poCreate)))
                .andExpect(status().isCreated())
                .andReturn();

        PurchaseOrderView po1 = mapper.readValue(res1.getResponse().getContentAsString(), PurchaseOrderView.class);

        // Call again with a different idempotency key
        MvcResult res2 = mockMvc.perform(post("/api/v1/purchase-orders")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", "idemp-po-direct-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(poCreate)))
                .andExpect(status().isCreated())
                .andReturn();

        PurchaseOrderView po2 = mapper.readValue(res2.getResponse().getContentAsString(), PurchaseOrderView.class);
        assertEquals(po1.id(), po2.id(), "Both calls must return the same PO ID");
        assertEquals(1, db.queryForObject("SELECT count(*) FROM purchase_orders WHERE rfq_id = ?", Integer.class, f.rfqId()));
    }

    // =========================================================================
    // Mandatory Test 6: testMultiStageIntakeAndOwnerVerification
    // =========================================================================
    @Test
    @DisplayName("6. Multi-stage intake: Delivery -> Quality Inspection -> Invoice Reconciliation -> Owner Verification")
    void testMultiStageIntakeAndOwnerVerification() throws Exception {
        AwardFixture f = setupEvaluatedRfqAndAwardProposal(new BigDecimal("1000.0000"), new BigDecimal("100.00"), new BigDecimal("2000.00"));
        DecisionCreate input = new DecisionCreate("APPROVE", "Awarded", f.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + f.proposalId() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", "idemp-ms-award-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk());

        UUID poId = db.queryForObject("SELECT id FROM purchase_orders WHERE rfq_id = ?", UUID.class, f.rfqId());

        // Stage 1: Storekeeper records gate delivery
        OffsetDateTime deliveryDate = OffsetDateTime.now().plusDays(2);
        DeliveryIntakeInput dIn = new DeliveryIntakeInput(deliveryDate, new BigDecimal("1000.0000"), "DC-2026-081", false);
        MvcResult resD = mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/receipts/delivery")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dIn)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stageDelivery").value("RECORDED"))
                .andExpect(jsonPath("$.receivedQuantity").value(1000.0000))
                .andExpect(jsonPath("$.deliveryStatus").value("EARLY"))
                .andReturn();

        PurchaseReceiptView receipt = mapper.readValue(resD.getResponse().getContentAsString(), PurchaseReceiptView.class);
        UUID receiptId = receipt.id();

        // Stage 2: Quality Inspection
        QualityInspectionInput qIn = new QualityInspectionInput(
                "INSPECTED", new BigDecimal("1000.0000"), new BigDecimal("980.0000"), new BigDecimal("20.0000"),
                "Surface oxidation on 20 kg", "Inspected via spectrometer"
        );
        mockMvc.perform(put("/api/v1/purchase-receipts/" + receiptId + "/quality-inspection")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(qIn)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stageQuality").value("INSPECTED"))
                .andExpect(jsonPath("$.acceptedQuantity").value(980.0000))
                .andExpect(jsonPath("$.rejectedQuantity").value(20.0000))
                .andExpect(jsonPath("$.qualityAcceptanceRate").value(98.00));

        // Stage 3: Invoice Reconciliation
        InvoiceReconciliationInput invIn = new InvoiceReconciliationInput(
                "INV-2026-8812", OffsetDateTime.now(),
                new BigDecimal("100000.00"), new BigDecimal("18000.00"),
                new BigDecimal("2000.00"), new BigDecimal("360.00"),
                new BigDecimal("500.00"), new BigDecimal("90.00"),
                new BigDecimal("18.00"), "CREDITABLE", "Unloading labour charges"
        );
        mockMvc.perform(put("/api/v1/purchase-receipts/" + receiptId + "/invoice-reconciliation")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(invIn)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stageInvoice").value("RECONCILED"))
                .andExpect(jsonPath("$.actualEconomicLandedCost").value(102500.00))
                .andExpect(jsonPath("$.actualCashPaid").value(120950.00))
                .andExpect(jsonPath("$.costVarianceAmount").value(500.00)); // 102500 - 102000 = +500

        // Stage 4 Auth: Non-owner attempt to verify fails (403 Forbidden)
        mockMvc.perform(post("/api/v1/purchase-receipts/" + receiptId + "/verify")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // Stage 4: Owner verification
        mockMvc.perform(post("/api/v1/purchase-receipts/" + receiptId + "/verify")
                        .header("Authorization", "Bearer dev-owner")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedBy").value("dev-owner"))
                .andExpect(jsonPath("$.outcomeSnapshotHash").isString());
    }

    // =========================================================================
    // Mandatory Test 7: testAdjustmentsWithSignedDeltas
    // =========================================================================
    @Test
    @DisplayName("7. Multiple adjustments with signed deltas correctly aggregate without mutating original receipt")
    void testAdjustmentsWithSignedDeltas() throws Exception {
        AwardFixture f = setupEvaluatedRfqAndAwardProposal(new BigDecimal("1000.0000"), new BigDecimal("50.00"), new BigDecimal("1000.00"));
        DecisionCreate input = new DecisionCreate("APPROVE", "Awarded", f.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + f.proposalId() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", "idemp-adj-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk());

        UUID poId = db.queryForObject("SELECT id FROM purchase_orders WHERE rfq_id = ?", UUID.class, f.rfqId());

        // Record delivery, inspection, invoice, verify
        DeliveryIntakeInput dIn = new DeliveryIntakeInput(OffsetDateTime.now(), new BigDecimal("1000.0000"), "DC-101", false);
        MvcResult resD = mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/receipts/delivery")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dIn)))
                .andExpect(status().isCreated()).andReturn();
        UUID receiptId = mapper.readValue(resD.getResponse().getContentAsString(), PurchaseReceiptView.class).id();

        QualityInspectionInput qIn = new QualityInspectionInput("INSPECTED", new BigDecimal("1000.0000"), new BigDecimal("1000.0000"), BigDecimal.ZERO, null, null);
        mockMvc.perform(put("/api/v1/purchase-receipts/" + receiptId + "/quality-inspection").header("Authorization", "Bearer dev-procurement").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(qIn))).andExpect(status().isOk());

        InvoiceReconciliationInput invIn = new InvoiceReconciliationInput("INV-101", OffsetDateTime.now(), new BigDecimal("50000.00"), new BigDecimal("9000.00"), new BigDecimal("1000.00"), new BigDecimal("180.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("18.00"), "CREDITABLE", null);
        mockMvc.perform(put("/api/v1/purchase-receipts/" + receiptId + "/invoice-reconciliation").header("Authorization", "Bearer dev-procurement").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(invIn))).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/purchase-receipts/" + receiptId + "/verify").header("Authorization", "Bearer dev-owner")).andExpect(status().isOk());

        // Adjustment 1: Physical Return of 60 kg due to latent internal cracks
        ReceiptAdjustmentInput adj1 = new ReceiptAdjustmentInput(
                "PHYSICAL_RETURN", "Latent internal cracks detected on lathe turning", "CN-2026-001",
                new BigDecimal("-60.0000"), BigDecimal.ZERO, new BigDecimal("-60.0000"), BigDecimal.ZERO,
                new BigDecimal("-3000.00"), new BigDecimal("-540.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        mockMvc.perform(post("/api/v1/purchase-receipts/" + receiptId + "/adjust")
                        .header("Authorization", "Bearer dev-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(adj1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recordType").value("CORRECTION_ADJUSTMENT"))
                .andExpect(jsonPath("$.deltaAcceptedQuantity").value(-60.0000));

        // Original receipt remains immutable
        BigDecimal origReceived = db.queryForObject("SELECT received_quantity FROM purchase_receipts WHERE id = ?", BigDecimal.class, receiptId);
        BigDecimal origAccepted = db.queryForObject("SELECT accepted_quantity FROM purchase_receipts WHERE id = ?", BigDecimal.class, receiptId);
        assertEquals(0, new BigDecimal("1000.0000").compareTo(origReceived));
        assertEquals(0, new BigDecimal("1000.0000").compareTo(origAccepted));

        // Check PO detail view aggregated quantities
        MvcResult resDetail = mockMvc.perform(get("/api/v1/purchase-orders/" + poId)
                        .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk())
                .andReturn();

        PurchaseOrderDetailView detail = mapper.readValue(resDetail.getResponse().getContentAsString(), PurchaseOrderDetailView.class);
        assertEquals(0, new BigDecimal("1000.0000").compareTo(detail.cumulativeReceivedQuantity()));
        assertEquals(0, new BigDecimal("940.0000").compareTo(detail.effectiveReceivedQuantity())); // 1000 - 60
        assertEquals(0, new BigDecimal("940.0000").compareTo(detail.cumulativeAcceptedQuantity())); // 1000 - 60
    }

    // =========================================================================
    // Mandatory Test 8: testFinancialOnlyAdjustmentDoesNotAffectPhysicalYield
    // =========================================================================
    @Test
    @DisplayName("8. Financial-only credit note updates cost but leaves physical yield strictly unchanged")
    void testFinancialOnlyAdjustmentDoesNotAffectPhysicalYield() throws Exception {
        AwardFixture f = setupEvaluatedRfqAndAwardProposal(new BigDecimal("500.0000"), new BigDecimal("80.00"), new BigDecimal("500.00"));
        DecisionCreate input = new DecisionCreate("APPROVE", "Awarded", f.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + f.proposalId() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", "idemp-fin-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk());

        UUID poId = db.queryForObject("SELECT id FROM purchase_orders WHERE rfq_id = ?", UUID.class, f.rfqId());

        // Setup verified receipt
        DeliveryIntakeInput dIn = new DeliveryIntakeInput(OffsetDateTime.now(), new BigDecimal("500.0000"), "DC-500", false);
        MvcResult resD = mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/receipts/delivery").header("Authorization", "Bearer dev-procurement").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(dIn))).andExpect(status().isCreated()).andReturn();
        UUID receiptId = mapper.readValue(resD.getResponse().getContentAsString(), PurchaseReceiptView.class).id();
        QualityInspectionInput qIn = new QualityInspectionInput("INSPECTED", new BigDecimal("500.0000"), new BigDecimal("500.0000"), BigDecimal.ZERO, null, null);
        mockMvc.perform(put("/api/v1/purchase-receipts/" + receiptId + "/quality-inspection").header("Authorization", "Bearer dev-procurement").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(qIn))).andExpect(status().isOk());
        InvoiceReconciliationInput invIn = new InvoiceReconciliationInput("INV-500", OffsetDateTime.now(), new BigDecimal("40000.00"), new BigDecimal("7200.00"), new BigDecimal("500.00"), new BigDecimal("90.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("18.00"), "CREDITABLE", null);
        mockMvc.perform(put("/api/v1/purchase-receipts/" + receiptId + "/invoice-reconciliation").header("Authorization", "Bearer dev-procurement").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(invIn))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/purchase-receipts/" + receiptId + "/verify").header("Authorization", "Bearer dev-owner")).andExpect(status().isOk());

        // Valid Financial Credit Note (0 physical deltas, negative material cost)
        ReceiptAdjustmentInput finAdj = new ReceiptAdjustmentInput(
                "FINANCIAL_CREDIT_NOTE", "Volume discount rebate per annual agreement", "CN-DISCOUNT-01",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("-2000.00"), new BigDecimal("-360.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
        mockMvc.perform(post("/api/v1/purchase-receipts/" + receiptId + "/adjust")
                        .header("Authorization", "Bearer dev-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(finAdj)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deltaMaterialBase").value(-2000.00))
                .andExpect(jsonPath("$.deltaAcceptedQuantity").value(0.0000));

        // Invalid Financial Credit Note with non-zero physical delta -> must be rejected
        ReceiptAdjustmentInput invalidFinAdj = new ReceiptAdjustmentInput(
                "FINANCIAL_CREDIT_NOTE", "Illegal attempt to combine physical and financial", "CN-FAIL",
                new BigDecimal("-10.0000"), BigDecimal.ZERO, new BigDecimal("-10.0000"), BigDecimal.ZERO,
                new BigDecimal("-500.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
        mockMvc.perform(post("/api/v1/purchase-receipts/" + receiptId + "/adjust")
                        .header("Authorization", "Bearer dev-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(invalidFinAdj)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Mandatory Test 9: testOwnerCloseShortShippedPo
    // =========================================================================
    @Test
    @DisplayName("9. Owner closes short-delivered PO with reason: status COMPLETED, po_delivery_status PO_SHORT_CLOSED, first_completed_at null")
    void testOwnerCloseShortShippedPo() throws Exception {
        AwardFixture f = setupEvaluatedRfqAndAwardProposal(new BigDecimal("1000.0000"), new BigDecimal("70.00"), new BigDecimal("1000.00"));
        DecisionCreate input = new DecisionCreate("APPROVE", "Awarded", f.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + f.proposalId() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", "idemp-close-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk());

        UUID poId = db.queryForObject("SELECT id FROM purchase_orders WHERE rfq_id = ?", UUID.class, f.rfqId());

        // Partial delivery: 850 kg
        DeliveryIntakeInput dIn = new DeliveryIntakeInput(OffsetDateTime.now(), new BigDecimal("850.0000"), "DC-850", false);
        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/receipts/delivery")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dIn)))
                .andExpect(status().isCreated());

        // Procurement operator attempt to close fails (403)
        PurchaseOrderCloseInput closeInput = new PurchaseOrderCloseInput("Supplier plant breakdown, short closed", null);
        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/close")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(closeInput)))
                .andExpect(status().isForbidden());

        // Owner closes PO
        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/close")
                        .header("Authorization", "Bearer dev-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(closeInput)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.poDeliveryStatus").value("PO_SHORT_CLOSED"))
                .andExpect(jsonPath("$.unfulfilledQuantity").value(150.0000))
                .andExpect(jsonPath("$.firstCompletedAt").doesNotExist());
    }

    // =========================================================================
    // Mandatory Test 10: testOwnerReopenShortClosedPo
    // =========================================================================
    @Test
    @DisplayName("10. Owner reopens short-closed PO: status returns to PARTIALLY_DELIVERED, unfulfilled quantity reset, audit preserved")
    void testOwnerReopenShortClosedPo() throws Exception {
        AwardFixture f = setupEvaluatedRfqAndAwardProposal(new BigDecimal("1000.0000"), new BigDecimal("70.00"), new BigDecimal("1000.00"));
        DecisionCreate input = new DecisionCreate("APPROVE", "Awarded", f.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + f.proposalId() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", "idemp-reopen-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk());

        UUID poId = db.queryForObject("SELECT id FROM purchase_orders WHERE rfq_id = ?", UUID.class, f.rfqId());

        // Partial delivery & close
        DeliveryIntakeInput dIn = new DeliveryIntakeInput(OffsetDateTime.now(), new BigDecimal("850.0000"), "DC-850", false);
        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/receipts/delivery").header("Authorization", "Bearer dev-procurement").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(dIn))).andExpect(status().isCreated());

        PurchaseOrderCloseInput closeInput = new PurchaseOrderCloseInput("Supplier plant breakdown, short closed", null);
        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/close").header("Authorization", "Bearer dev-owner").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(closeInput))).andExpect(status().isOk());

        // Reopen attempt by non-owner fails (403)
        PurchaseOrderReopenInput reopenInput = new PurchaseOrderReopenInput("Supplier restarted rolling line, balance 150kg arriving next week");
        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/reopen")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(reopenInput)))
                .andExpect(status().isForbidden());

        // Owner reopens PO
        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/reopen")
                        .header("Authorization", "Bearer dev-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(reopenInput)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_DELIVERED"))
                .andExpect(jsonPath("$.unfulfilledQuantity").value(0.0000));

        // Audit event preserved
        var audits = db.queryForList("SELECT action, metadata::text FROM audit_events WHERE entity_id = ? AND action = 'PURCHASE_ORDER_REOPENED'", poId);
        assertEquals(1, audits.size());
        assertTrue(audits.getFirst().get("metadata").toString().contains("Supplier restarted rolling line"));
    }

    // =========================================================================
    // Edge-Case Test: Unauthorized Over-Delivery (> 10%) Rejected
    // =========================================================================
    @Test
    @DisplayName("11. Unauthorized over-delivery (> 10%) without manager authorization flag is rejected")
    void testUnauthorizedOverDeliveryRejected() throws Exception {
        AwardFixture f = setupEvaluatedRfqAndAwardProposal(new BigDecimal("1000.0000"), new BigDecimal("50.00"), new BigDecimal("1000.00"));
        DecisionCreate input = new DecisionCreate("APPROVE", "Awarded", f.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + f.proposalId() + "/decision").header("Authorization", "Bearer dev-owner").header("Idempotency-Key", "idemp-od-001").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input))).andExpect(status().isOk());

        UUID poId = db.queryForObject("SELECT id FROM purchase_orders WHERE rfq_id = ?", UUID.class, f.rfqId());

        // Attempt to deliver 1,200 kg (+20%) without overDeliveryAuthorized flag
        DeliveryIntakeInput dIn = new DeliveryIntakeInput(OffsetDateTime.now(), new BigDecimal("1200.0000"), "DC-OVER", false);

        mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/receipts/delivery")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(dIn)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Edge-Case Test: Quality Quantity Sum Mismatch Rejected
    // =========================================================================
    @Test
    @DisplayName("12. Inspection accepted + rejected sum mismatching inspected quantity is rejected")
    void testQualityQuantitySumMismatchRejected() throws Exception {
        AwardFixture f = setupEvaluatedRfqAndAwardProposal(new BigDecimal("1000.0000"), new BigDecimal("50.00"), new BigDecimal("1000.00"));
        DecisionCreate input = new DecisionCreate("APPROVE", "Awarded", f.snapshotHash());
        mockMvc.perform(post("/api/v1/proposals/" + f.proposalId() + "/decision").header("Authorization", "Bearer dev-owner").header("Idempotency-Key", "idemp-qfail-001").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input))).andExpect(status().isOk());

        UUID poId = db.queryForObject("SELECT id FROM purchase_orders WHERE rfq_id = ?", UUID.class, f.rfqId());

        DeliveryIntakeInput dIn = new DeliveryIntakeInput(OffsetDateTime.now(), new BigDecimal("1000.0000"), "DC-1000", false);
        MvcResult resD = mockMvc.perform(post("/api/v1/purchase-orders/" + poId + "/receipts/delivery").header("Authorization", "Bearer dev-procurement").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(dIn))).andExpect(status().isCreated()).andReturn();
        UUID receiptId = mapper.readValue(resD.getResponse().getContentAsString(), PurchaseReceiptView.class).id();

        // 900 + 50 != 1000
        QualityInspectionInput qBad = new QualityInspectionInput("INSPECTED", new BigDecimal("1000.0000"), new BigDecimal("900.0000"), new BigDecimal("50.0000"), "50 defect", null);
        mockMvc.perform(put("/api/v1/purchase-receipts/" + receiptId + "/quality-inspection")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(qBad)))
                .andExpect(status().isBadRequest());
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 calculation failed", e);
        }
    }
}
