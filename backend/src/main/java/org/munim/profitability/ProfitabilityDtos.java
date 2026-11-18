package org.munim.profitability;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProfitabilityDtos {
    private ProfitabilityDtos() {}

    public enum SourcingBasis {
        AWARDED_QUOTE,
        ESTIMATE
    }

    public enum MarginClassification {
        HEALTHY_MARGIN,
        ACCEPTABLE_MARGIN,
        BELOW_MINIMUM_MARGIN,
        LOSS_MAKING
    }

    public record BomItemCostInput(
            UUID materialId,
            String partName,
            BigDecimal netQuantity,
            String unit,
            BigDecimal scrapPercentage,
            boolean scrapRecoverySupported,
            BigDecimal scrapRecoveryPrice,
            SourcingBasis sourcingBasis,
            UUID awardedQuoteRevisionId,
            BigDecimal unitCost,
            String estimateRationale,
            String itcStatus, // "CREDITABLE", "NON_CREDITABLE"
            BigDecimal gstRate // e.g. 18.00
    ) {}

    public record OperationCostInput(
            String operationName,
            BigDecimal setupHours,
            BigDecimal setupRate,
            BigDecimal cycleHours,
            BigDecimal machineRate,
            BigDecimal labourHours,
            BigDecimal labourRate
    ) {}

    public record CostingRequest(
            UUID enquiryId,
            UUID bomId,
            BigDecimal batchQuantity,
            List<BomItemCostInput> bomItems,
            List<OperationCostInput> operations,
            BigDecimal overheadMultiplier, // e.g. 0.15 for 15%
            BigDecimal packagingPerPart,
            BigDecimal outboundFreightPerPart,
            BigDecimal targetMargin, // e.g. 0.20 for 20%
            BigDecimal minimumMargin, // e.g. 0.10 for 10%
            BigDecimal quotedSellingPrice, // optional, proposed base selling price
            BigDecimal outputGstRate // e.g. 18.00
    ) {}

    public record BomItemCostResult(
            String partName,
            BigDecimal netQuantity,
            BigDecimal grossQuantity,
            BigDecimal scrapQuantity,
            BigDecimal unitCost,
            BigDecimal effectiveMaterialPrice,
            BigDecimal grossMaterialCost,
            BigDecimal scrapCredit,
            BigDecimal netMaterialCost
    ) {}

    public record OperationCostResult(
            String operationName,
            BigDecimal setupCostPerPart,
            BigDecimal machineCostPerPart,
            BigDecimal labourCostPerPart,
            BigDecimal totalConversionCostPerPart
    ) {}

    public record CostingResult(
            BigDecimal batchQuantity,
            List<BomItemCostResult> bomItemCosts,
            List<OperationCostResult> operationCosts,
            BigDecimal materialCostPerPart,
            BigDecimal conversionCostPerPart,
            BigDecimal setupCostPerPart,
            BigDecimal machineCostPerPart,
            BigDecimal labourCostPerPart,
            BigDecimal overheadCostPerPart,
            BigDecimal packagingCostPerPart,
            BigDecimal freightCostPerPart,
            BigDecimal totalCostPerPart,
            BigDecimal totalBatchCost,
            BigDecimal targetMargin,
            BigDecimal minimumMargin,
            BigDecimal targetSellingPrice,
            BigDecimal quotedSellingPrice,
            BigDecimal grossProfitPerPart,
            BigDecimal realizedMargin,
            MarginClassification marginClassification,
            BigDecimal outputGstRate,
            BigDecimal outputGstAmount,
            BigDecimal customerPayable,
            String calculationVersion,
            Map<String, Object> snapshot,
            String snapshotSha256
    ) {}
}
