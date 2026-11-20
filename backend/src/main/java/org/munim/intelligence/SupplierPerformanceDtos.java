package org.munim.intelligence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SupplierPerformanceDtos {
    private SupplierPerformanceDtos() {}

    public record SupplierScorecardView(
            UUID supplierId,
            String supplierName,
            BigDecimal contractOtdRate,
            int eligiblePoCount,
            int onTimePoCount,
            int delayedPoCount,
            int shortClosedPoCount,
            BigDecimal averagePoDelayDays,
            int consignmentCount,
            BigDecimal consignmentOnTimeRate,
            BigDecimal averageConsignmentsPerCompletedPo,
            BigDecimal partialDeliveryOrderRate,
            BigDecimal gateAcceptanceRate,
            BigDecimal netUsableYieldRate,
            BigDecimal latentDefectRate,
            BigDecimal inspectedQuantityTotal,
            BigDecimal costVariancePercentage,
            BigDecimal totalActualLandedCost,
            String confidenceTier,
            String confidenceExplanation
    ) {}

    public record SupplierScorecardList(
            List<SupplierScorecardView> items
    ) {}

    public record PoPerformanceRecord(
            UUID id,
            String poNumber,
            UUID supplierId,
            UUID materialId,
            BigDecimal orderedQuantity,
            OffsetDateTime promisedDeliveryDate,
            String status,
            BigDecimal unfulfilledQuantity,
            OffsetDateTime firstCompletedAt,
            String poDeliveryStatus,
            OffsetDateTime createdAt
    ) {}

    public record ReceiptPerformanceRecord(
            UUID id,
            UUID purchaseOrderId,
            UUID supplierId,
            UUID materialId,
            int receiptSequence,
            String recordType,
            UUID supersededReceiptId,
            String adjustmentType,
            String stageDelivery,
            OffsetDateTime actualDeliveryDate,
            BigDecimal receivedQuantity,
            String deliveryStatus,
            Integer delayDays,
            String stageQuality,
            BigDecimal inspectedQuantity,
            BigDecimal acceptedQuantity,
            BigDecimal rejectedQuantity,
            String stageInvoice,
            BigDecimal actualEconomicLandedCost,
            BigDecimal costVarianceAmount,
            BigDecimal deltaReceivedQuantity,
            BigDecimal deltaInspectedQuantity,
            BigDecimal deltaAcceptedQuantity,
            BigDecimal deltaRejectedQuantity,
            BigDecimal deltaEconomicLandedCost,
            String verificationStatus,
            OffsetDateTime createdAt
    ) {}
}
