package org.munim.ml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.munim.ml.SupplierMlDtos.SupplierDeliveryFeatureVector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres-test")
class SupplierMlFeatureRepositoryTest {

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private SupplierMlFeatureRepository featureRepository;

    private UUID supplierId;
    private UUID materialId;

    @BeforeEach
    void setUp() {
        supplierId = db.queryForObject(
                "INSERT INTO suppliers(name, location, is_synthetic) VALUES ('PointInTime Test Supplier', 'Pune', true) RETURNING id",
                UUID.class
        );
        materialId = db.queryForObject("SELECT id FROM materials LIMIT 1", UUID.class);
    }

    private UUID createRfq(OffsetDateTime deadline) {
        return db.queryForObject(
                """
                INSERT INTO rfqs (material_id, specification, quantity, unit, required_procurement_delivery_at, reference_at, timezone, allow_split_sourcing, status)
                VALUES (?, 'Standard Test Spec', 1000.0000, 'kg', ?, now(), 'Asia/Kolkata', false, 'AWARDED')
                RETURNING id
                """,
                UUID.class, materialId, Timestamp.from(deadline.toInstant())
        );
    }

    private UUID createSupplierQuoteAndRevision(UUID rfqId) {
        UUID quoteId = db.queryForObject(
                "INSERT INTO supplier_quotes (rfq_id, supplier_id) VALUES (?, ?) RETURNING id",
                UUID.class, rfqId, supplierId
        );
        UUID revisionId = db.queryForObject(
                """
                INSERT INTO supplier_quote_revisions (supplier_quote_id, rfq_id, revision_no, state, received_at, raw_message, raw_message_sha256, fields, completeness_status)
                VALUES (?, ?, 1, 'VERIFIED', now(), 'Quote', 'sha', '{"unitPrice":"95.00"}'::jsonb, 'COMPLETE')
                RETURNING id
                """,
                UUID.class, quoteId, rfqId
        );
        db.update("UPDATE supplier_quotes SET current_revision_id = ?, awarded_revision_id = ? WHERE id = ?", revisionId, revisionId, quoteId);
        return revisionId;
    }

    private UUID insertPoWithLifecycle(
            UUID rfqId, UUID revId, String poNumber, String status,
            OffsetDateTime createdAt, OffsetDateTime promisedDate,
            OffsetDateTime completedAt, OffsetDateTime closedAt,
            String deliveryStatus, BigDecimal unfulfilledQty
    ) {
        String uniquePoNum = poNumber + "-" + UUID.randomUUID();
        UUID poId = db.queryForObject(
                """
                INSERT INTO purchase_orders (
                    po_number, rfq_id, supplier_id, quote_revision_id, material_id,
                    ordered_quantity, unit, agreed_unit_price, agreed_freight_amount, freight_basis,
                    material_tax_rate, material_itc_status, freight_tax_rate, freight_itc_status,
                    quoted_economic_landed_cost, quoted_cash_payable, promised_delivery_date,
                    status, unfulfilled_quantity, first_completed_at, closed_at, po_delivery_status, po_snapshot_hash, created_at
                ) VALUES (
                    ?, ?, ?, ?, ?,
                    1000.0000, 'kg', 95.00, 1500.00, 'SEPARATE',
                    18.00, 'CREDITABLE', 18.00, 'CREDITABLE',
                    96500.00, 113600.00, ?,
                    ?, ?, ?, ?, ?, 'snapshotfake', ?
                ) RETURNING id
                """,
                UUID.class,
                uniquePoNum, rfqId, supplierId, revId, materialId,
                Timestamp.from(promisedDate.toInstant()),
                status,
                unfulfilledQty != null ? unfulfilledQty : BigDecimal.ZERO,
                completedAt != null ? Timestamp.from(completedAt.toInstant()) : null,
                closedAt != null ? Timestamp.from(closedAt.toInstant()) : null,
                deliveryStatus,
                Timestamp.from(createdAt.toInstant())
        );

        // Always record PURCHASE_ORDER_ISSUED audit event at createdAt
        db.update(
                """
                INSERT INTO audit_events (actor_id, actor_role, action, entity_type, entity_id, entity_version, reason, policy_version, metadata, occurred_at)
                VALUES ('test-system', 'OWNER', 'PURCHASE_ORDER_ISSUED', 'PURCHASE_ORDER', ?, 0, 'PO Issued', 1, '{}'::jsonb, ?)
                """,
                poId, Timestamp.from(createdAt.toInstant())
        );

        if (closedAt != null) {
            db.update(
                    """
                    INSERT INTO audit_events (actor_id, actor_role, action, entity_type, entity_id, entity_version, reason, policy_version, metadata, occurred_at)
                    VALUES ('test-owner', 'OWNER', 'PURCHASE_ORDER_CLOSED', 'PURCHASE_ORDER', ?, 1, 'PO Closed', 1, '{}'::jsonb, ?)
                    """,
                    poId, Timestamp.from(closedAt.toInstant())
            );
        }

        return poId;
    }

