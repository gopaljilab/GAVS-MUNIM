package org.munim.intelligence;

import org.munim.intelligence.SupplierPerformanceDtos.*;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Deterministic Supplier Performance Intelligence Engine.
 * Implements empirical outcome evaluations per Revision 2.6 of MUNIM_V2_SUPPLIER_INTELLIGENCE_PLAN.md:
 * - PO-Level Contract On-Time Delivery (OTD %)
 * - Consignment-Level Logistics Throughput & On-Time Rate
 * - Consignments per Completed PO and Partial-Delivery Order Rate
 * - Gate Acceptance Rate (Initial Conformance)
 * - Net Usable Yield Rate (Post-Adjustment Manufacturing Yield)
 * - Latent Defect Return Rate
 * - Economic Landed Cost Variance (%) & Net Landed Spend
 * - Evidence-Based Performance Confidence Tiers
 */
@Component
public class SupplierPerformanceEngine {

    public SupplierScorecardView calculateScorecard(
            UUID supplierId,
            String supplierName,
            List<PoPerformanceRecord> orders,
            List<ReceiptPerformanceRecord> receipts,
            OffsetDateTime evaluationTime
    ) {
        OffsetDateTime evalTime = evaluationTime != null ? evaluationTime : OffsetDateTime.now();
        LocalDate evalDate = evalTime.toLocalDate();

        // ---------------------------------------------------------------------
        // 1. PO-Level Delivery Metrics (Headline Contract Performance)
        // ---------------------------------------------------------------------
        int eligiblePoCount = 0;
        int onTimePoCount = 0;
        int delayedPoCount = 0;
        int shortClosedPoCount = 0;
        long totalDelayDays = 0;

        List<PoPerformanceRecord> activeOrders = orders != null ? orders : Collections.emptyList();

        for (PoPerformanceRecord po : activeOrders) {
            if ("CANCELLED".equalsIgnoreCase(po.status())) {
                continue; // Cancelled orders strictly excluded
            }

            LocalDate promisedDate = po.promisedDeliveryDate().toLocalDate();
            boolean isCompleted = po.firstCompletedAt() != null ||
                    ("COMPLETED".equalsIgnoreCase(po.status()) && !"PO_SHORT_CLOSED".equalsIgnoreCase(po.poDeliveryStatus()));
            boolean isShortClosed = "PO_SHORT_CLOSED".equalsIgnoreCase(po.poDeliveryStatus()) ||
                    ("COMPLETED".equalsIgnoreCase(po.status()) && po.unfulfilledQuantity() != null && po.unfulfilledQuantity().compareTo(BigDecimal.ZERO) > 0);
            boolean isOpenOverdue = ("ISSUED".equalsIgnoreCase(po.status()) || "PARTIALLY_DELIVERED".equalsIgnoreCase(po.status())) &&
                    po.firstCompletedAt() == null && evalDate.isAfter(promisedDate);

            // Eligibility determination
            if (isCompleted || isShortClosed || isOpenOverdue) {
                eligiblePoCount++;

                if (isShortClosed) {
                    shortClosedPoCount++;
                    // Short-closure is strictly non-compliant; excluded from on-time numerator
                } else if (isCompleted) {
                    LocalDate completedDate = po.firstCompletedAt() != null ? po.firstCompletedAt().toLocalDate() : evalDate;
                    if (!completedDate.isAfter(promisedDate)) {
                        onTimePoCount++;
                    } else {
                        delayedPoCount++;
                        long delay = ChronoUnit.DAYS.between(promisedDate, completedDate);
                        totalDelayDays += Math.max(1, delay);
                    }
                } else if (isOpenOverdue) {
                    delayedPoCount++;
                    long delay = ChronoUnit.DAYS.between(promisedDate, evalDate);
                    totalDelayDays += Math.max(1, delay);
                }
            }
            // POs that are open and not yet due (evalDate <= promisedDate) are excluded
        }

        BigDecimal contractOtdRate = null;
        if (eligiblePoCount > 0) {
            contractOtdRate = BigDecimal.valueOf(onTimePoCount)
                    .multiply(new BigDecimal("100.00"))
                    .divide(BigDecimal.valueOf(eligiblePoCount), 2, RoundingMode.HALF_UP);
        }

        BigDecimal averagePoDelayDays = null;
        if (delayedPoCount > 0) {
            averagePoDelayDays = BigDecimal.valueOf(totalDelayDays)
                    .divide(BigDecimal.valueOf(delayedPoCount), 1, RoundingMode.HALF_UP);
        }

        // ---------------------------------------------------------------------
        // 2. Consignment-Level Logistics Metrics
        // ---------------------------------------------------------------------
        List<ReceiptPerformanceRecord> allReceipts = receipts != null ? receipts : Collections.emptyList();

        List<ReceiptPerformanceRecord> verifiedOriginalReceipts = new ArrayList<>();
        List<ReceiptPerformanceRecord> verifiedAdjustments = new ArrayList<>();

        for (ReceiptPerformanceRecord r : allReceipts) {
            if ("VERIFIED".equalsIgnoreCase(r.verificationStatus())) {
                if ("ORIGINAL".equalsIgnoreCase(r.recordType()) && "RECORDED".equalsIgnoreCase(r.stageDelivery())) {
                    verifiedOriginalReceipts.add(r);
                } else if ("CORRECTION_ADJUSTMENT".equalsIgnoreCase(r.recordType())) {
                    verifiedAdjustments.add(r);
                }
            }
        }

        int consignmentCount = verifiedOriginalReceipts.size();
        int onTimeConsignments = 0;
        Map<UUID, Integer> poReceiptCounts = new HashMap<>();

        for (ReceiptPerformanceRecord r : verifiedOriginalReceipts) {
            if ("ON_TIME".equalsIgnoreCase(r.deliveryStatus()) || "EARLY".equalsIgnoreCase(r.deliveryStatus())) {
                onTimeConsignments++;
            }
            poReceiptCounts.merge(r.purchaseOrderId(), 1, Integer::sum);
        }

        BigDecimal consignmentOnTimeRate = null;
        if (consignmentCount > 0) {
            consignmentOnTimeRate = BigDecimal.valueOf(onTimeConsignments)
                    .multiply(new BigDecimal("100.00"))
                    .divide(BigDecimal.valueOf(consignmentCount), 2, RoundingMode.HALF_UP);
        }

        // Measure consignments per completed PO and partial delivery rate
        int completedPosWithHistoryCount = 0;
        int totalConsignmentsForCompletedPos = 0;
        int multiConsignmentPoCount = 0;

        for (PoPerformanceRecord po : activeOrders) {
            if ("CANCELLED".equalsIgnoreCase(po.status())) continue;
            boolean isCompletedOrClosed = "COMPLETED".equalsIgnoreCase(po.status()) ||
                    po.firstCompletedAt() != null ||
                    "PO_SHORT_CLOSED".equalsIgnoreCase(po.poDeliveryStatus());

            if (isCompletedOrClosed) {
                int count = poReceiptCounts.getOrDefault(po.id(), 0);
                if (count >= 1) {
                    completedPosWithHistoryCount++;
                    totalConsignmentsForCompletedPos += count;
                    if (count > 1) {
                        multiConsignmentPoCount++;
                    }
                }
            }
        }

        BigDecimal averageConsignmentsPerCompletedPo = null;
        BigDecimal partialDeliveryOrderRate = null;
        if (completedPosWithHistoryCount > 0) {
            averageConsignmentsPerCompletedPo = BigDecimal.valueOf(totalConsignmentsForCompletedPos)
                    .divide(BigDecimal.valueOf(completedPosWithHistoryCount), 2, RoundingMode.HALF_UP);
            partialDeliveryOrderRate = BigDecimal.valueOf(multiConsignmentPoCount)
                    .multiply(new BigDecimal("100.00"))
                    .divide(BigDecimal.valueOf(completedPosWithHistoryCount), 2, RoundingMode.HALF_UP);
        }

        // ---------------------------------------------------------------------
        // 3. Quality Metrics: Gate Conformance vs. Net Usable Yield
        // ---------------------------------------------------------------------
        BigDecimal totalInspectedQty = BigDecimal.ZERO;
        BigDecimal totalAcceptedQty = BigDecimal.ZERO;

        for (ReceiptPerformanceRecord r : verifiedOriginalReceipts) {
            if ("INSPECTED".equalsIgnoreCase(r.stageQuality()) && r.inspectedQuantity() != null) {
                totalInspectedQty = totalInspectedQty.add(r.inspectedQuantity());
                if (r.acceptedQuantity() != null) {
                    totalAcceptedQty = totalAcceptedQty.add(r.acceptedQuantity());
                }
            }
        }

        BigDecimal gateAcceptanceRate = null;
        if (totalInspectedQty.compareTo(BigDecimal.ZERO) > 0) {
            gateAcceptanceRate = totalAcceptedQty
                    .multiply(new BigDecimal("100.00"))
                    .divide(totalInspectedQty, 2, RoundingMode.HALF_UP);
        }

        BigDecimal deltaInspectedTotal = BigDecimal.ZERO;
        BigDecimal deltaAcceptedTotal = BigDecimal.ZERO;
        BigDecimal latentDefectsTotal = BigDecimal.ZERO;

        for (ReceiptPerformanceRecord adj : verifiedAdjustments) {
            if (adj.deltaInspectedQuantity() != null) {
                deltaInspectedTotal = deltaInspectedTotal.add(adj.deltaInspectedQuantity());
            }
            if (adj.deltaAcceptedQuantity() != null) {
                deltaAcceptedTotal = deltaAcceptedTotal.add(adj.deltaAcceptedQuantity());
                if (adj.deltaAcceptedQuantity().compareTo(BigDecimal.ZERO) < 0) {
                    latentDefectsTotal = latentDefectsTotal.add(adj.deltaAcceptedQuantity().abs());
                }
            }
        }

        BigDecimal netInspectedQty = totalInspectedQty.add(deltaInspectedTotal);
        BigDecimal netAcceptedQty = totalAcceptedQty.add(deltaAcceptedTotal);

        BigDecimal netUsableYieldRate = null;
        if (netInspectedQty.compareTo(BigDecimal.ZERO) > 0) {
            netUsableYieldRate = netAcceptedQty
                    .multiply(new BigDecimal("100.00"))
                    .divide(netInspectedQty, 2, RoundingMode.HALF_UP);
        }

        BigDecimal latentDefectRate = null;
        if (totalAcceptedQty.compareTo(BigDecimal.ZERO) > 0) {
            if (latentDefectsTotal.compareTo(BigDecimal.ZERO) == 0) {
                latentDefectRate = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            } else {
                latentDefectRate = latentDefectsTotal
                        .multiply(new BigDecimal("100.00"))
                        .divide(totalAcceptedQty, 2, RoundingMode.HALF_UP);
            }
        }

        // ---------------------------------------------------------------------
        // 4. Cost Variance & Spend Metrics
        // ---------------------------------------------------------------------
        BigDecimal totalActualLandedSpend = BigDecimal.ZERO;
        BigDecimal totalBaselineLanded = BigDecimal.ZERO;
        BigDecimal totalCostVarianceAmount = BigDecimal.ZERO;
        int reconciledCount = 0;

        for (ReceiptPerformanceRecord r : verifiedOriginalReceipts) {
            if ("RECONCILED".equalsIgnoreCase(r.stageInvoice()) && r.actualEconomicLandedCost() != null) {
                reconciledCount++;
                totalActualLandedSpend = totalActualLandedSpend.add(r.actualEconomicLandedCost());

                BigDecimal variance = r.costVarianceAmount() != null ? r.costVarianceAmount() : BigDecimal.ZERO;
                totalCostVarianceAmount = totalCostVarianceAmount.add(variance);

                BigDecimal baseline = r.actualEconomicLandedCost().subtract(variance);
                totalBaselineLanded = totalBaselineLanded.add(baseline);
            }
        }

        for (ReceiptPerformanceRecord adj : verifiedAdjustments) {
            if (adj.deltaEconomicLandedCost() != null) {
                totalActualLandedSpend = totalActualLandedSpend.add(adj.deltaEconomicLandedCost());
                totalCostVarianceAmount = totalCostVarianceAmount.add(adj.deltaEconomicLandedCost());
            }
        }

        BigDecimal costVariancePercentage = null;
        if (reconciledCount > 0 && totalBaselineLanded.compareTo(BigDecimal.ZERO) > 0) {
            costVariancePercentage = totalCostVarianceAmount
                    .multiply(new BigDecimal("100.00"))
                    .divide(totalBaselineLanded, 2, RoundingMode.HALF_UP);
        }

        // ---------------------------------------------------------------------
        // 5. Evidence-Based Performance Confidence Tier
        // ---------------------------------------------------------------------
        String confidenceTier;
        String confidenceExplanation;

        if (completedPosWithHistoryCount == 0) {
            confidenceTier = "UNRATED";
            confidenceExplanation = "No verified purchase history";
        } else if (completedPosWithHistoryCount == 1) {
            confidenceTier = "LOW";
            confidenceExplanation = "Single order history — preliminary";
        } else if (completedPosWithHistoryCount <= 4) {
            confidenceTier = "MEDIUM";
            confidenceExplanation = String.format("%d completed purchase orders with verified outcomes", completedPosWithHistoryCount);
        } else {
            confidenceTier = "HIGH";
            confidenceExplanation = String.format("%d completed purchase orders across distinct orders", completedPosWithHistoryCount);
        }

        return new SupplierScorecardView(
                supplierId,
                supplierName,
                contractOtdRate,
                eligiblePoCount,
                onTimePoCount,
                delayedPoCount,
                shortClosedPoCount,
                averagePoDelayDays,
                consignmentCount,
                consignmentOnTimeRate,
                averageConsignmentsPerCompletedPo,
                partialDeliveryOrderRate,
                gateAcceptanceRate,
                netUsableYieldRate,
                latentDefectRate,
                totalInspectedQty.setScale(4, RoundingMode.HALF_UP),
                costVariancePercentage,
                totalActualLandedSpend.setScale(2, RoundingMode.HALF_UP),
                confidenceTier,
                confidenceExplanation
        );
    }
}
