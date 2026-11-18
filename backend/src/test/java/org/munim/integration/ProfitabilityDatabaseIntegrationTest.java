package org.munim.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres-test")
public class ProfitabilityDatabaseIntegrationTest {

    @Autowired
    private JdbcTemplate db;

    private UUID materialId;

    @BeforeEach
    void setUp() {
        materialId = db.queryForObject("SELECT id FROM materials LIMIT 1", UUID.class);
        assertNotNull(materialId, "Materials table must have seeded material");
    }

    private UUID createCustomerEnquiry(String reference) {
        return db.queryForObject(
                "INSERT INTO customer_enquiries(customer_name, customer_reference, contact_email, contact_phone, required_delivery_date, status) " +
                        "VALUES ('Acme Fabrications Ltd', ?, 'orders@acmefab.com', '+91-9876543210', now() + interval '30 days', 'OPEN') " +
                        "RETURNING id",
                UUID.class,
                reference
        );
    }

    private UUID createBom(UUID enquiryId, BigDecimal batchQty) {
        return db.queryForObject(
                "INSERT INTO boms(enquiry_id, bom_version, batch_quantity, status) " +
                        "VALUES (?, 1, ?, 'DRAFT') RETURNING id",
                UUID.class,
                enquiryId, batchQty
        );
    }

    // 1. Full end-to-end insertion of customer enquiry, BOM, BOM items, costing sheet, and customer quotation
    @Test
    @DisplayName("1. DB Integration: Successfully insert and verify full profitability entity graph")
    void testInsertFullProfitabilityGraph() {
        String enqNumber = "ENQ-" + UUID.randomUUID().toString().substring(0, 8);
        UUID enquiryId = createCustomerEnquiry(enqNumber);
        assertNotNull(enquiryId);

        BigDecimal batchQty = new BigDecimal("100.0000");
        UUID bomId = createBom(enquiryId, batchQty);
        assertNotNull(bomId);

        // Insert BOM Item with ESTIMATE sourcing
        UUID bomItemId = db.queryForObject(
                "INSERT INTO bom_items(bom_id, material_id, part_name, net_quantity, unit, scrap_percentage, " +
                        "scrap_recovery_supported, scrap_recovery_price, sourcing_basis, estimated_unit_cost, estimate_rationale) " +
                        "VALUES (?, ?, 'Chassis Base', 2.5000, 'kg', 10.00, true, 30.00, 'ESTIMATE', 85.00, 'Phone estimate Jindal Nov 2026') " +
                        "RETURNING id",
                UUID.class,
                bomId, materialId
        );
        assertNotNull(bomItemId);

        // Insert Costing Sheet
        UUID costingSheetId = db.queryForObject(
                "INSERT INTO costing_sheets(enquiry_id, bom_id, batch_quantity, material_cost, conversion_cost, overhead_cost, " +
                        "packaging_cost, freight_cost, total_cost, target_margin, target_price, quoted_price, realized_margin, " +
                        "margin_classification, calculation_version, calculation_snapshot, snapshot_sha256) " +
                        "VALUES (?, ?, ?, 233.75, 107.00, 16.05, 5.00, 10.00, 371.80, 20.00, 464.75, 480.00, 22.54, " +
                        "'HEALTHY_MARGIN', '1.0', '{\"version\":\"1.0\"}'::jsonb, 'a1b2c3d4e5f67890123456789012345678901234567890123456789012345678') " +
                        "RETURNING id",
                UUID.class,
                enquiryId, bomId, batchQty
        );
        assertNotNull(costingSheetId);

        // Insert Customer Quotation
        UUID quotationId = db.queryForObject(
                "INSERT INTO customer_quotations(enquiry_id, costing_sheet_id, selling_price, output_gst_rate, customer_payable, " +
                        "valid_from, valid_until, payment_terms, margin_classification, approval_status) " +
                        "VALUES (?, ?, 480.00, 18.00, 566.40, now(), now() + interval '15 days', '30 Days Net', 'HEALTHY_MARGIN', 'DRAFT') " +
                        "RETURNING id",
                UUID.class,
                enquiryId, costingSheetId
        );
        assertNotNull(quotationId);

        // Verify retrieval
        var quoteRow = db.queryForMap("SELECT * FROM customer_quotations WHERE id = ?", quotationId);
        assertEquals(new BigDecimal("480.00"), quoteRow.get("selling_price"));
        assertEquals(new BigDecimal("566.40"), quoteRow.get("customer_payable"));
        assertEquals("HEALTHY_MARGIN", quoteRow.get("margin_classification"));
        assertEquals("DRAFT", quoteRow.get("approval_status"));
    }