    private UUID insertPoWithoutAuditEvents(
            UUID rfqId, UUID revId, String poNumber, String status,
            OffsetDateTime createdAt, OffsetDateTime promisedDate,
            OffsetDateTime completedAt, OffsetDateTime closedAt,
            String deliveryStatus, BigDecimal unfulfilledQty
    ) {
        String uniquePoNum = poNumber + "-" + UUID.randomUUID();
        return db.queryForObject(
                """
                INSERT INTO purchase_orders (
                    po_number, rfq_id, supplier_id, quote_revision_id, material_id,
                    ordered_quantity, unit, agreed_unit_price, agreed_freight_amount, freight_basis,
                    material_tax_rate, material_itc_status, freight_tax_rate, freight_itc_status,
                    quoted_economic_landed_cost, quoted_cash_payable, promised_delivery_date,
                    status, unfulfilled_quantity, first_completed_at, closed_at, po_delivery_status, po_snapshot_hash, created_at
                ) VALUES (
                    ?, ?, ?, ?, ?,
                    1000.0000, 'kg', 95.00, 1500.00, 'SEPARATE',
                    18.00, 'CREDITABLE', 18.00, 'CREDITABLE',
                    96500.00, 113600.00, ?,
                    ?, ?, ?, ?, ?, 'snapshotfake', ?
                ) RETURNING id
                """,
                UUID.class,
                uniquePoNum, rfqId, supplierId, revId, materialId,
                Timestamp.from(promisedDate.toInstant()),
                status,
                unfulfilledQty != null ? unfulfilledQty : BigDecimal.ZERO,
                completedAt != null ? Timestamp.from(completedAt.toInstant()) : null,
                closedAt != null ? Timestamp.from(closedAt.toInstant()) : null,
                deliveryStatus,
                Timestamp.from(createdAt.toInstant())
        );
    }

    private void insertPoAuditEvent(UUID poId, String action, OffsetDateTime occurredAt) {
        Integer maxVersion = db.queryForObject(
                "SELECT COALESCE(MAX(entity_version), 0) + 1 FROM audit_events WHERE entity_id = ?",
                Integer.class, poId
        );
        insertPoAuditEvent(poId, action, occurredAt, maxVersion != null ? maxVersion : 1);
    }

    private void insertPoAuditEvent(UUID poId, String action, OffsetDateTime occurredAt, int version) {
        db.update(
                """
                INSERT INTO audit_events (actor_id, actor_role, action, entity_type, entity_id, entity_version, reason, policy_version, metadata, occurred_at)
                VALUES ('test-owner', 'OWNER', ?, 'PURCHASE_ORDER', ?, ?, 'Lifecycle event', 1, '{}'::jsonb, ?)
                """,
                action, poId, version, Timestamp.from(occurredAt.toInstant())
        );
    }

    private UUID insertVerifiedOriginalReceipt(
            UUID poId, int sequence, OffsetDateTime createdAt,
            BigDecimal receivedQty, BigDecimal inspectedQty, BigDecimal acceptedQty, BigDecimal rejectedQty
    ) {
        return insertCustomOriginalReceipt(
                poId, sequence, createdAt, createdAt, createdAt, "VERIFIED",
                receivedQty, inspectedQty, acceptedQty, rejectedQty
        );
    }

