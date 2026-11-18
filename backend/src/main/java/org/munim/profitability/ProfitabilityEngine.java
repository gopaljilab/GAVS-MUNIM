package org.munim.profitability;

import tools.jackson.databind.ObjectMapper;
import org.munim.profitability.ProfitabilityDtos.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class ProfitabilityEngine {

    public static final String CALCULATION_VERSION = "1.0";
    public static final int SCALE_MONEY = 2;
    public static final int SCALE_QTY = 4;
    public static final int SCALE_MARGIN = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private static final Set<String> SUPPORTED_UNITS = Set.of("kg", "tonne", "sheet", "sqm", "meter");

    private final ObjectMapper mapper;

    public ProfitabilityEngine(ObjectMapper mapper) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
    }

    public ProfitabilityEngine() {
        this(new ObjectMapper());
    }

    public CostingResult calculate(CostingRequest request) {
        validateRequest(request);

        BigDecimal batchQuantity = request.batchQuantity().setScale(SCALE_QTY, ROUNDING);

        // 1. BOM item costing
        List<BomItemCostResult> bomResults = new ArrayList<>();
        BigDecimal totalNetMaterialCost = BigDecimal.ZERO.setScale(SCALE_MONEY, ROUNDING);

        for (BomItemCostInput item : request.bomItems()) {
            BomItemCostResult itemResult = calculateBomItem(item);
            bomResults.add(itemResult);
            totalNetMaterialCost = totalNetMaterialCost.add(itemResult.netMaterialCost());
        }

        // 2. Direct conversion costing (Operations)
        List<OperationCostResult> operationResults = new ArrayList<>();
        BigDecimal totalSetupCostPerPart = BigDecimal.ZERO.setScale(SCALE_MONEY, ROUNDING);
        BigDecimal totalMachineCostPerPart = BigDecimal.ZERO.setScale(SCALE_MONEY, ROUNDING);
        BigDecimal totalLabourCostPerPart = BigDecimal.ZERO.setScale(SCALE_MONEY, ROUNDING);
        BigDecimal totalConversionCostPerPart = BigDecimal.ZERO.setScale(SCALE_MONEY, ROUNDING);

        if (request.operations() != null) {
            for (OperationCostInput op : request.operations()) {
                OperationCostResult opResult = calculateOperation(op, batchQuantity);
                operationResults.add(opResult);
                totalSetupCostPerPart = totalSetupCostPerPart.add(opResult.setupCostPerPart());
                totalMachineCostPerPart = totalMachineCostPerPart.add(opResult.machineCostPerPart());
                totalLabourCostPerPart = totalLabourCostPerPart.add(opResult.labourCostPerPart());
                totalConversionCostPerPart = totalConversionCostPerPart.add(opResult.totalConversionCostPerPart());
            }
        }

        // 3. Factory overhead
        BigDecimal overheadMultiplier = request.overheadMultiplier() != null
                ? request.overheadMultiplier()
                : BigDecimal.ZERO;
        BigDecimal overheadCostPerPart = totalConversionCostPerPart
                .multiply(overheadMultiplier)
                .setScale(SCALE_MONEY, ROUNDING);

        // 4. Logistics (Packaging & Outbound freight)
        BigDecimal packagingCostPerPart = request.packagingPerPart() != null
                ? request.packagingPerPart().setScale(SCALE_MONEY, ROUNDING)
                : BigDecimal.ZERO.setScale(SCALE_MONEY, ROUNDING);

        BigDecimal freightCostPerPart = request.outboundFreightPerPart() != null
                ? request.outboundFreightPerPart().setScale(SCALE_MONEY, ROUNDING)
                : BigDecimal.ZERO.setScale(SCALE_MONEY, ROUNDING);

        // 5. Total unit and batch production cost
        BigDecimal totalCostPerPart = totalNetMaterialCost
                .add(totalConversionCostPerPart)
                .add(overheadCostPerPart)
                .add(packagingCostPerPart)
                .add(freightCostPerPart)
                .setScale(SCALE_MONEY, ROUNDING);

        BigDecimal totalBatchCost = totalCostPerPart
                .multiply(batchQuantity)
                .setScale(SCALE_MONEY, ROUNDING);

        // 6. Target pricing
        BigDecimal targetMargin = request.targetMargin().setScale(4, ROUNDING);
        BigDecimal targetMarginComplement = BigDecimal.ONE.subtract(targetMargin);
        BigDecimal targetSellingPrice = totalCostPerPart
                .divide(targetMarginComplement, SCALE_MONEY, ROUNDING);

        // 7. Quoted pricing and margin classification
        BigDecimal minimumMargin = request.minimumMargin().setScale(4, ROUNDING);
        BigDecimal quotedSellingPrice = null;
        BigDecimal grossProfitPerPart = null;
        BigDecimal realizedMargin = null;
        MarginClassification classification = null;

        BigDecimal outputGstRate = request.outputGstRate() != null
                ? request.outputGstRate().setScale(SCALE_MARGIN, ROUNDING)
                : BigDecimal.valueOf(18.00).setScale(SCALE_MARGIN, ROUNDING);

        BigDecimal outputGstAmount;
        BigDecimal customerPayable;

        BigDecimal targetMarginPercent = targetMargin.multiply(BigDecimal.valueOf(100)).setScale(SCALE_MARGIN, ROUNDING);
        BigDecimal minMarginPercent = minimumMargin.multiply(BigDecimal.valueOf(100)).setScale(SCALE_MARGIN, ROUNDING);

        if (request.quotedSellingPrice() != null) {
            quotedSellingPrice = request.quotedSellingPrice().setScale(SCALE_MONEY, ROUNDING);
            grossProfitPerPart = quotedSellingPrice.subtract(totalCostPerPart).setScale(SCALE_MONEY, ROUNDING);

            // realizedMargin = ((quotedSellingPrice - totalCostPerPart) / quotedSellingPrice) * 100
            realizedMargin = grossProfitPerPart
                    .multiply(BigDecimal.valueOf(100))
                    .divide(quotedSellingPrice, SCALE_MARGIN, ROUNDING);

            if (quotedSellingPrice.compareTo(totalCostPerPart) < 0) {
                classification = MarginClassification.LOSS_MAKING;
            } else if (realizedMargin.compareTo(minMarginPercent) < 0) {
                classification = MarginClassification.BELOW_MINIMUM_MARGIN;
            } else if (realizedMargin.compareTo(targetMarginPercent) < 0) {
                classification = MarginClassification.ACCEPTABLE_MARGIN;
            } else {
                classification = MarginClassification.HEALTHY_MARGIN;
            }

            outputGstAmount = quotedSellingPrice
                    .multiply(outputGstRate)
                    .divide(BigDecimal.valueOf(100), SCALE_MONEY, ROUNDING);
            customerPayable = quotedSellingPrice.add(outputGstAmount).setScale(SCALE_MONEY, ROUNDING);
        } else {
            outputGstAmount = targetSellingPrice
                    .multiply(outputGstRate)
                    .divide(BigDecimal.valueOf(100), SCALE_MONEY, ROUNDING);
            customerPayable = targetSellingPrice.add(outputGstAmount).setScale(SCALE_MONEY, ROUNDING);
        }

        // 8. Canonical snapshot and hash
        Map<String, Object> snapshot = buildSnapshotMap(
                request,
                batchQuantity,
                bomResults,
                operationResults,
                totalNetMaterialCost,
                totalConversionCostPerPart,
                totalSetupCostPerPart,
                totalMachineCostPerPart,
                totalLabourCostPerPart,
                overheadCostPerPart,
                packagingCostPerPart,
                freightCostPerPart,
                totalCostPerPart,
                totalBatchCost,
                targetMargin,
                minimumMargin,
                targetSellingPrice,
                quotedSellingPrice,
                grossProfitPerPart,
                realizedMargin,
                classification,
                outputGstRate,
                outputGstAmount,
                customerPayable
        );

        String snapshotJson = toJson(snapshot);
        String snapshotSha256 = sha256(snapshotJson);

        return new CostingResult(
                batchQuantity,
                bomResults,
                operationResults,
                totalNetMaterialCost,
                totalConversionCostPerPart,
                totalSetupCostPerPart,
                totalMachineCostPerPart,
                totalLabourCostPerPart,
                overheadCostPerPart,
                packagingCostPerPart,
                freightCostPerPart,
                totalCostPerPart,
                totalBatchCost,
                targetMargin,
                minimumMargin,
                targetSellingPrice,
                quotedSellingPrice,
                grossProfitPerPart,
                realizedMargin,
                classification,
                outputGstRate,
                outputGstAmount,
                customerPayable,
                CALCULATION_VERSION,
                snapshot,
                snapshotSha256
        );
    }

    private BomItemCostResult calculateBomItem(BomItemCostInput item) {
        BigDecimal netQty = item.netQuantity().setScale(SCALE_QTY, ROUNDING);
        BigDecimal scrapMultiplier = BigDecimal.ONE.add(
                item.scrapPercentage().divide(BigDecimal.valueOf(100), 8, ROUNDING)
        );
        BigDecimal grossQty = netQty.multiply(scrapMultiplier).setScale(SCALE_QTY, ROUNDING);
        BigDecimal scrapQty = grossQty.subtract(netQty).setScale(SCALE_QTY, ROUNDING);

        BigDecimal inboundBasePrice = item.unitCost().setScale(SCALE_MONEY, ROUNDING);
        BigDecimal effectivePrice;

        if ("CREDITABLE".equalsIgnoreCase(item.itcStatus())) {
            effectivePrice = inboundBasePrice;
        } else {
            // NON_CREDITABLE: add inbound GST to cost basis
            BigDecimal gstFactor = BigDecimal.ONE.add(
                    item.gstRate().divide(BigDecimal.valueOf(100), 8, ROUNDING)
            );
            effectivePrice = inboundBasePrice.multiply(gstFactor).setScale(SCALE_MONEY, ROUNDING);
        }

        BigDecimal grossMaterialCost = grossQty.multiply(effectivePrice).setScale(SCALE_MONEY, ROUNDING);

        BigDecimal scrapCredit = BigDecimal.ZERO.setScale(SCALE_MONEY, ROUNDING);
        if (item.scrapRecoverySupported()) {
            scrapCredit = scrapQty.multiply(item.scrapRecoveryPrice()).setScale(SCALE_MONEY, ROUNDING);
        }

        BigDecimal netMaterialCost = grossMaterialCost.subtract(scrapCredit).setScale(SCALE_MONEY, ROUNDING);

        return new BomItemCostResult(
                item.partName(),
                netQty,
                grossQty,
                scrapQty,
                inboundBasePrice,
                effectivePrice,
                grossMaterialCost,
                scrapCredit,
                netMaterialCost
        );
    }

    private OperationCostResult calculateOperation(OperationCostInput op, BigDecimal batchQuantity) {
        BigDecimal setupHours = op.setupHours() != null ? op.setupHours() : BigDecimal.ZERO;
        BigDecimal setupRate = op.setupRate() != null ? op.setupRate() : BigDecimal.ZERO;
        BigDecimal cycleHours = op.cycleHours() != null ? op.cycleHours() : BigDecimal.ZERO;
        BigDecimal machineRate = op.machineRate() != null ? op.machineRate() : BigDecimal.ZERO;
        BigDecimal labourHours = op.labourHours() != null ? op.labourHours() : BigDecimal.ZERO;
        BigDecimal labourRate = op.labourRate() != null ? op.labourRate() : BigDecimal.ZERO;

        BigDecimal setupCostPerPart = setupHours
                .multiply(setupRate)
                .divide(batchQuantity, SCALE_MONEY, ROUNDING);

        BigDecimal machineCostPerPart = cycleHours
                .multiply(machineRate)
                .setScale(SCALE_MONEY, ROUNDING);

        BigDecimal labourCostPerPart = labourHours
                .multiply(labourRate)
                .setScale(SCALE_MONEY, ROUNDING);

        BigDecimal totalConversionCostPerPart = setupCostPerPart
                .add(machineCostPerPart)
                .add(labourCostPerPart)
                .setScale(SCALE_MONEY, ROUNDING);

        return new OperationCostResult(
                op.operationName(),
                setupCostPerPart,
                machineCostPerPart,
                labourCostPerPart,
                totalConversionCostPerPart
        );
    }

    private void validateRequest(CostingRequest request) {
        if (request == null) {
            throw new ProfitabilityValidationException("REQUEST_NULL", "Costing request cannot be null");
        }

        if (request.batchQuantity() == null || request.batchQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ProfitabilityValidationException("INVALID_BATCH_QUANTITY", "Batch quantity must be greater than zero");
        }

        if (request.bomItems() == null || request.bomItems().isEmpty()) {
            throw new ProfitabilityValidationException("EMPTY_BOM", "BOM must contain at least one item");
        }

        for (BomItemCostInput item : request.bomItems()) {
            validateBomItem(item);
        }

        if (request.operations() != null) {
            for (OperationCostInput op : request.operations()) {
                validateOperation(op);
            }
        }

        if (request.overheadMultiplier() != null && request.overheadMultiplier().compareTo(BigDecimal.ZERO) < 0) {
            throw new ProfitabilityValidationException("INVALID_OVERHEAD_MULTIPLIER", "Overhead multiplier cannot be negative");
        }

        if (request.packagingPerPart() != null && request.packagingPerPart().compareTo(BigDecimal.ZERO) < 0) {
            throw new ProfitabilityValidationException("INVALID_PACKAGING_COST", "Packaging cost cannot be negative");
        }

        if (request.outboundFreightPerPart() != null && request.outboundFreightPerPart().compareTo(BigDecimal.ZERO) < 0) {
            throw new ProfitabilityValidationException("INVALID_FREIGHT_COST", "Outbound freight cost cannot be negative");
        }

        if (request.targetMargin() == null
                || request.targetMargin().compareTo(BigDecimal.ZERO) < 0
                || request.targetMargin().compareTo(BigDecimal.ONE) >= 0) {
            throw new ProfitabilityValidationException("INVALID_MARGIN", "Target margin must be in range [0, 1.0)");
        }

        if (request.minimumMargin() == null
                || request.minimumMargin().compareTo(BigDecimal.ZERO) < 0
                || request.minimumMargin().compareTo(BigDecimal.ONE) >= 0) {
            throw new ProfitabilityValidationException("INVALID_MARGIN", "Minimum margin must be in range [0, 1.0)");
        }

        if (request.minimumMargin().compareTo(request.targetMargin()) > 0) {
            throw new ProfitabilityValidationException("INVALID_MARGIN", "Minimum margin cannot exceed target margin");
        }

        if (request.quotedSellingPrice() != null && request.quotedSellingPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ProfitabilityValidationException("INVALID_SELLING_PRICE", "Quoted selling price must be greater than zero");
        }

        if (request.outputGstRate() != null
                && (request.outputGstRate().compareTo(BigDecimal.ZERO) < 0
                || request.outputGstRate().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new ProfitabilityValidationException("INVALID_GST_RATE", "Output GST rate must be between 0 and 100");
        }
    }

    private void validateBomItem(BomItemCostInput item) {
        if (item == null) {
            throw new ProfitabilityValidationException("NULL_BOM_ITEM", "BOM item cannot be null");
        }

        if (item.partName() == null || item.partName().trim().isEmpty()) {
            throw new ProfitabilityValidationException("INVALID_PART_NAME", "Part name cannot be blank");
        }

        if (item.netQuantity() == null || item.netQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ProfitabilityValidationException("INVALID_NET_QUANTITY", "Net quantity must be greater than zero");
        }

        if (item.unit() == null || !SUPPORTED_UNITS.contains(item.unit().trim().toLowerCase(Locale.ROOT))) {
            throw new ProfitabilityValidationException("INVALID_UNIT",
                    "Unsupported unit: " + item.unit() + ". Supported units are: " + SUPPORTED_UNITS);
        }

        if (item.scrapPercentage() == null
                || item.scrapPercentage().compareTo(BigDecimal.ZERO) < 0
                || item.scrapPercentage().compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new ProfitabilityValidationException("INVALID_SCRAP_PERCENTAGE", "Scrap percentage must be in range [0, 100)");
        }

        if (item.scrapRecoverySupported()) {
            if (item.scrapRecoveryPrice() == null || item.scrapRecoveryPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ProfitabilityValidationException("INVALID_SCRAP_PRICE",
                        "Scrap recovery price must be non-null and >= 0 when scrap recovery is supported");
            }
        }

        if (item.sourcingBasis() == null) {
            throw new ProfitabilityValidationException("INVALID_SOURCING_BASIS", "Sourcing basis is required");
        }

        if (item.sourcingBasis() == SourcingBasis.AWARDED_QUOTE) {
            if (item.awardedQuoteRevisionId() == null) {
                throw new ProfitabilityValidationException("INVALID_SOURCING_BASIS",
                        "Awarded quote revision ID is required for AWARDED_QUOTE sourcing basis");
            }
            if (item.unitCost() == null || item.unitCost().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ProfitabilityValidationException("INVALID_UNIT_COST", "Awarded quote unit cost must be greater than zero");
            }
            if (item.estimateRationale() != null && !item.estimateRationale().trim().isEmpty()) {
                throw new ProfitabilityValidationException("INVALID_SOURCING_BASIS",
                        "Awarded quote cannot have estimate rationale");
            }
        } else if (item.sourcingBasis() == SourcingBasis.ESTIMATE) {
            if (item.awardedQuoteRevisionId() != null) {
                throw new ProfitabilityValidationException("INVALID_SOURCING_BASIS",
                        "Estimate sourcing basis cannot reference an awarded quote revision");
            }
            if (item.unitCost() == null || item.unitCost().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ProfitabilityValidationException("INVALID_UNIT_COST", "Estimated unit cost must be greater than zero");
            }
            if (item.estimateRationale() == null || item.estimateRationale().trim().isEmpty()) {
                throw new ProfitabilityValidationException("MISSING_ESTIMATE_RATIONALE",
                        "Estimate rationale is mandatory when sourcing basis is ESTIMATE");
            }
        }

        if (item.itcStatus() == null
                || (!"CREDITABLE".equalsIgnoreCase(item.itcStatus().trim())
                && !"NON_CREDITABLE".equalsIgnoreCase(item.itcStatus().trim()))) {
            throw new ProfitabilityValidationException("INVALID_ITC_STATUS",
                    "ITC status must be explicitly specified as either CREDITABLE or NON_CREDITABLE");
        }

        if (item.gstRate() == null
                || item.gstRate().compareTo(BigDecimal.ZERO) < 0
                || item.gstRate().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ProfitabilityValidationException("INVALID_GST_RATE", "Material GST rate must be between 0 and 100");
        }
    }

    private void validateOperation(OperationCostInput op) {
        if (op == null) {
            throw new ProfitabilityValidationException("NULL_OPERATION", "Operation cannot be null");
        }

        if (op.operationName() == null || op.operationName().trim().isEmpty()) {
            throw new ProfitabilityValidationException("INVALID_OPERATION_NAME", "Operation name cannot be blank");
        }

        if (op.setupHours() != null && op.setupHours().compareTo(BigDecimal.ZERO) < 0) {
            throw new ProfitabilityValidationException("INVALID_SETUP_HOURS", "Setup hours cannot be negative");
        }

        if (op.setupRate() != null && op.setupRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new ProfitabilityValidationException("INVALID_SETUP_RATE", "Setup rate cannot be negative");
        }

        if (op.cycleHours() != null && op.cycleHours().compareTo(BigDecimal.ZERO) < 0) {
            throw new ProfitabilityValidationException("INVALID_CYCLE_HOURS", "Cycle hours cannot be negative");
        }

        if (op.machineRate() != null && op.machineRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new ProfitabilityValidationException("INVALID_MACHINE_RATE", "Machine rate cannot be negative");
        }

        if (op.labourHours() != null && op.labourHours().compareTo(BigDecimal.ZERO) < 0) {
            throw new ProfitabilityValidationException("INVALID_LABOUR_HOURS", "Labour hours cannot be negative");
        }

        if (op.labourRate() != null && op.labourRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new ProfitabilityValidationException("INVALID_LABOUR_RATE", "Labour rate cannot be negative");
        }
    }

    private Map<String, Object> buildSnapshotMap(
            CostingRequest req,
            BigDecimal batchQuantity,
            List<BomItemCostResult> bomResults,
            List<OperationCostResult> operationResults,
            BigDecimal totalNetMaterialCost,
            BigDecimal totalConversionCostPerPart,
            BigDecimal totalSetupCostPerPart,
            BigDecimal totalMachineCostPerPart,
            BigDecimal totalLabourCostPerPart,
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
            MarginClassification classification,
            BigDecimal outputGstRate,
            BigDecimal outputGstAmount,
            BigDecimal customerPayable
    ) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("calculationVersion", CALCULATION_VERSION);
        map.put("enquiryId", req.enquiryId() != null ? req.enquiryId().toString() : null);
        map.put("bomId", req.bomId() != null ? req.bomId().toString() : null);
        map.put("batchQuantity", batchQuantity.toPlainString());
        map.put("materialCostPerPart", totalNetMaterialCost.toPlainString());
        map.put("conversionCostPerPart", totalConversionCostPerPart.toPlainString());
        map.put("setupCostPerPart", totalSetupCostPerPart.toPlainString());
        map.put("machineCostPerPart", totalMachineCostPerPart.toPlainString());
        map.put("labourCostPerPart", totalLabourCostPerPart.toPlainString());
        map.put("overheadCostPerPart", overheadCostPerPart.toPlainString());
        map.put("packagingCostPerPart", packagingCostPerPart.toPlainString());
        map.put("freightCostPerPart", freightCostPerPart.toPlainString());
        map.put("totalCostPerPart", totalCostPerPart.toPlainString());
        map.put("totalBatchCost", totalBatchCost.toPlainString());
        map.put("targetMargin", targetMargin.toPlainString());
        map.put("minimumMargin", minimumMargin.toPlainString());
        map.put("targetSellingPrice", targetSellingPrice.toPlainString());
        map.put("quotedSellingPrice", quotedSellingPrice != null ? quotedSellingPrice.toPlainString() : null);
        map.put("grossProfitPerPart", grossProfitPerPart != null ? grossProfitPerPart.toPlainString() : null);
        map.put("realizedMargin", realizedMargin != null ? realizedMargin.toPlainString() : null);
        map.put("marginClassification", classification != null ? classification.name() : null);
        map.put("outputGstRate", outputGstRate.toPlainString());
        map.put("outputGstAmount", outputGstAmount.toPlainString());
        map.put("customerPayable", customerPayable.toPlainString());
        map.put("bomItemCosts", bomResults);
        map.put("operationCosts", operationResults);
        return Collections.unmodifiableMap(map);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize costing snapshot", e);
        }
    }

    private String sha256(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate SHA-256 hash", e);
        }
    }
}