    // 2. bom_items_sourcing_check: ESTIMATE masquerading with awarded revision id
    @Test
    @DisplayName("2. DB Constraint: bom_items_sourcing_check rejects ESTIMATE with awarded revision ID")
    void testBomItemSourcingConstraint_EstimateMasqueradingAsAwarded() {
        UUID enquiryId = createCustomerEnquiry("ENQ-" + UUID.randomUUID().toString().substring(0, 8));
        UUID bomId = createBom(enquiryId, new BigDecimal("50.0000"));
        UUID fakeRevisionId = UUID.randomUUID();

        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "INSERT INTO bom_items(bom_id, material_id, part_name, net_quantity, unit, scrap_percentage, " +
                        "scrap_recovery_supported, sourcing_basis, awarded_quote_revision_id, estimated_unit_cost, estimate_rationale) " +
                        "VALUES (?, ?, 'Cover', 1.0000, 'kg', 0.00, false, 'ESTIMATE', ?, 100.00, 'Estimate')",
                bomId, materialId, fakeRevisionId
        ));
    }

    // 3. bom_items_sourcing_check: ESTIMATE without mandatory rationale
    @Test
    @DisplayName("3. DB Constraint: bom_items_sourcing_check rejects ESTIMATE with missing or blank rationale")
    void testBomItemSourcingConstraint_EstimateWithoutRationale() {
        UUID enquiryId = createCustomerEnquiry("ENQ-" + UUID.randomUUID().toString().substring(0, 8));
        UUID bomId = createBom(enquiryId, new BigDecimal("50.0000"));

        // Null rationale
        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "INSERT INTO bom_items(bom_id, material_id, part_name, net_quantity, unit, scrap_percentage, " +
                        "scrap_recovery_supported, sourcing_basis, estimated_unit_cost, estimate_rationale) " +
                        "VALUES (?, ?, 'Cover', 1.0000, 'kg', 0.00, false, 'ESTIMATE', 100.00, NULL)",
                bomId, materialId
        ));

        // Blank rationale
        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "INSERT INTO bom_items(bom_id, material_id, part_name, net_quantity, unit, scrap_percentage, " +
                        "scrap_recovery_supported, sourcing_basis, estimated_unit_cost, estimate_rationale) " +
                        "VALUES (?, ?, 'Cover', 1.0000, 'kg', 0.00, false, 'ESTIMATE', 100.00, '   ')",
                bomId, materialId
        ));
    }

    // 4. bom_items_sourcing_check: AWARDED_QUOTE without awarded quote revision ID
    @Test
    @DisplayName("4. DB Constraint: bom_items_sourcing_check rejects AWARDED_QUOTE without revision ID")
    void testBomItemSourcingConstraint_AwardedWithoutRevision() {
        UUID enquiryId = createCustomerEnquiry("ENQ-" + UUID.randomUUID().toString().substring(0, 8));
        UUID bomId = createBom(enquiryId, new BigDecimal("50.0000"));

        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "INSERT INTO bom_items(bom_id, material_id, part_name, net_quantity, unit, scrap_percentage, " +
                        "scrap_recovery_supported, sourcing_basis, awarded_quote_revision_id) " +
                        "VALUES (?, ?, 'Cover', 1.0000, 'kg', 0.00, false, 'AWARDED_QUOTE', NULL)",
                bomId, materialId
        ));
    }

    // 5. bom_items_sourcing_check: AWARDED_QUOTE with estimate cost populated
    @Test
    @DisplayName("5. DB Constraint: bom_items_sourcing_check rejects AWARDED_QUOTE with estimate cost")
    void testBomItemSourcingConstraint_AwardedWithEstimateCost() {
        UUID enquiryId = createCustomerEnquiry("ENQ-" + UUID.randomUUID().toString().substring(0, 8));
        UUID bomId = createBom(enquiryId, new BigDecimal("50.0000"));
        UUID fakeRevisionId = UUID.randomUUID();

        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "INSERT INTO bom_items(bom_id, material_id, part_name, net_quantity, unit, scrap_percentage, " +
                        "scrap_recovery_supported, sourcing_basis, awarded_quote_revision_id, estimated_unit_cost) " +
                        "VALUES (?, ?, 'Cover', 1.0000, 'kg', 0.00, false, 'AWARDED_QUOTE', ?, 99.00)",
                bomId, materialId, fakeRevisionId
        ));
    }

    // 6. bom_items_scrap_recovery_check: supported = true but price is null
    @Test
    @DisplayName("6. DB Constraint: bom_items_scrap_recovery_check rejects recovery supported without price")
    void testBomItemScrapRecoveryConstraint_SupportedWithoutPrice() {
        UUID enquiryId = createCustomerEnquiry("ENQ-" + UUID.randomUUID().toString().substring(0, 8));
        UUID bomId = createBom(enquiryId, new BigDecimal("50.0000"));

        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "INSERT INTO bom_items(bom_id, material_id, part_name, net_quantity, unit, scrap_percentage, " +
                        "scrap_recovery_supported, scrap_recovery_price, sourcing_basis, estimated_unit_cost, estimate_rationale) " +
                        "VALUES (?, ?, 'Cover', 1.0000, 'kg', 5.00, true, NULL, 'ESTIMATE', 100.00, 'Phone quote')",
                bomId, materialId
        ));
    }

    // 7. bom_items_scrap_recovery_check: supported = false but price is provided
    @Test
    @DisplayName("7. DB Constraint: bom_items_scrap_recovery_check rejects recovery price when supported is false")
    void testBomItemScrapRecoveryConstraint_NotSupportedWithPrice() {
        UUID enquiryId = createCustomerEnquiry("ENQ-" + UUID.randomUUID().toString().substring(0, 8));
        UUID bomId = createBom(enquiryId, new BigDecimal("50.0000"));

        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "INSERT INTO bom_items(bom_id, material_id, part_name, net_quantity, unit, scrap_percentage, " +
                        "scrap_recovery_supported, scrap_recovery_price, sourcing_basis, estimated_unit_cost, estimate_rationale) " +
                        "VALUES (?, ?, 'Cover', 1.0000, 'kg', 5.00, false, 25.00, 'ESTIMATE', 100.00, 'Phone quote')",
                bomId, materialId
        ));
    }

    // 8. customer_quotations_validity_check: valid_until <= valid_from rejected
    @Test
    @DisplayName("8. DB Constraint: customer_quotations_validity_check rejects valid_until <= valid_from")
    void testCustomerQuotationValidityConstraint() {
        UUID enquiryId = createCustomerEnquiry("ENQ-" + UUID.randomUUID().toString().substring(0, 8));
        UUID bomId = createBom(enquiryId, new BigDecimal("10.0000"));

        UUID costingSheetId = db.queryForObject(
                "INSERT INTO costing_sheets(enquiry_id, bom_id, batch_quantity, material_cost, conversion_cost, overhead_cost, " +
                        "packaging_cost, freight_cost, total_cost, target_margin, target_price, quoted_price, realized_margin, " +
                        "margin_classification, calculation_version, calculation_snapshot, snapshot_sha256) " +
                        "VALUES (?, ?, 10.0000, 100.00, 50.00, 10.00, 5.00, 5.00, 170.00, 20.00, 212.50, 220.00, 22.73, " +
                        "'HEALTHY_MARGIN', '1.0', '{}'::jsonb, '1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef') " +
                        "RETURNING id",
                UUID.class,
                enquiryId, bomId
        );

        // valid_until earlier than valid_from
        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "INSERT INTO customer_quotations(enquiry_id, costing_sheet_id, selling_price, output_gst_rate, customer_payable, " +
                        "valid_from, valid_until, payment_terms, margin_classification, approval_status) " +
                        "VALUES (?, ?, 220.00, 18.00, 259.60, now(), now() - interval '1 day', 'Immediate', 'HEALTHY_MARGIN', 'DRAFT')",
                enquiryId, costingSheetId
        ));

        // valid_until equal to valid_from
        OffsetDateTime now = OffsetDateTime.now();
        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "INSERT INTO customer_quotations(enquiry_id, costing_sheet_id, selling_price, output_gst_rate, customer_payable, " +
                        "valid_from, valid_until, payment_terms, margin_classification, approval_status) " +
                        "VALUES (?, ?, 220.00, 18.00, 259.60, ?, ?, 'Immediate', 'HEALTHY_MARGIN', 'DRAFT')",
                enquiryId, costingSheetId, now, now
        ));
    }

    // 9. Batch quantity constraint: zero or negative batch quantity rejected
    @Test
    @DisplayName("9. DB Constraint: boms and costing_sheets reject zero and negative batch_quantity")
    void testBatchQuantityConstraint_ZeroOrNegative() {
        UUID enquiryId = createCustomerEnquiry("ENQ-" + UUID.randomUUID().toString().substring(0, 8));

        // BOM zero batch quantity
        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "INSERT INTO boms(enquiry_id, bom_version, batch_quantity, status) VALUES (?, 1, 0, 'DRAFT')",
                enquiryId
        ));

        // BOM negative batch quantity
        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "INSERT INTO boms(enquiry_id, bom_version, batch_quantity, status) VALUES (?, 1, -10.0, 'DRAFT')",
                enquiryId
        ));

        UUID validBomId = createBom(enquiryId, new BigDecimal("10.0000"));

        // Costing sheet zero batch quantity
        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "INSERT INTO costing_sheets(enquiry_id, bom_id, batch_quantity, material_cost, conversion_cost, overhead_cost, " +
                        "packaging_cost, freight_cost, total_cost, calculation_snapshot, snapshot_sha256) " +
                        "VALUES (?, ?, 0, 100.00, 50.00, 10.00, 5.00, 5.00, 170.00, '{}'::jsonb, 'abcdef')",
                enquiryId, validBomId
        ));
    }

    // 10. Customer quotation selling price constraint: zero or negative selling price rejected
    @Test
    @DisplayName("10. DB Constraint: customer_quotations rejects zero and negative selling_price and customer_payable")
    void testCustomerQuotationSellingPrice_ZeroOrNegative() {
        UUID enquiryId = createCustomerEnquiry("ENQ-" + UUID.randomUUID().toString().substring(0, 8));
        UUID bomId = createBom(enquiryId, new BigDecimal("10.0000"));

        UUID costingSheetId = db.queryForObject(
                "INSERT INTO costing_sheets(enquiry_id, bom_id, batch_quantity, material_cost, conversion_cost, overhead_cost, " +
                        "packaging_cost, freight_cost, total_cost, calculation_snapshot, snapshot_sha256) " +
                        "VALUES (?, ?, 10.0000, 100.00, 50.00, 10.00, 5.00, 5.00, 170.00, '{}'::jsonb, 'abcdef') " +
                        "RETURNING id",
                UUID.class,
                enquiryId, bomId
        );

        // Zero selling price
        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "INSERT INTO customer_quotations(enquiry_id, costing_sheet_id, selling_price, output_gst_rate, customer_payable, " +
                        "valid_from, valid_until, payment_terms, margin_classification, approval_status) " +
                        "VALUES (?, ?, 0.00, 18.00, 0.00, now(), now() + interval '10 days', 'Net 30', 'HEALTHY_MARGIN', 'DRAFT')",
                enquiryId, costingSheetId
        ));

        // Negative selling price
        assertThrows(DataIntegrityViolationException.class, () -> db.update(
                "INSERT INTO customer_quotations(enquiry_id, costing_sheet_id, selling_price, output_gst_rate, customer_payable, " +
                        "valid_from, valid_until, payment_terms, margin_classification, approval_status) " +
                        "VALUES (?, ?, -100.00, 18.00, -118.00, now(), now() + interval '10 days', 'Net 30', 'HEALTHY_MARGIN', 'DRAFT')",
                enquiryId, costingSheetId
        ));
    }
}