    private UUID insertCustomOriginalReceipt(
            UUID poId, int sequence, OffsetDateTime createdAt,
            OffsetDateTime inspectedAt, OffsetDateTime verifiedAt, String verificationStatus,
            BigDecimal receivedQty, BigDecimal inspectedQty, BigDecimal acceptedQty, BigDecimal rejectedQty
    ) {
        BigDecimal rate = inspectedQty != null && inspectedQty.compareTo(BigDecimal.ZERO) > 0 ?
                acceptedQty.multiply(new BigDecimal("100.00")).divide(inspectedQty, 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
        String reason = rejectedQty != null && rejectedQty.compareTo(BigDecimal.ZERO) > 0 ? "Dimensional tolerance failure" : null;

        UUID receiptId = db.queryForObject(
                """
                INSERT INTO purchase_receipts (
                    purchase_order_id, supplier_id, material_id, receipt_sequence, record_type,
                    stage_delivery, actual_delivery_date, received_quantity, delivery_status, delay_days,
                    stage_quality, inspected_quantity, accepted_quantity, rejected_quantity, quality_acceptance_rate,
                    rejection_reason, inspected_at, stage_invoice, verification_status, verified_at, verified_by, created_at
                ) VALUES (
                    ?, ?, ?, ?, 'ORIGINAL',
                    'RECORDED', ?, ?, 'ON_TIME', 0,
                    'INSPECTED', ?, ?, ?, ?,
                    ?, ?, 'PENDING', ?, ?, 'test-owner', ?
                ) RETURNING id
                """,
                UUID.class,
                poId, supplierId, materialId, sequence,
                Timestamp.from(createdAt.toInstant()),
                receivedQty, inspectedQty, acceptedQty, rejectedQty, rate,
                reason, inspectedAt != null ? Timestamp.from(inspectedAt.toInstant()) : null,
                verificationStatus, verifiedAt != null ? Timestamp.from(verifiedAt.toInstant()) : null,
                Timestamp.from(createdAt.toInstant())
        );

        if (verifiedAt != null && "VERIFIED".equals(verificationStatus)) {
            db.update(
                    """
                    INSERT INTO audit_events (actor_id, actor_role, action, entity_type, entity_id, entity_version, reason, policy_version, metadata, occurred_at)
                    VALUES ('test-owner', 'OWNER', 'RECEIPT_VERIFIED', 'PURCHASE_RECEIPT', ?, 1, 'Receipt Verified', 1, '{}'::jsonb, ?)
                    """,
                    receiptId, Timestamp.from(verifiedAt.toInstant())
            );
        }

        return receiptId;
    }

    private UUID insertVerifiedAdjustment(
            UUID poId, UUID originalReceiptId, int sequence, OffsetDateTime createdAt,
            BigDecimal deltaAcceptedQty
    ) {
        UUID adjId = db.queryForObject(
                """
                INSERT INTO purchase_receipts (
                    purchase_order_id, supplier_id, material_id, receipt_sequence, record_type,
                    superseded_receipt_id, adjustment_type, correction_reason,
                    stage_delivery, stage_quality, stage_invoice, verification_status, verified_at, verified_by,
                    delta_received_quantity, delta_inspected_quantity, delta_accepted_quantity, delta_rejected_quantity,
                    created_at
                ) VALUES (
                    ?, ?, ?, ?, 'CORRECTION_ADJUSTMENT',
                    ?, 'PHYSICAL_RETURN', 'Post-inspection latent defect return',
                    'RECORDED', 'INSPECTED', 'PENDING', 'VERIFIED', ?, 'test-owner',
                    0.0000, 0.0000, ?, 0.0000,
                    ?
                ) RETURNING id
                """,
                UUID.class,
                poId, supplierId, materialId, sequence,
                originalReceiptId,
                Timestamp.from(createdAt.toInstant()),
                deltaAcceptedQty,
                Timestamp.from(createdAt.toInstant())
        );

        db.update(
                """
                INSERT INTO audit_events (actor_id, actor_role, action, entity_type, entity_id, entity_version, reason, policy_version, metadata, occurred_at)
                VALUES ('test-owner', 'OWNER', 'RECEIPT_ADJUSTED', 'PURCHASE_RECEIPT', ?, 1, 'Adjustment Recorded', 1, '{}'::jsonb, ?)
                """,
                adjId, Timestamp.from(createdAt.toInstant())
        );

        return adjId;
    }

    @Test
    @DisplayName("1. A PO completed before T is eligible")
    void testPoCompletedBeforeCutoffIsEligible() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        // PO placed 25 days ago, promised 15 days ago, completed 12 days ago (before T)
        UUID rfq1 = createRfq(now.minusDays(15));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        insertPoWithLifecycle(
                rfq1, rev1, "PO-ELIGIBLE-1", "COMPLETED",
                now.minusDays(25), now.minusDays(15), now.minusDays(16), null, "PO_ON_TIME", BigDecimal.ZERO
        );

        // Target PO placed at T (10 days ago)
        UUID rfqTarget = createRfq(now.minusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-1", "ISSUED",
                tCutoff, now.minusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        assertEquals(1, fv.histEligiblePoCount());
        assertEquals(1, fv.histOntimePoCount());
        assertEquals(new BigDecimal("100.00"), fv.histPointInTimeOtdRate());
        assertEquals(1, fv.histMaterialPoCount());
        assertTrue(fv.hasMaterialEvidence());
    }

    @Test
    @DisplayName("2. A PO completed after T is excluded (Proves Original SQL Defect Fix)")
    void testPoCompletedAfterCutoffIsExcluded() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        // Prior PO was placed 15 days ago with promised date 5 days ago (in-flight at T, not yet due at T)
        // Under original SQL, because first_completed_at IS NOT NULL and created_at < T, it was LEAKED!
        // Under corrected SQL, since first_completed_at (minusDays(2)) >= T, it is excluded!
        UUID rfq1 = createRfq(now.minusDays(5));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        insertPoWithLifecycle(
                rfq1, rev1, "PO-FUTURE-COMPL-1", "COMPLETED",
                now.minusDays(15), now.minusDays(5), now.minusDays(2), null, "PO_ON_TIME", BigDecimal.ZERO
        );

        // Target PO placed at T (10 days ago)
        UUID rfqTarget = createRfq(now.plusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-2", "ISSUED",
                tCutoff, now.plusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // Must NOT include the PO whose completion occurred after T
        assertEquals(0, fv.histEligiblePoCount(), "PO completed after T must be excluded from historical completed outcomes");
        assertEquals(0, fv.histOntimePoCount());
        assertNull(fv.histPointInTimeOtdRate());
    }

    @Test
    @DisplayName("3. A PO short-closed before T is eligible under the failure label")
    void testPoShortClosedBeforeCutoffIsEligibleAsFailure() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        // Prior PO placed 30 days ago, promised 20 days ago, short-closed 15 days ago (before T)
        UUID rfq1 = createRfq(now.minusDays(20));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        insertPoWithLifecycle(
                rfq1, rev1, "PO-SHORT-CLOSED-PAST", "COMPLETED",
                now.minusDays(30), now.minusDays(20), null, now.minusDays(15), "PO_SHORT_CLOSED", new BigDecimal("500.0000")
        );

        UUID rfqTarget = createRfq(now.minusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-3", "ISSUED",
                tCutoff, now.minusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // Eligible as a failure outcome (counted in denominator, not in on-time numerator)
        assertEquals(1, fv.histEligiblePoCount());
        assertEquals(0, fv.histOntimePoCount());
        assertEquals(new BigDecimal("0.00"), fv.histPointInTimeOtdRate());
    }

    @Test
    @DisplayName("4. A PO short-closed after T is excluded")
    void testPoShortClosedAfterCutoffIsExcluded() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        // Prior PO placed 15 days ago, promised 5 days ago (not yet due at T). Short-closed 2 days ago (after T)
        UUID rfq1 = createRfq(now.minusDays(5));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        insertPoWithLifecycle(
                rfq1, rev1, "PO-SHORT-CLOSED-FUTURE", "COMPLETED",
                now.minusDays(15), now.minusDays(5), null, now.minusDays(2), "PO_SHORT_CLOSED", new BigDecimal("500.0000")
        );

        UUID rfqTarget = createRfq(now.plusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-4", "ISSUED",
                tCutoff, now.plusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        assertEquals(0, fv.histEligiblePoCount());
        assertNull(fv.histPointInTimeOtdRate());
    }

    @Test
    @DisplayName("5. A PO that was open at T but completed later is not treated as completed at T")
    void testPoOpenAtCutoffCompletedLaterNotTreatedAsCompleted() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        // Prior PO placed 14 days ago, promised 6 days ago (in-flight at T). Completed 2 days ago.
        UUID rfq1 = createRfq(now.minusDays(6));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        insertPoWithLifecycle(
                rfq1, rev1, "PO-OPEN-AT-T-COMPLETED-LATER", "COMPLETED",
                now.minusDays(14), now.minusDays(6), now.minusDays(2), null, "PO_ON_TIME", BigDecimal.ZERO
        );

        UUID rfqTarget = createRfq(now.plusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-5", "ISSUED",
                tCutoff, now.plusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // At T (10 days ago), promised date (6 days ago) was in the future relative to T. Order was in-flight and not yet due.
        assertEquals(0, fv.histEligiblePoCount());
        assertNull(fv.histPointInTimeOtdRate());
    }

    @Test
    @DisplayName("6. A PO that was open at T and overdue at T is handled according to feature contract")
    void testPoOpenAndOverdueAtCutoffIsHandled() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        // Prior PO placed 25 days ago with promised delivery 15 days ago (5 days before T).
        // At T (10 days ago), no completed delivery had arrived -> Open and Overdue!
        // Later completed at T+2 (now.minusDays(8))
        UUID rfq1 = createRfq(now.minusDays(15));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        insertPoWithLifecycle(
                rfq1, rev1, "PO-OPEN-OVERDUE-AT-T", "COMPLETED",
                now.minusDays(25), now.minusDays(15), now.minusDays(8), null, "PO_DELAYED", BigDecimal.ZERO
        );

        UUID rfqTarget = createRfq(now.minusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-6", "ISSUED",
                tCutoff, now.minusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // At T, this order was open and overdue -> eligible failure/delay signal
        assertEquals(1, fv.histEligiblePoCount());
        assertEquals(0, fv.histOntimePoCount());
        assertEquals(new BigDecimal("0.00"), fv.histPointInTimeOtdRate());
        assertNotNull(fv.histAvgDelayDays());
        // Delay at T = (T - 10d) - (T - 15d) = 5 days
        assertEquals(new BigDecimal("5.0"), fv.histAvgDelayDays());
    }

    @Test
    @DisplayName("7. A future receipt cannot affect historical quality features")
    void testFutureReceiptCannotAffectQualityFeatures() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        // Prior PO completed before T
        UUID rfq1 = createRfq(now.minusDays(20));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        UUID po1 = insertPoWithLifecycle(
                rfq1, rev1, "PO-QUALITY-TEST", "COMPLETED",
                now.minusDays(30), now.minusDays(20), now.minusDays(18), null, "PO_ON_TIME", BigDecimal.ZERO
        );

        // Past verified receipt 15 days ago: 100 inspected, 90 accepted (90% yield)
        insertVerifiedOriginalReceipt(po1, 1, now.minusDays(15), new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("90.00"), new BigDecimal("10.00"));

        // Future verified receipt 5 days ago (AFTER T): 100 inspected, 10 accepted (10% yield)
        insertVerifiedOriginalReceipt(po1, 2, now.minusDays(5), new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("90.00"));

        UUID rfqTarget = createRfq(now.minusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-7", "ISSUED",
                tCutoff, now.minusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // Historical yield known at T must be exactly 90.00%, unaffected by the future receipt
        assertNotNull(fv.histNetUsableYield());
        assertEquals(new BigDecimal("90.00"), fv.histNetUsableYield());
    }

    @Test
    @DisplayName("8. A future financial adjustment cannot affect historical quality or cost features")
    void testFutureAdjustmentCannotAffectHistoricalFeatures() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        UUID rfq1 = createRfq(now.minusDays(20));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        UUID po1 = insertPoWithLifecycle(
                rfq1, rev1, "PO-ADJUSTMENT-TEST", "COMPLETED",
                now.minusDays(30), now.minusDays(20), now.minusDays(18), null, "PO_ON_TIME", BigDecimal.ZERO
        );

        // Past receipt at T-15d: 100 inspected, 100 accepted (100% yield)
        UUID originalReceiptId = insertVerifiedOriginalReceipt(
                po1, 1, now.minusDays(15), new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO
        );

        // Future latent defect return at T-5d (AFTER T): -50 accepted quantity
        insertVerifiedAdjustment(po1, originalReceiptId, 2, now.minusDays(5), new BigDecimal("-50.0000"));

        UUID rfqTarget = createRfq(now.minusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-8", "ISSUED",
                tCutoff, now.minusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // Historical yield at T must remain 100.00%, unaffected by future adjustment
        assertNotNull(fv.histNetUsableYield());
        assertEquals(new BigDecimal("100.00"), fv.histNetUsableYield());
    }

    @Test
    @DisplayName("9. The target PO cannot contribute to its own features")
    void testTargetPoCannotContributeToItsOwnFeatures() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        // Target PO placed at T, then receives late delivery at T+2
        UUID rfqTarget = createRfq(now.minusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-9", "COMPLETED",
                tCutoff, now.minusDays(5), now.minusDays(8), null, "PO_DELAYED", BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // Target PO must NOT contribute to its own historical metrics
        assertEquals(0, fv.histEligiblePoCount());
        assertEquals(0, fv.histOntimePoCount());
        assertNull(fv.histPointInTimeOtdRate());
    }

    @Test
    @DisplayName("10. Missing historical data remains null (Non-Zero Unknown Principle)")
    void testMissingHistoricalDataRemainsNull() {
        OffsetDateTime now = OffsetDateTime.now();

        // New supplier with ZERO historical orders or receipts
        UUID newSupplierId = db.queryForObject(
                "INSERT INTO suppliers(name, location, is_synthetic) VALUES ('Zero History Supplier', 'Hubli', true) RETURNING id",
                UUID.class
        );

        UUID rfqTarget = createRfq(now.plusDays(7));
        UUID quoteId = db.queryForObject("INSERT INTO supplier_quotes (rfq_id, supplier_id) VALUES (?, ?) RETURNING id", UUID.class, rfqTarget, newSupplierId);
        UUID revTarget = db.queryForObject(
                "INSERT INTO supplier_quote_revisions (supplier_quote_id, rfq_id, revision_no, state, received_at, raw_message, raw_message_sha256, fields, completeness_status) VALUES (?, ?, 1, 'VERIFIED', now(), 'M', 's', '{}'::jsonb, 'COMPLETE') RETURNING id",
                UUID.class, quoteId, rfqTarget
        );
        db.update("UPDATE supplier_quotes SET current_revision_id = ?, awarded_revision_id = ? WHERE id = ?", revTarget, revTarget, quoteId);

        UUID targetPoId = db.queryForObject(
                """
                INSERT INTO purchase_orders (
                    po_number, rfq_id, supplier_id, quote_revision_id, material_id,
                    ordered_quantity, unit, agreed_unit_price, agreed_freight_amount, freight_basis,
                    material_tax_rate, material_itc_status, freight_tax_rate, freight_itc_status,
                    quoted_economic_landed_cost, quoted_cash_payable, promised_delivery_date,
                    status, unfulfilled_quantity, po_snapshot_hash, created_at
                ) VALUES (
                    ?, ?, ?, ?, ?,
                    500.0000, 'kg', 100.00, 0.00, 'INCLUDED_IN_UNIT_PRICE',
                    18.00, 'CREDITABLE', 0.00, 'CREDITABLE',
                    50000.00, 59000.00, ?,
                    'ISSUED', 0.0000, 'fake', now()
                ) RETURNING id
                """,
                UUID.class,
                "PO-ZERO-HIST-" + UUID.randomUUID(),
                rfqTarget, newSupplierId, revTarget, materialId, Timestamp.from(now.plusDays(7).toInstant())
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // Enforce Non-Zero Unknown Principle:
        assertEquals(0, fv.histEligiblePoCount());
        assertEquals(0, fv.histOntimePoCount());
        assertNull(fv.histPointInTimeOtdRate(), "Must be null, not 0.00");
        assertNull(fv.histAvgDelayDays(), "Must be null, not 0.0");
        assertNull(fv.histNetUsableYield(), "Must be null, not 0.00");
        assertNull(fv.histPartialDeliveryRate(), "Must be null, not 0.00");
        assertEquals(0, fv.histMaterialPoCount());
        assertFalse(fv.hasMaterialEvidence());
    }

    @Test
    @DisplayName("11. Receipt created before T but verified after T is excluded")
    void testReceiptCreatedBeforeCutoffVerifiedAfterCutoffIsExcluded() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        UUID rfq1 = createRfq(now.minusDays(20));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        UUID po1 = insertPoWithLifecycle(
                rfq1, rev1, "PO-RECEIPT-VERIF-CUTOFF", "COMPLETED",
                now.minusDays(30), now.minusDays(20), now.minusDays(18), null, "PO_ON_TIME", BigDecimal.ZERO
        );

        // Created at T-15d, inspected at T-14d, but verified at T-5d (AFTER T)
        insertCustomOriginalReceipt(
                po1, 1, now.minusDays(15), now.minusDays(14), now.minusDays(5), "VERIFIED",
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("90.00"), new BigDecimal("10.00")
        );

        UUID rfqTarget = createRfq(now.minusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-11", "ISSUED",
                tCutoff, now.minusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // Must be null: unverified at T
        assertNull(fv.histNetUsableYield(), "Receipt verified after T must be excluded from historical yield");
    }

    @Test
    @DisplayName("12. Receipt created and verified before T contributes normally")
    void testReceiptCreatedAndVerifiedBeforeCutoffContributesNormally() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        UUID rfq1 = createRfq(now.minusDays(20));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        UUID po1 = insertPoWithLifecycle(
                rfq1, rev1, "PO-RECEIPT-VALID-T", "COMPLETED",
                now.minusDays(30), now.minusDays(20), now.minusDays(18), null, "PO_ON_TIME", BigDecimal.ZERO
        );

        // Created at T-15d, inspected at T-14d, verified at T-12d (all strictly before T)
        insertCustomOriginalReceipt(
                po1, 1, now.minusDays(15), now.minusDays(14), now.minusDays(12), "VERIFIED",
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("95.00"), new BigDecimal("5.00")
        );

        UUID rfqTarget = createRfq(now.minusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-12", "ISSUED",
                tCutoff, now.minusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        assertNotNull(fv.histNetUsableYield());
        assertEquals(new BigDecimal("95.00"), fv.histNetUsableYield());
    }

    @Test
    @DisplayName("13. Quality inspection completed after T does not contribute to historical quality features")
    void testQualityInspectionCompletedAfterCutoffDoesNotContribute() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        UUID rfq1 = createRfq(now.minusDays(20));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        UUID po1 = insertPoWithLifecycle(
                rfq1, rev1, "PO-INSPECT-AFTER-T", "COMPLETED",
                now.minusDays(30), now.minusDays(20), now.minusDays(18), null, "PO_ON_TIME", BigDecimal.ZERO
        );

        // Intake created at T-15d, but inspection occurred at T-5d (after T) and verified at T-4d
        insertCustomOriginalReceipt(
                po1, 1, now.minusDays(15), now.minusDays(5), now.minusDays(4), "VERIFIED",
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal("50.00")
        );

        UUID rfqTarget = createRfq(now.minusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-13", "ISSUED",
                tCutoff, now.minusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // Quality inspection performed after T must not leak into features at T
        assertNull(fv.histNetUsableYield());
    }

    @Test
    @DisplayName("14. PO short-closed before T and reopened after T remains a failure at T")
    void testPoShortClosedBeforeCutoffReopenedAfterCutoffRemainsFailure() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        // PO placed 30 days ago, promised 20 days ago, short-closed 15 days ago
        UUID rfq1 = createRfq(now.minusDays(20));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        UUID po1 = insertPoWithLifecycle(
                rfq1, rev1, "PO-SC-THEN-REOPEN-POST-T", "PARTIALLY_DELIVERED",
                now.minusDays(30), now.minusDays(20), null, now.minusDays(15), "PO_SHORT_CLOSED", new BigDecimal("500.0000")
        );

        // Reopened at T-5d (after cutoff T)
        insertPoAuditEvent(po1, "PURCHASE_ORDER_REOPENED", now.minusDays(5));

        UUID rfqTarget = createRfq(now.minusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-14", "ISSUED",
                tCutoff, now.minusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // At T (10 days ago), the latest action was PURCHASE_ORDER_CLOSED (15 days ago)
        assertEquals(1, fv.histEligiblePoCount(), "Must remain eligible as failure at T");
        assertEquals(0, fv.histOntimePoCount());
        assertEquals(new BigDecimal("0.00"), fv.histPointInTimeOtdRate());
    }

    @Test
    @DisplayName("15. PO short-closed and reopened before T is treated according to its reopened state at T")
    void testPoShortClosedAndReopenedBeforeCutoffTreatedAccordingToReopenedState() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        // PO placed 30 days ago, promised in future (plus 5 days relative to now)
        UUID rfq1 = createRfq(now.plusDays(5));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        UUID po1 = insertPoWithLifecycle(
                rfq1, rev1, "PO-SC-THEN-REOPEN-PRE-T", "PARTIALLY_DELIVERED",
                now.minusDays(30), now.plusDays(5), null, now.minusDays(20), "PO_SHORT_CLOSED", BigDecimal.ZERO
        );

        // Reopened at T-15d (before cutoff T)
        insertPoAuditEvent(po1, "PURCHASE_ORDER_REOPENED", now.minusDays(15));

        UUID rfqTarget = createRfq(now.plusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-15", "ISSUED",
                tCutoff, now.plusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // At T, the latest action was REOPENED. The promised date is in the future.
        // It is an in-flight order not yet due -> right-censored, NOT a failure!
        assertEquals(0, fv.histEligiblePoCount(), "Reopened in-flight order not yet due at T must be right-censored");
        assertNull(fv.histPointInTimeOtdRate());
    }

    @Test
    @DisplayName("16. PO short-closed before T, reopened after T, and completed later retains its historical failure outcome at T")
    void testPoShortClosedBeforeCutoffReopenedAfterCutoffAndCompletedLaterRetainsFailure() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        UUID rfq1 = createRfq(now.minusDays(20));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        UUID po1 = insertPoWithLifecycle(
                rfq1, rev1, "PO-SC-REOPEN-COMPL-LATER", "COMPLETED",
                now.minusDays(30), now.minusDays(20), now.minusDays(5), now.minusDays(15), "PO_ON_TIME", BigDecimal.ZERO
        );

        // Reopened at T-8d (after T), then completed at T-5d (after T)
        insertPoAuditEvent(po1, "PURCHASE_ORDER_REOPENED", now.minusDays(8));

        UUID rfqTarget = createRfq(now.minusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-16", "ISSUED",
                tCutoff, now.minusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // At cutoff T (10 days ago), the order was short-closed. Later delivery at T-5d cannot rewrite past state!
        assertEquals(1, fv.histEligiblePoCount(), "Historical failure must not be overwritten by future completion");
        assertEquals(0, fv.histOntimePoCount());
        assertEquals(new BigDecimal("0.00"), fv.histPointInTimeOtdRate());
    }

    @Test
    @DisplayName("17. Future adjustments remain excluded")
    void testFutureAdjustmentsRemainExcludedDedicated() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        UUID rfq1 = createRfq(now.minusDays(20));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        UUID po1 = insertPoWithLifecycle(
                rfq1, rev1, "PO-ADJ-EXCLUDED-DEDICATED", "COMPLETED",
                now.minusDays(30), now.minusDays(20), now.minusDays(18), null, "PO_ON_TIME", BigDecimal.ZERO
        );

        // Receipt verified at T-15d: 100 inspected, 100 accepted
        UUID origReceiptId = insertCustomOriginalReceipt(
                po1, 1, now.minusDays(15), now.minusDays(15), now.minusDays(15), "VERIFIED",
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO
        );

        // Adjustment recorded at T-3d (after cutoff T): -30 accepted
        insertVerifiedAdjustment(po1, origReceiptId, 2, now.minusDays(3), new BigDecimal("-30.0000"));

        UUID rfqTarget = createRfq(now.minusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-17", "ISSUED",
                tCutoff, now.minusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // Yield at T must be 100.00%, unaffected by the adjustment at T-3d
        assertEquals(new BigDecimal("100.00"), fv.histNetUsableYield());
    }

    @Test
    @DisplayName("18. Missing or ambiguous lifecycle history is excluded safely")
    void testMissingOrAmbiguousLifecycleHistoryExcludedSafely() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);

        // PO inserted WITHOUT any audit events
        UUID rfq1 = createRfq(now.minusDays(20));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        insertPoWithoutAuditEvents(
                rfq1, rev1, "PO-AMBIGUOUS-NO-AUDIT", "COMPLETED",
                now.minusDays(30), now.minusDays(20), now.minusDays(15), null, "PO_ON_TIME", BigDecimal.ZERO
        );

        UUID rfqTarget = createRfq(now.minusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-18", "ISSUED",
                tCutoff, now.minusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // The PO with missing audit events must be safely excluded rather than guessing
        assertEquals(0, fv.histEligiblePoCount(), "PO with missing audit trail must be excluded");
        assertNull(fv.histPointInTimeOtdRate());
    }

    @Test
    @DisplayName("19. Simultaneous timestamp audit events are strictly ordered by entity_version")
    void testSimultaneousTimestampAuditEventOrderingByEntityVersion() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime tCutoff = now.minusDays(10);
        OffsetDateTime eventTime = now.minusDays(15);

        // PO placed 30 days ago, promised in future
        UUID rfq1 = createRfq(now.plusDays(5));
        UUID rev1 = createSupplierQuoteAndRevision(rfq1);
        UUID po1 = insertPoWithLifecycle(
                rfq1, rev1, "PO-TIE-BREAK-VERSION", "PARTIALLY_DELIVERED",
                now.minusDays(30), now.plusDays(5), null, null, null, BigDecimal.ZERO
        );

        // Insert CLOSED (version 1) and REOPENED (version 2) with IDENTICAL timestamps
        insertPoAuditEvent(po1, "PURCHASE_ORDER_CLOSED", eventTime, 1);
        insertPoAuditEvent(po1, "PURCHASE_ORDER_REOPENED", eventTime, 2);

        UUID rfqTarget = createRfq(now.plusDays(5));
        UUID revTarget = createSupplierQuoteAndRevision(rfqTarget);
        UUID targetPoId = insertPoWithLifecycle(
                rfqTarget, revTarget, "PO-TARGET-19", "ISSUED",
                tCutoff, now.plusDays(5), null, null, null, BigDecimal.ZERO
        );

        Optional<SupplierDeliveryFeatureVector> fvOpt = featureRepository.extractFeaturesForPurchaseOrder(targetPoId, tCutoff);
        assertTrue(fvOpt.isPresent());
        SupplierDeliveryFeatureVector fv = fvOpt.get();

        // The query must select entity_version = 2 (REOPENED) over entity_version = 1 (CLOSED)
        // With promised date in future, REOPENED order is in-flight not yet due -> excluded, not failure!
        assertEquals(0, fv.histEligiblePoCount(), "Later entity_version (REOPENED) must break tie over earlier CLOSED");
        assertNull(fv.histPointInTimeOtdRate());
    }
}
