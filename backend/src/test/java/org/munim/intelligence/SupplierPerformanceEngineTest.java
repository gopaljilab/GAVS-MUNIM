package org.munim.intelligence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.munim.intelligence.SupplierPerformanceDtos.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SupplierPerformanceEngine Unit Tests")
class SupplierPerformanceEngineTest {

    private SupplierPerformanceEngine engine;
    private UUID supplierId;
    private String supplierName;
    private UUID materialId;

    // Fixed synthetic reference dates
    private final OffsetDateTime baseDate = OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private final OffsetDateTime day2 = baseDate.plusDays(2);
    private final OffsetDateTime day3 = baseDate.plusDays(3);
    private final OffsetDateTime day4 = baseDate.plusDays(4);
    private final OffsetDateTime day5 = baseDate.plusDays(5);
    private final OffsetDateTime day7 = baseDate.plusDays(7);
    private final OffsetDateTime day8 = baseDate.plusDays(8);
    private final OffsetDateTime day9 = baseDate.plusDays(9);
    private final OffsetDateTime day10 = baseDate.plusDays(10);
    private final OffsetDateTime day14 = baseDate.plusDays(14);
    private final OffsetDateTime day20 = baseDate.plusDays(20);

    @BeforeEach
    void setUp() {
        engine = new SupplierPerformanceEngine();
        supplierId = UUID.randomUUID();
        supplierName = "Synthetic Steel Works Pvt Ltd";
        materialId = UUID.randomUUID();
    }

    private PoPerformanceRecord createPo(
            UUID id, BigDecimal qty, OffsetDateTime promisedDate, String status,
            BigDecimal unfulfilledQty, OffsetDateTime firstCompletedAt, String poDeliveryStatus
    ) {
        return new PoPerformanceRecord(
                id, "PO-SYN-001", supplierId, materialId, qty, promisedDate,
                status, unfulfilledQty, firstCompletedAt, poDeliveryStatus, baseDate
        );
    }

    private ReceiptPerformanceRecord createReceipt(
            UUID id, UUID poId, int seq, String recordType, UUID supersededId,
            String adjType, OffsetDateTime actualDate, BigDecimal receivedQty,
            String deliveryStatus, Integer delayDays, String qualityStage,
            BigDecimal inspectedQty, BigDecimal acceptedQty, BigDecimal rejectedQty,
            String invoiceStage, BigDecimal landedCost, BigDecimal costVariance,
            BigDecimal deltaRecv, BigDecimal deltaInsp, BigDecimal deltaAcc, BigDecimal deltaRej,
            BigDecimal deltaCost
    ) {
        return new ReceiptPerformanceRecord(
                id, poId, supplierId, materialId, seq, recordType, supersededId, adjType,
                "RECORDED", actualDate, receivedQty, deliveryStatus, delayDays,
                qualityStage, inspectedQty, acceptedQty, rejectedQty,
                invoiceStage, landedCost, costVariance,
                deltaRecv, deltaInsp, deltaAcc, deltaRej, deltaCost,
                "VERIFIED", actualDate
        );
    }

