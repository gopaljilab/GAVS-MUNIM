package org.munim.intelligence;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SupplierIntelligenceDtos {
    private SupplierIntelligenceDtos() {}

    public record PurchaseOrderCreate(
            @NotNull UUID rfqId,
            String notes
    ) {}

    public record PurchaseOrderView(
            UUID id,
            String poNumber,
            UUID rfqId,
            UUID supplierId,
            UUID quoteRevisionId,
            UUID proposalId,
            UUID materialId,
            BigDecimal orderedQuantity,
            String unit,
            BigDecimal agreedUnitPrice,
            BigDecimal agreedFreightAmount,
            String freightBasis,
            BigDecimal materialTaxRate,
            String materialItcStatus,
            BigDecimal freightTaxRate,
            String freightItcStatus,
            BigDecimal quotedEconomicLandedCost,
            BigDecimal quotedCashPayable,
            OffsetDateTime promisedDeliveryDate,
            String status,
            BigDecimal unfulfilledQuantity,
            String closureReason,
            String closedBy,
            OffsetDateTime closedAt,
            OffsetDateTime firstCompletedAt,
            String poDeliveryStatus,
            String notes,
            String poSnapshotHash,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            int version
    ) {}

    public record PurchaseOrderDetailView(
            PurchaseOrderView po,
            BigDecimal cumulativeReceivedQuantity,
            BigDecimal cumulativeAcceptedQuantity,
            BigDecimal cumulativeRejectedQuantity,
            BigDecimal effectiveReceivedQuantity,
            List<PurchaseReceiptView> receipts
    ) {}

    public record PurchaseOrderPage(
            List<PurchaseOrderView> items,
            int total
    ) {}

    public record PurchaseOrderCloseInput(
            @NotBlank @Size(min = 10) String reason,
            String notes
    ) {}

    public record PurchaseOrderReopenInput(
            @NotBlank @Size(min = 10) String reason
    ) {}

    public record DeliveryIntakeInput(
            @NotNull OffsetDateTime actualDeliveryDate,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal receivedQuantity,
            @NotBlank String deliveryChallanNo,
            Boolean overDeliveryAuthorized
    ) {}

    public record QualityInspectionInput(
            @NotBlank String status,
            BigDecimal inspectedQuantity,
            BigDecimal acceptedQuantity,
            BigDecimal rejectedQuantity,
            String rejectionReason,
            String inspectorNotes
    ) {}

    public record InvoiceReconciliationInput(
            @NotBlank String invoiceNumber,
            @NotNull OffsetDateTime invoiceDate,
            @NotNull @DecimalMin("0.00") BigDecimal actualMaterialBase,
            @DecimalMin("0.00") BigDecimal actualMaterialGst,
            @DecimalMin("0.00") BigDecimal actualFreightBase,
            @DecimalMin("0.00") BigDecimal actualFreightGst,
            BigDecimal actualOtherExpenses,
            BigDecimal actualOtherExpensesGst,
            BigDecimal otherExpensesTaxRate,
            String otherExpensesItcStatus,
            String otherExpensesDescription
    ) {}

    public record ReceiptVerificationInput(
            String notes
    ) {}

    public record ReceiptAdjustmentInput(
            @NotBlank String adjustmentType,
            @NotBlank @Size(min = 10) String correctionReason,
            String creditNoteNumber,
            BigDecimal deltaReceivedQuantity,
            BigDecimal deltaInspectedQuantity,
            BigDecimal deltaAcceptedQuantity,
            BigDecimal deltaRejectedQuantity,
            BigDecimal deltaMaterialBase,
            BigDecimal deltaMaterialGst,
            BigDecimal deltaFreightBase,
            BigDecimal deltaFreightGst,
            BigDecimal deltaOtherExpenses,
            BigDecimal deltaOtherExpensesGst
    ) {}

    public record PurchaseReceiptView(
            UUID id,
            UUID purchaseOrderId,
            UUID supplierId,
            UUID materialId,
            int receiptSequence,
            String recordType,
            UUID supersededReceiptId,
            String adjustmentType,
            String correctionReason,
            String stageDelivery,
            OffsetDateTime actualDeliveryDate,
            BigDecimal receivedQuantity,
            String deliveryChallanNo,
            String deliveryStatus,
            Integer delayDays,
            boolean isCriticalDelay,
            String stageQuality,
            BigDecimal inspectedQuantity,
            BigDecimal acceptedQuantity,
            BigDecimal rejectedQuantity,
            BigDecimal qualityAcceptanceRate,
            String rejectionReason,
            String inspectorId,
            String inspectorNotes,
            OffsetDateTime inspectedAt,
            String stageInvoice,
            String invoiceNumber,
            OffsetDateTime invoiceDate,
            BigDecimal actualMaterialBase,
            BigDecimal actualMaterialGst,
            BigDecimal actualFreightBase,
            BigDecimal actualFreightGst,
            BigDecimal actualOtherExpenses,
            BigDecimal actualOtherExpensesGst,
            BigDecimal otherExpensesTaxRate,
            String otherExpensesItcStatus,
            String otherExpensesDescription,
            BigDecimal actualEconomicLandedCost,
            BigDecimal actualCashPaid,
            BigDecimal costVarianceAmount,
            BigDecimal costVariancePercentage,
            String currency,
            BigDecimal deltaReceivedQuantity,
            BigDecimal deltaInspectedQuantity,
            BigDecimal deltaAcceptedQuantity,
            BigDecimal deltaRejectedQuantity,
            BigDecimal deltaMaterialBase,
            BigDecimal deltaMaterialGst,
            BigDecimal deltaFreightBase,
            BigDecimal deltaFreightGst,
            BigDecimal deltaOtherExpenses,
            BigDecimal deltaOtherExpensesGst,
            BigDecimal deltaEconomicLandedCost,
            BigDecimal deltaCashPaid,
            String creditNoteNumber,
            String verificationStatus,
            String verifiedBy,
            OffsetDateTime verifiedAt,
            String outcomeSnapshotHash,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}
}
