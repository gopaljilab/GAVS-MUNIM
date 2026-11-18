package org.munim.profitability;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.munim.profitability.ProfitabilityDtos.OperationCostInput;
import org.munim.profitability.ProfitabilityDtos.SourcingBasis;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CommercialDtos {
    private CommercialDtos() {}

    // 1. Customer Enquiry DTOs
    public record EnquiryCreate(
            @NotBlank(message = "Customer name is required") String customerName,
            String customerReference,
            String contactEmail,
            String contactPhone,
            @NotNull(message = "Required delivery date is required") OffsetDateTime requiredDeliveryDate,
            String notes
    ) {}

    public record EnquiryUpdate(
            String customerReference,
            String contactEmail,
            String contactPhone,
            OffsetDateTime requiredDeliveryDate,
            String notes,
            String status
    ) {}

    public record CustomerEnquiryView(
            UUID id,
            String customerName,
            String customerReference,
            String contactEmail,
            String contactPhone,
            OffsetDateTime requiredDeliveryDate,
            String status,
            String notes,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            int version
    ) {}

    // 2. Bill of Materials (BOM) DTOs
    public record BomCreate(
            Integer bomVersion,
            @NotNull(message = "Batch quantity is required")
            @DecimalMin(value = "0.0001", message = "Batch quantity must be greater than zero")
            BigDecimal batchQuantity
    ) {}

    public record BomItemCreate(
            @NotNull(message = "Material ID is required") UUID materialId,
            @NotBlank(message = "Part name is required") String partName,
            @NotNull(message = "Net quantity is required")
            @DecimalMin(value = "0.0001", message = "Net quantity must be greater than zero")
            BigDecimal netQuantity,
            @NotBlank(message = "Unit is required") String unit,
            BigDecimal scrapPercentage,
            Boolean scrapRecoverySupported,
            BigDecimal scrapRecoveryPrice,
            @NotNull(message = "Sourcing basis is required") SourcingBasis sourcingBasis,
            UUID awardedQuoteRevisionId,
            BigDecimal estimatedUnitCost,
            String estimateRationale
    ) {}

    public record BomItemUpdate(
            String partName,
            BigDecimal netQuantity,
            String unit,
            BigDecimal scrapPercentage,
            Boolean scrapRecoverySupported,
            BigDecimal scrapRecoveryPrice,
            SourcingBasis sourcingBasis,
            UUID awardedQuoteRevisionId,
            BigDecimal estimatedUnitCost,
            String estimateRationale
    ) {}

    public record BomItemView(
            UUID id,
            UUID bomId,
            UUID materialId,
            String partName,
            BigDecimal netQuantity,
            String unit,
            BigDecimal scrapPercentage,
            boolean scrapRecoverySupported,
            BigDecimal scrapRecoveryPrice,
            SourcingBasis sourcingBasis,
            UUID awardedQuoteRevisionId,
            BigDecimal estimatedUnitCost,
            String estimateRationale,
            OffsetDateTime createdAt
    ) {}

    public record BomView(
            UUID id,
            UUID enquiryId,
            int bomVersion,
            BigDecimal batchQuantity,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<BomItemView> items
    ) {}

    // 3. Costing Run DTOs
    public record ItemTaxInput(
            @NotNull UUID bomItemId,
            String itcStatus, // "CREDITABLE", "NON_CREDITABLE"
            BigDecimal gstRate // e.g. 18.00
    ) {}

    public record CostingRunRequest(
            List<OperationCostInput> operations,
            BigDecimal overheadMultiplier,
            BigDecimal packagingPerPart,
            BigDecimal outboundFreightPerPart,
            @NotNull(message = "Target margin is required") BigDecimal targetMargin,
            @NotNull(message = "Minimum margin is required") BigDecimal minimumMargin,
            BigDecimal quotedSellingPrice,
            BigDecimal outputGstRate,
            List<ItemTaxInput> itemTaxInputs
    ) {}

    public record CostingSheetView(
            UUID id,
            UUID enquiryId,
            UUID bomId,
            BigDecimal batchQuantity,
            BigDecimal materialCost,
            BigDecimal conversionCost,
            BigDecimal overheadCost,
            BigDecimal packagingCost,
            BigDecimal freightCost,
            BigDecimal totalCost,
            BigDecimal targetMargin,
            BigDecimal targetPrice,
            BigDecimal quotedPrice,
            BigDecimal realizedMargin,
            String marginClassification,
            String calculationVersion,
            Map<String, Object> calculationSnapshot,
            String snapshotSha256,
            OffsetDateTime createdAt
    ) {}

    // 4. Customer Quotation DTOs
    public record QuotationCreate(
            @NotNull(message = "Costing sheet ID is required") UUID costingSheetId,
            @NotNull(message = "Valid from date is required") OffsetDateTime validFrom,
            @NotNull(message = "Valid until date is required") OffsetDateTime validUntil,
            @NotBlank(message = "Payment terms are required") String paymentTerms,
            BigDecimal sellingPrice, // Optional override: defaults to costingSheet.quotedPrice or targetPrice
            BigDecimal outputGstRate // Optional override: defaults to 18.00
    ) {}

    public record CustomerQuotationView(
            UUID id,
            UUID enquiryId,
            UUID costingSheetId,
            BigDecimal sellingPrice,
            BigDecimal outputGstRate,
            BigDecimal customerPayable,
            OffsetDateTime validFrom,
            OffsetDateTime validUntil,
            String paymentTerms,
            String marginClassification,
            String approvalStatus,
            UUID commercialProposalId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            int version
    ) {}
}