    @Test
    @DisplayName("1. Single complete on-time receipt: PO 1000kg due Day 5, delivers Day 4 -> PO_ON_TIME, 100% OTD")
    void testSingleCompleteOnTimeReceipt() {
        UUID poId = UUID.randomUUID();
        PoPerformanceRecord po = createPo(poId, new BigDecimal("1000.0000"), day5, "COMPLETED", BigDecimal.ZERO, day4, "PO_ON_TIME");
        ReceiptPerformanceRecord r = createReceipt(
                UUID.randomUUID(), poId, 1, "ORIGINAL", null, null, day4, new BigDecimal("1000.0000"),
                "EARLY", -1, "INSPECTED", new BigDecimal("1000.0000"), new BigDecimal("1000.0000"), BigDecimal.ZERO,
                "RECONCILED", new BigDecimal("65000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        SupplierScorecardView sc = engine.calculateScorecard(supplierId, supplierName, List.of(po), List.of(r), day5);

        assertEquals(0, new BigDecimal("100.00").compareTo(sc.contractOtdRate()));
        assertEquals(1, sc.eligiblePoCount());
        assertEquals(1, sc.onTimePoCount());
        assertEquals(0, sc.delayedPoCount());
        assertEquals(0, sc.shortClosedPoCount());
        assertNull(sc.averagePoDelayDays());
        assertEquals(1, sc.consignmentCount());
        assertEquals(0, new BigDecimal("100.00").compareTo(sc.consignmentOnTimeRate()));
        assertEquals(0, new BigDecimal("1.00").compareTo(sc.averageConsignmentsPerCompletedPo()));
        assertEquals(0, new BigDecimal("0.00").compareTo(sc.partialDeliveryOrderRate()));
        assertEquals("LOW", sc.confidenceTier());
    }

    @Test
    @DisplayName("2. Multiple partial receipts completed on time: PO 2000kg due Day 10, Consignment 1 Day 4, Consignment 2 Day 9 -> 100% OTD, N_receipt=2")
    void testMultiplePartialReceiptsCompletedOnTime() {
        UUID poId = UUID.randomUUID();
        PoPerformanceRecord po = createPo(poId, new BigDecimal("2000.0000"), day10, "COMPLETED", BigDecimal.ZERO, day9, "PO_ON_TIME");

        ReceiptPerformanceRecord r1 = createReceipt(
                UUID.randomUUID(), poId, 1, "ORIGINAL", null, null, day4, new BigDecimal("1000.0000"),
                "EARLY", -6, "INSPECTED", new BigDecimal("1000.0000"), new BigDecimal("1000.0000"), BigDecimal.ZERO,
                "RECONCILED", new BigDecimal("65000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
        ReceiptPerformanceRecord r2 = createReceipt(
                UUID.randomUUID(), poId, 2, "ORIGINAL", null, null, day9, new BigDecimal("1000.0000"),
                "EARLY", -1, "INSPECTED", new BigDecimal("1000.0000"), new BigDecimal("1000.0000"), BigDecimal.ZERO,
                "RECONCILED", new BigDecimal("65000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        SupplierScorecardView sc = engine.calculateScorecard(supplierId, supplierName, List.of(po), List.of(r1, r2), day10);

        assertEquals(0, new BigDecimal("100.00").compareTo(sc.contractOtdRate()));
        assertEquals(1, sc.eligiblePoCount());
        assertEquals(2, sc.consignmentCount());
        assertEquals(0, new BigDecimal("2.00").compareTo(sc.averageConsignmentsPerCompletedPo()));
        assertEquals(0, new BigDecimal("100.00").compareTo(sc.partialDeliveryOrderRate())); // required multiple deliveries
    }

    @Test
    @DisplayName("3. Early partial receipt but final completion late: PO 2000kg due Day 10, Consignment 1 Day 8, Consignment 2 Day 14 -> PO_DELAYED (+4 days), 0% Contract OTD")
    void testPartialReceiptOnTimeButFinalCompletionLate() {
        UUID poId = UUID.randomUUID();
        PoPerformanceRecord po = createPo(poId, new BigDecimal("2000.0000"), day10, "COMPLETED", BigDecimal.ZERO, day14, "PO_DELAYED");

        ReceiptPerformanceRecord r1 = createReceipt(
                UUID.randomUUID(), poId, 1, "ORIGINAL", null, null, day8, new BigDecimal("1000.0000"),
                "EARLY", -2, "INSPECTED", new BigDecimal("1000.0000"), new BigDecimal("1000.0000"), BigDecimal.ZERO,
                "RECONCILED", new BigDecimal("65000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
        ReceiptPerformanceRecord r2 = createReceipt(
                UUID.randomUUID(), poId, 2, "ORIGINAL", null, null, day14, new BigDecimal("1000.0000"),
                "DELAYED", 4, "INSPECTED", new BigDecimal("1000.0000"), new BigDecimal("1000.0000"), BigDecimal.ZERO,
                "RECONCILED", new BigDecimal("65000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        SupplierScorecardView sc = engine.calculateScorecard(supplierId, supplierName, List.of(po), List.of(r1, r2), day14);

        assertEquals(0, new BigDecimal("0.00").compareTo(sc.contractOtdRate()));
        assertEquals(1, sc.eligiblePoCount());
        assertEquals(0, sc.onTimePoCount());
        assertEquals(1, sc.delayedPoCount());
        assertEquals(0, new BigDecimal("4.0").compareTo(sc.averagePoDelayDays()));
        // Note: Consignment OTD is 50.0% (1 early, 1 late), but Contract OTD is strictly 0.0%
        assertEquals(0, new BigDecimal("50.00").compareTo(sc.consignmentOnTimeRate()));
    }

    @Test
    @DisplayName("4. PO closed with unfulfilled quantity: 850kg delivered, closed with 150kg unfulfilled -> PO_SHORT_CLOSED, 0% OTD, in denominator")
    void testPoClosedWithUnfulfilledQuantity() {
        UUID poId = UUID.randomUUID();
        PoPerformanceRecord po = createPo(poId, new BigDecimal("1000.0000"), day5, "COMPLETED", new BigDecimal("150.0000"), null, "PO_SHORT_CLOSED");

        ReceiptPerformanceRecord r = createReceipt(
                UUID.randomUUID(), poId, 1, "ORIGINAL", null, null, day3, new BigDecimal("850.0000"),
                "EARLY", -2, "INSPECTED", new BigDecimal("850.0000"), new BigDecimal("850.0000"), BigDecimal.ZERO,
                "RECONCILED", new BigDecimal("55250.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        SupplierScorecardView sc = engine.calculateScorecard(supplierId, supplierName, List.of(po), List.of(r), day7);

        assertEquals(1, sc.eligiblePoCount());
        assertEquals(0, sc.onTimePoCount());
        assertEquals(1, sc.shortClosedPoCount());
        assertEquals(0, new BigDecimal("0.00").compareTo(sc.contractOtdRate()));
    }

    @Test
    @DisplayName("5. PO not yet due is excluded from denominator")
    void testPoNotYetDueExcludedFromDenominator() {
        UUID poId = UUID.randomUUID();
        PoPerformanceRecord po = createPo(poId, new BigDecimal("1000.0000"), day10, "ISSUED", BigDecimal.ZERO, null, null);

        SupplierScorecardView sc = engine.calculateScorecard(supplierId, supplierName, List.of(po), Collections.emptyList(), day5);

        assertEquals(0, sc.eligiblePoCount());
        assertNull(sc.contractOtdRate()); // null, not 0.00%
        assertEquals("UNRATED", sc.confidenceTier());
    }

    @Test
    @DisplayName("6. Supplier with 0 eligible POs returns null OTD, null Quality, null Cost, and UNRATED tier")
    void testNoEligiblePosYieldsNullOtd() {
        SupplierScorecardView sc = engine.calculateScorecard(supplierId, supplierName, Collections.emptyList(), Collections.emptyList(), day5);

        assertNull(sc.contractOtdRate());
        assertNull(sc.averagePoDelayDays());
        assertNull(sc.consignmentOnTimeRate());
        assertNull(sc.averageConsignmentsPerCompletedPo());
        assertNull(sc.partialDeliveryOrderRate());
        assertNull(sc.gateAcceptanceRate());
        assertNull(sc.netUsableYieldRate());
        assertNull(sc.latentDefectRate());
        assertNull(sc.costVariancePercentage());
        assertEquals("UNRATED", sc.confidenceTier());
    }

    @Test
    @DisplayName("7. Post-completion physical return: 1000kg completed on Day 4 (PO_ON_TIME). Day 20 return 100kg -> OTD preserved, Usable Yield 90%, Latent Defect 10%")
    void testPostCompletionPhysicalReturnPreservesOtd() {
        UUID poId = UUID.randomUUID();
        UUID origReceiptId = UUID.randomUUID();

        PoPerformanceRecord po = createPo(poId, new BigDecimal("1000.0000"), day5, "COMPLETED", BigDecimal.ZERO, day4, "PO_ON_TIME");

        ReceiptPerformanceRecord rOrig = createReceipt(
                origReceiptId, poId, 1, "ORIGINAL", null, null, day4, new BigDecimal("1000.0000"),
                "EARLY", -1, "INSPECTED", new BigDecimal("1000.0000"), new BigDecimal("1000.0000"), BigDecimal.ZERO,
                "RECONCILED", new BigDecimal("65000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        ReceiptPerformanceRecord rAdj = createReceipt(
                UUID.randomUUID(), poId, 2, "CORRECTION_ADJUSTMENT", origReceiptId, "PHYSICAL_RETURN", day20, null,
                null, null, null, null, null, null,
                null, null, null,
                new BigDecimal("-100.0000"), BigDecimal.ZERO, new BigDecimal("-100.0000"), BigDecimal.ZERO,
                new BigDecimal("-6500.00")
        );

        SupplierScorecardView sc = engine.calculateScorecard(supplierId, supplierName, List.of(po), List.of(rOrig, rAdj), day20);

        // OTD remains frozen
        assertEquals(0, new BigDecimal("100.00").compareTo(sc.contractOtdRate()));
        assertEquals(1, sc.onTimePoCount());

        // Quality: Gate Acceptance is 100%, Net Usable Yield is 900/1000 = 90%, Latent Defect is 100/1000 = 10%
        assertEquals(0, new BigDecimal("100.00").compareTo(sc.gateAcceptanceRate()));
        assertEquals(0, new BigDecimal("90.00").compareTo(sc.netUsableYieldRate()));
        assertEquals(0, new BigDecimal("10.00").compareTo(sc.latentDefectRate()));

        // Delivery count must NOT count adjustment as consignment
        assertEquals(1, sc.consignmentCount());
    }

    @Test
    @DisplayName("8. Pre-completion return delays initial completion: 500kg Day 2, -50kg Day 3, 500kg Day 4 (cum 950kg), 50kg Day 7 -> first_completed_at Day 7, PO_DELAYED")
    void testPreCompletionReturnDelaysInitialCompletion() {
        UUID poId = UUID.randomUUID();
        // Completed at Day 7, promised Day 5 -> delay = +2 days
        PoPerformanceRecord po = createPo(poId, new BigDecimal("1000.0000"), day5, "COMPLETED", BigDecimal.ZERO, day7, "PO_DELAYED");

        ReceiptPerformanceRecord r1 = createReceipt(
                UUID.randomUUID(), poId, 1, "ORIGINAL", null, null, day2, new BigDecimal("500.0000"),
                "EARLY", -3, "INSPECTED", new BigDecimal("500.0000"), new BigDecimal("500.0000"), BigDecimal.ZERO,
                "RECONCILED", new BigDecimal("32500.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
        ReceiptPerformanceRecord r2 = createReceipt(
                UUID.randomUUID(), poId, 2, "ORIGINAL", null, null, day4, new BigDecimal("500.0000"),
                "EARLY", -1, "INSPECTED", new BigDecimal("500.0000"), new BigDecimal("500.0000"), BigDecimal.ZERO,
                "RECONCILED", new BigDecimal("32500.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
        ReceiptPerformanceRecord r3 = createReceipt(
                UUID.randomUUID(), poId, 3, "ORIGINAL", null, null, day7, new BigDecimal("50.0000"),
                "DELAYED", 2, "INSPECTED", new BigDecimal("50.0000"), new BigDecimal("50.0000"), BigDecimal.ZERO,
                "RECONCILED", new BigDecimal("3250.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        SupplierScorecardView sc = engine.calculateScorecard(supplierId, supplierName, List.of(po), List.of(r1, r2, r3), day7);

        assertEquals(0, new BigDecimal("0.00").compareTo(sc.contractOtdRate()));
        assertEquals(1, sc.delayedPoCount());
        assertEquals(0, new BigDecimal("2.0").compareTo(sc.averagePoDelayDays()));
    }

    @Test
    @DisplayName("9. Financial-only credit note updates cost but leaves physical yield strictly unchanged")
    void testFinancialOnlyAdjustmentDoesNotAffectQuality() {
        UUID poId = UUID.randomUUID();
        UUID rId = UUID.randomUUID();
        PoPerformanceRecord po = createPo(poId, new BigDecimal("1000.0000"), day5, "COMPLETED", BigDecimal.ZERO, day4, "PO_ON_TIME");

        ReceiptPerformanceRecord rOrig = createReceipt(
                rId, poId, 1, "ORIGINAL", null, null, day4, new BigDecimal("1000.0000"),
                "EARLY", -1, "INSPECTED", new BigDecimal("1000.0000"), new BigDecimal("980.0000"), new BigDecimal("20.0000"),
                "RECONCILED", new BigDecimal("65000.00"), new BigDecimal("500.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        // Financial credit note of -1000 INR with ZERO physical deltas
        ReceiptPerformanceRecord rFin = createReceipt(
                UUID.randomUUID(), poId, 2, "CORRECTION_ADJUSTMENT", rId, "FINANCIAL_CREDIT_NOTE", day7, null,
                null, null, null, null, null, null,
                null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("-1000.00")
        );

        SupplierScorecardView sc = engine.calculateScorecard(supplierId, supplierName, List.of(po), List.of(rOrig, rFin), day7);

        // Quality metrics unchanged
        assertEquals(0, new BigDecimal("98.00").compareTo(sc.gateAcceptanceRate()));
        assertEquals(0, new BigDecimal("98.00").compareTo(sc.netUsableYieldRate()));
        assertEquals(0, new BigDecimal("0.00").compareTo(sc.latentDefectRate()));

        // Cost variance reflects -1000 credit: net variance = 500 - 1000 = -500 INR. Baseline = 65000 - 500 = 64500.
        // -500 / 64500 * 100 = -0.78%
        assertEquals(0, new BigDecimal("-0.78").compareTo(sc.costVariancePercentage()));
        assertEquals(0, new BigDecimal("64000.00").compareTo(sc.totalActualLandedCost())); // 65000 - 1000
    }

    @Test
    @DisplayName("10. Multiple adjustments do not inflate consignment count")
    void testMultipleAdjustmentsDoNotInflateConsignmentCount() {
        UUID poId = UUID.randomUUID();
        UUID rId = UUID.randomUUID();
        PoPerformanceRecord po = createPo(poId, new BigDecimal("1000.0000"), day5, "COMPLETED", BigDecimal.ZERO, day4, "PO_ON_TIME");

        ReceiptPerformanceRecord rOrig = createReceipt(
                rId, poId, 1, "ORIGINAL", null, null, day4, new BigDecimal("1000.0000"),
                "EARLY", -1, "INSPECTED", new BigDecimal("1000.0000"), new BigDecimal("1000.0000"), BigDecimal.ZERO,
                "RECONCILED", new BigDecimal("50000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        ReceiptPerformanceRecord adj1 = createReceipt(
                UUID.randomUUID(), poId, 2, "CORRECTION_ADJUSTMENT", rId, "PHYSICAL_RETURN", day7, null,
                null, null, null, null, null, null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-10.0000"), BigDecimal.ZERO, BigDecimal.ZERO
        );
        ReceiptPerformanceRecord adj2 = createReceipt(
                UUID.randomUUID(), poId, 3, "CORRECTION_ADJUSTMENT", rId, "FINANCIAL_CREDIT_NOTE", day8, null,
                null, null, null, null, null, null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-500.00")
        );

        SupplierScorecardView sc = engine.calculateScorecard(supplierId, supplierName, List.of(po), List.of(rOrig, adj1, adj2), day8);

        assertEquals(1, sc.consignmentCount(), "Consignment count must strictly count original gate deliveries only");
        assertEquals(0, new BigDecimal("1.00").compareTo(sc.averageConsignmentsPerCompletedPo()));
    }

    @Test
    @DisplayName("11. NOT_INSPECTED quality stage yields null acceptance and usable yield rather than 0%")
    void testMissingInspectionData() {
        UUID poId = UUID.randomUUID();
        PoPerformanceRecord po = createPo(poId, new BigDecimal("1000.0000"), day5, "COMPLETED", BigDecimal.ZERO, day4, "PO_ON_TIME");

        ReceiptPerformanceRecord r = createReceipt(
                UUID.randomUUID(), poId, 1, "ORIGINAL", null, null, day4, new BigDecimal("1000.0000"),
                "EARLY", -1, "NOT_INSPECTED", null, null, null,
                "RECONCILED", new BigDecimal("50000.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        SupplierScorecardView sc = engine.calculateScorecard(supplierId, supplierName, List.of(po), List.of(r), day5);

        assertNull(sc.gateAcceptanceRate(), "Uninspected receipt must yield null gate acceptance rate");
        assertNull(sc.netUsableYieldRate(), "Uninspected receipt must yield null net usable yield rate");
        assertNull(sc.latentDefectRate(), "Uninspected receipt must yield null latent defect rate");
    }

    @Test
    @DisplayName("12. Zero denominators return null across all metric categories")
    void testZeroDenominators() {
        SupplierScorecardView sc = engine.calculateScorecard(supplierId, supplierName, Collections.emptyList(), Collections.emptyList(), day5);

        assertNull(sc.contractOtdRate());
        assertNull(sc.averagePoDelayDays());
        assertNull(sc.consignmentOnTimeRate());
        assertNull(sc.averageConsignmentsPerCompletedPo());
        assertNull(sc.partialDeliveryOrderRate());
        assertNull(sc.gateAcceptanceRate());
        assertNull(sc.netUsableYieldRate());
        assertNull(sc.latentDefectRate());
        assertNull(sc.costVariancePercentage());
        assertEquals("UNRATED", sc.confidenceTier());
    }

    @Test
    @DisplayName("13. Supplier confidence tiers scale strictly with sample size (UNRATED -> LOW -> MEDIUM -> HIGH)")
    void testSupplierComparisonWithDifferentSampleSizes() {
        // 1 completed PO -> LOW
        PoPerformanceRecord po1 = createPo(UUID.randomUUID(), new BigDecimal("100.0000"), day5, "COMPLETED", BigDecimal.ZERO, day4, "PO_ON_TIME");
        ReceiptPerformanceRecord r1 = createReceipt(UUID.randomUUID(), po1.id(), 1, "ORIGINAL", null, null, day4, new BigDecimal("100.0000"), "ON_TIME", 0, "NOT_INSPECTED", null, null, null, "PENDING", null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals("LOW", engine.calculateScorecard(supplierId, supplierName, List.of(po1), List.of(r1), day5).confidenceTier());

        // 3 completed POs -> MEDIUM
        PoPerformanceRecord po2 = createPo(UUID.randomUUID(), new BigDecimal("100.0000"), day5, "COMPLETED", BigDecimal.ZERO, day4, "PO_ON_TIME");
        ReceiptPerformanceRecord r2 = createReceipt(UUID.randomUUID(), po2.id(), 1, "ORIGINAL", null, null, day4, new BigDecimal("100.0000"), "ON_TIME", 0, "NOT_INSPECTED", null, null, null, "PENDING", null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        PoPerformanceRecord po3 = createPo(UUID.randomUUID(), new BigDecimal("100.0000"), day5, "COMPLETED", BigDecimal.ZERO, day4, "PO_ON_TIME");
        ReceiptPerformanceRecord r3 = createReceipt(UUID.randomUUID(), po3.id(), 1, "ORIGINAL", null, null, day4, new BigDecimal("100.0000"), "ON_TIME", 0, "NOT_INSPECTED", null, null, null, "PENDING", null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals("MEDIUM", engine.calculateScorecard(supplierId, supplierName, List.of(po1, po2, po3), List.of(r1, r2, r3), day5).confidenceTier());

        // 5 completed POs -> HIGH
        PoPerformanceRecord po4 = createPo(UUID.randomUUID(), new BigDecimal("100.0000"), day5, "COMPLETED", BigDecimal.ZERO, day4, "PO_ON_TIME");
        ReceiptPerformanceRecord r4 = createReceipt(UUID.randomUUID(), po4.id(), 1, "ORIGINAL", null, null, day4, new BigDecimal("100.0000"), "ON_TIME", 0, "NOT_INSPECTED", null, null, null, "PENDING", null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        PoPerformanceRecord po5 = createPo(UUID.randomUUID(), new BigDecimal("100.0000"), day5, "COMPLETED", BigDecimal.ZERO, day4, "PO_ON_TIME");
        ReceiptPerformanceRecord r5 = createReceipt(UUID.randomUUID(), po5.id(), 1, "ORIGINAL", null, null, day4, new BigDecimal("100.0000"), "ON_TIME", 0, "NOT_INSPECTED", null, null, null, "PENDING", null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals("HIGH", engine.calculateScorecard(supplierId, supplierName, List.of(po1, po2, po3, po4, po5), List.of(r1, r2, r3, r4, r5), day5).confidenceTier());
    }

    @Test
    @DisplayName("14. Cost variance calculates positive and negative adjustments with accurate percentage")
    void testNegativeAndPositiveCostAdjustments() {
        UUID poId = UUID.randomUUID();
        UUID rId = UUID.randomUUID();
        PoPerformanceRecord po = createPo(poId, new BigDecimal("1000.0000"), day5, "COMPLETED", BigDecimal.ZERO, day4, "PO_ON_TIME");

        // Landed 100,000, Variance +5,000 (Baseline 95,000)
        ReceiptPerformanceRecord rOrig = createReceipt(
                rId, poId, 1, "ORIGINAL", null, null, day4, new BigDecimal("1000.0000"),
                "ON_TIME", 0, "INSPECTED", new BigDecimal("1000.0000"), new BigDecimal("1000.0000"), BigDecimal.ZERO,
                "RECONCILED", new BigDecimal("100000.00"), new BigDecimal("5000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        // Debit note +2,000
        ReceiptPerformanceRecord rDebit = createReceipt(
                UUID.randomUUID(), poId, 2, "CORRECTION_ADJUSTMENT", rId, "FINANCIAL_DEBIT_NOTE", day7, null,
                null, null, null, null, null, null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("2000.00")
        );

        // Credit note -1,000
        ReceiptPerformanceRecord rCredit = createReceipt(
                UUID.randomUUID(), poId, 3, "CORRECTION_ADJUSTMENT", rId, "FINANCIAL_CREDIT_NOTE", day8, null,
                null, null, null, null, null, null, null, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1000.00")
        );

        SupplierScorecardView sc = engine.calculateScorecard(supplierId, supplierName, List.of(po), List.of(rOrig, rDebit, rCredit), day8);

        // Net variance = 5000 + 2000 - 1000 = +6000 INR
        // Baseline = 95,000 INR
        // Cost Variance % = 6000 / 95000 * 100 = 6.32%
        assertEquals(0, new BigDecimal("6.32").compareTo(sc.costVariancePercentage()));
        assertEquals(0, new BigDecimal("101000.00").compareTo(sc.totalActualLandedCost())); // 100,000 + 2,000 - 1,000
    }

    @Test
    @DisplayName("15. Deterministic repeated calculations yield identical results")
    void testDeterministicRepeatedCalculations() {
        UUID poId = UUID.randomUUID();
        PoPerformanceRecord po = createPo(poId, new BigDecimal("1000.0000"), day5, "COMPLETED", BigDecimal.ZERO, day4, "PO_ON_TIME");
        ReceiptPerformanceRecord r = createReceipt(
                UUID.randomUUID(), poId, 1, "ORIGINAL", null, null, day4, new BigDecimal("1000.0000"),
                "EARLY", -1, "INSPECTED", new BigDecimal("1000.0000"), new BigDecimal("990.0000"), new BigDecimal("10.0000"),
                "RECONCILED", new BigDecimal("50000.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        SupplierScorecardView sc1 = engine.calculateScorecard(supplierId, supplierName, List.of(po), List.of(r), day5);
        SupplierScorecardView sc2 = engine.calculateScorecard(supplierId, supplierName, List.of(po), List.of(r), day5);

        assertEquals(sc1.contractOtdRate(), sc2.contractOtdRate());
        assertEquals(sc1.gateAcceptanceRate(), sc2.gateAcceptanceRate());
        assertEquals(sc1.netUsableYieldRate(), sc2.netUsableYieldRate());
        assertEquals(sc1.costVariancePercentage(), sc2.costVariancePercentage());
        assertEquals(sc1.confidenceTier(), sc2.confidenceTier());
    }
}
