package org.munim.integration;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.munim.intelligence.SupplierIntelligenceDtos.DeliveryIntakeInput;
import org.munim.intelligence.SupplierIntelligenceDtos.InvoiceReconciliationInput;
import org.munim.intelligence.SupplierIntelligenceDtos.PurchaseOrderView;
import org.munim.intelligence.SupplierIntelligenceDtos.PurchaseReceiptView;
import org.munim.intelligence.SupplierPerformanceDtos.SupplierScorecardList;
import org.munim.intelligence.SupplierPerformanceDtos.SupplierScorecardView;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("postgres-test")
@DisplayName("SupplierPerformanceIntegrationTest — REST Endpoints")
class SupplierPerformanceIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private ObjectMapper mapper;

    private MockMvc mockMvc;

    private UUID materialId;
    private UUID supplierId;
    private String supplierName;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        materialId = db.queryForObject(
                "INSERT INTO materials(name, grade, specification, base_unit) " +
                        "VALUES ('Synthetic MS Plate', 'IS2062', 'IS2062-E250BR', 'kg') RETURNING id",
                UUID.class
        );

        supplierName = "Synthetic Test Vendor " + UUID.randomUUID().toString().substring(0, 8);
        supplierId = db.queryForObject(
                "INSERT INTO suppliers(name, location, is_synthetic, active) " +
                        "VALUES (?, 'Peenya Industrial Area', true, true) RETURNING id",
                UUID.class,
                supplierName
        );
    }

    @Test
    @DisplayName("1. GET /suppliers/{id}/scorecard returns UNRATED with null metrics for brand new supplier")
    void testNewSupplierReturnsUnratedScorecard() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/suppliers/" + supplierId + "/scorecard")
                        .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierId").value(supplierId.toString()))
                .andExpect(jsonPath("$.supplierName").value(supplierName))
                .andExpect(jsonPath("$.confidenceTier").value("UNRATED"))
                .andExpect(jsonPath("$.eligiblePoCount").value(0))
                .andExpect(jsonPath("$.consignmentCount").value(0))
                .andExpect(jsonPath("$.contractOtdRate").doesNotExist())
                .andExpect(jsonPath("$.gateAcceptanceRate").doesNotExist())
                .andExpect(jsonPath("$.costVariancePercentage").doesNotExist())
                .andReturn();

        SupplierScorecardView sc = mapper.readValue(res.getResponse().getContentAsString(), SupplierScorecardView.class);
        assertNull(sc.contractOtdRate());
        assertNull(sc.gateAcceptanceRate());
        assertNull(sc.costVariancePercentage());
        assertEquals("UNRATED", sc.confidenceTier());
    }

    @Test
    @DisplayName("2. GET /suppliers/{id}/scorecard returns 404 for unknown supplier ID")
    void testUnknownSupplierReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers/" + UUID.randomUUID() + "/scorecard")
                        .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("3. GET /suppliers/scorecards returns leaderboard containing active suppliers")
    void testListSupplierScorecardsLeaderboard() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/suppliers/scorecards")
                        .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andReturn();

        SupplierScorecardList list = mapper.readValue(res.getResponse().getContentAsString(), SupplierScorecardList.class);
        assertNotNull(list.items());
        assertTrue(list.items().stream().anyMatch(s -> s.supplierId().equals(supplierId)));
    }

    @Test
    @DisplayName("4. Verified delivery, inspection, and invoice outcome updates empirical scorecard metrics")
    void testVerifiedOutcomesReflectedInScorecard() throws Exception {
        // Direct insert of a completed on-time PO
        UUID poId = UUID.randomUUID();
        String poNumber = "PO-TEST-" + UUID.randomUUID().toString().substring(0, 6);
        OffsetDateTime promised = OffsetDateTime.now().plusDays(5);
        OffsetDateTime delivered = OffsetDateTime.now().plusDays(3);

        UUID rfqId = db.queryForObject(
                "INSERT INTO rfqs(material_id, specification, quantity, unit, required_procurement_delivery_at, reference_at, timezone, status) " +
                        "VALUES (?, 'IS2062-E250BR', 1000.0000, 'kg', now() + interval '10 days', now(), 'Asia/Kolkata', 'EVALUATED') RETURNING id",
                UUID.class, materialId
        );

        UUID quoteId = db.queryForObject(
                "INSERT INTO supplier_quotes(rfq_id, supplier_id) VALUES (?, ?) RETURNING id",
                UUID.class, rfqId, supplierId
        );

        UUID revId = db.queryForObject(
                "INSERT INTO supplier_quote_revisions(supplier_quote_id, rfq_id, revision_no, state, received_at, raw_message, raw_message_sha256, fields, completeness_status) " +
                        "VALUES (?, ?, 1, 'VERIFIED', now(), 'Raw Quote', 'sha256raw', '{}'::jsonb, 'COMPLETE') RETURNING id",
                UUID.class, quoteId, rfqId
        );

        db.update("UPDATE supplier_quotes SET current_revision_id = ? WHERE id = ?", revId, quoteId);
        db.update("UPDATE rfqs SET awarded_quote_id = ? WHERE id = ?", quoteId, rfqId);

        db.update(
                "INSERT INTO purchase_orders(" +
                        "id, po_number, rfq_id, supplier_id, quote_revision_id, material_id, ordered_quantity, unit, " +
                        "agreed_unit_price, agreed_freight_amount, freight_basis, material_tax_rate, material_itc_status, " +
                        "freight_tax_rate, freight_itc_status, quoted_economic_landed_cost, quoted_cash_payable, " +
                        "promised_delivery_date, status, first_completed_at, po_delivery_status, po_snapshot_hash" +
                        ") VALUES (?, ?, ?, ?, ?, ?, 1000.0000, 'kg', 50.00, 1000.00, 'SEPARATE', 18.00, 'CREDITABLE', 18.00, 'CREDITABLE', 51000.00, 60180.00, ?, 'COMPLETED', ?, 'PO_ON_TIME', 'hash123')",
                poId, poNumber, rfqId, supplierId, revId, materialId, promised, delivered
        );

        // Insert verified receipt
        UUID receiptId = UUID.randomUUID();
        db.update(
                "INSERT INTO purchase_receipts(" +
                        "id, purchase_order_id, supplier_id, material_id, receipt_sequence, record_type, " +
                        "stage_delivery, actual_delivery_date, received_quantity, delivery_challan_no, delivery_status, " +
                        "stage_quality, inspected_quantity, accepted_quantity, rejected_quantity, rejection_reason, quality_acceptance_rate, " +
                        "stage_invoice, invoice_number, invoice_date, actual_material_base, actual_material_gst, " +
                        "actual_freight_base, actual_freight_gst, actual_economic_landed_cost, actual_cash_paid, " +
                        "cost_variance_amount, cost_variance_percentage, verification_status, verified_by, verified_at, outcome_snapshot_hash" +
                        ") VALUES (?, ?, ?, ?, 1, 'ORIGINAL', 'RECORDED', ?, 1000.0000, 'DC-PERF-01', 'ON_TIME', " +
                        "'INSPECTED', 1000.0000, 990.0000, 10.0000, 'Surface oxidation', 99.00, " +
                        "'RECONCILED', 'INV-PERF-01', ?, 50000.00, 9000.00, 1000.00, 180.00, 51000.00, 60180.00, " +
                        "0.00, 0.00, 'VERIFIED', 'dev-owner', now(), 'hash-rc-01')",
                receiptId, poId, supplierId, materialId, delivered, delivered
        );

        // Query scorecard
        MvcResult res = mockMvc.perform(get("/api/v1/suppliers/" + supplierId + "/scorecard")
                        .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidenceTier").value("LOW"))
                .andExpect(jsonPath("$.eligiblePoCount").value(1))
                .andExpect(jsonPath("$.onTimePoCount").value(1))
                .andExpect(jsonPath("$.contractOtdRate").value(100.00))
                .andExpect(jsonPath("$.consignmentCount").value(1))
                .andExpect(jsonPath("$.consignmentOnTimeRate").value(100.00))
                .andExpect(jsonPath("$.gateAcceptanceRate").value(99.00))
                .andExpect(jsonPath("$.netUsableYieldRate").value(99.00))
                .andExpect(jsonPath("$.costVariancePercentage").value(0.00))
                .andReturn();

        SupplierScorecardView sc = mapper.readValue(res.getResponse().getContentAsString(), SupplierScorecardView.class);
        assertEquals(0, new BigDecimal("100.00").compareTo(sc.contractOtdRate()));
        assertEquals(0, new BigDecimal("99.00").compareTo(sc.gateAcceptanceRate()));
        assertEquals(0, new BigDecimal("0.00").compareTo(sc.costVariancePercentage()));
    }
}
