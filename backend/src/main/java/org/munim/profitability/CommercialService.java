package org.munim.profitability;

import org.munim.profitability.CommercialDtos.*;
import org.munim.profitability.ProfitabilityDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class CommercialService {

    private static final Set<String> VALID_ENQUIRY_STATUSES = Set.of("OPEN", "COSTED", "QUOTED", "WON", "LOST");
    private static final Set<String> SUPPORTED_UNITS = Set.of("kg", "tonne", "sheet", "sqm", "meter");

    private final CommercialRepository repo;
    private final ProfitabilityEngine engine;

    public CommercialService(CommercialRepository repo, ProfitabilityEngine engine) {
        this.repo = repo;
        this.engine = engine;
    }

    // ==========================================
    // 1. Customer Enquiries
    // ==========================================

    @Transactional
    public CustomerEnquiryView createEnquiry(EnquiryCreate in) {
        if (in.customerName() == null || in.customerName().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name cannot be blank");
        }
        if (in.requiredDeliveryDate() == null) {
            throw new IllegalArgumentException("Required delivery date cannot be null");
        }
        if (in.requiredDeliveryDate().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Required delivery date must be in the future");
        }

        UUID id = repo.insertEnquiry(in);
        return getEnquiry(id);
    }

    public CustomerEnquiryView getEnquiry(UUID id) {
        return repo.findEnquiryById(id)
                .orElseThrow(() -> new NoSuchElementException("Customer enquiry not found: " + id));
    }

    public List<CustomerEnquiryView> listEnquiries(String status) {
        return repo.listEnquiries(status);
    }

    @Transactional
    public CustomerEnquiryView updateEnquiry(UUID id, EnquiryUpdate in) {
        CustomerEnquiryView current = getEnquiry(id);

        if (in.status() != null) {
            String newStatus = in.status().trim().toUpperCase(Locale.ROOT);
            if (!VALID_ENQUIRY_STATUSES.contains(newStatus)) {
                throw new IllegalArgumentException("Invalid enquiry status: " + in.status());
            }
            validateLifecycleTransition(current.status(), newStatus);
        }

        if (in.requiredDeliveryDate() != null && in.requiredDeliveryDate().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Required delivery date must be in the future");
        }

        repo.updateEnquiry(id, in);
        return getEnquiry(id);
    }

    private void validateLifecycleTransition(String currentStatus, String targetStatus) {
        if (currentStatus.equals(targetStatus)) {
            return;
        }

        if ("WON".equals(currentStatus) || "LOST".equals(currentStatus)) {
            throw new IllegalStateException("Enquiry is in terminal state '" + currentStatus + "' and cannot change status");
        }

        boolean valid = switch (currentStatus) {
            case "OPEN" -> "COSTED".equals(targetStatus) || "LOST".equals(targetStatus);
            case "COSTED" -> "QUOTED".equals(targetStatus) || "LOST".equals(targetStatus) || "OPEN".equals(targetStatus);
            case "QUOTED" -> "WON".equals(targetStatus) || "LOST".equals(targetStatus);
            default -> false;
        };

        if (!valid) {
            throw new IllegalStateException("Illegal enquiry status transition from " + currentStatus + " to " + targetStatus);
        }
    }

    // ==========================================
    // 2. Bills of Materials (BOMs)
    // ==========================================

    @Transactional
    public BomView createBom(UUID enquiryId, BomCreate in) {
        getEnquiry(enquiryId); // validates enquiry exists

        if (in.batchQuantity() == null || in.batchQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Batch quantity must be greater than zero");
        }
        if (in.batchQuantity().scale() > 4) {
            throw new IllegalArgumentException("Batch quantity supports at most 4 decimal places");
        }

        int version = in.bomVersion() != null && in.bomVersion() > 0
                ? in.bomVersion()
                : repo.getNextBomVersion(enquiryId);

        UUID id = repo.insertBom(enquiryId, version, in.batchQuantity());
        return getBom(id);
    }

    public BomView getBom(UUID id) {
        return repo.findBomById(id)
                .orElseThrow(() -> new NoSuchElementException("BOM not found: " + id));
    }

    public List<BomView> listBomsForEnquiry(UUID enquiryId) {
        getEnquiry(enquiryId);
        return repo.listBomsByEnquiryId(enquiryId);
    }

    // ==========================================
    // 3. BOM Items
    // ==========================================

    @Transactional
    public BomItemView addBomItem(UUID bomId, BomItemCreate in) {
        getBom(bomId); // validates BOM exists
        validateBomItemCreate(in);

        UUID itemId = repo.insertBomItem(bomId, in);
        return getBomItem(itemId);
    }

    public BomItemView getBomItem(UUID id) {
        return repo.findBomItemById(id)
                .orElseThrow(() -> new NoSuchElementException("BOM item not found: " + id));
    }

    @Transactional
    public BomItemView updateBomItem(UUID bomId, UUID itemId, BomItemUpdate in) {
        BomItemView existing = getBomItem(itemId);
        if (!existing.bomId().equals(bomId)) {
            throw new IllegalArgumentException("BOM item does not belong to the specified BOM");
        }

        if (in.netQuantity() != null && in.netQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Net quantity must be greater than zero");
        }
        if (in.unit() != null && !SUPPORTED_UNITS.contains(in.unit().trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported unit: " + in.unit());
        }
        if (in.scrapPercentage() != null && (in.scrapPercentage().compareTo(BigDecimal.ZERO) < 0 || in.scrapPercentage().compareTo(BigDecimal.valueOf(100)) >= 0)) {
            throw new IllegalArgumentException("Scrap percentage must be in [0, 100)");
        }
        if (Boolean.TRUE.equals(in.scrapRecoverySupported()) && in.scrapRecoveryPrice() != null && in.scrapRecoveryPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Scrap recovery price cannot be negative");
        }

        SourcingBasis basis = in.sourcingBasis() != null ? in.sourcingBasis() : existing.sourcingBasis();
        if (basis == SourcingBasis.AWARDED_QUOTE) {
            UUID revId = in.awardedQuoteRevisionId() != null ? in.awardedQuoteRevisionId() : existing.awardedQuoteRevisionId();
            if (revId == null) {
                throw new IllegalArgumentException("Awarded quote revision ID is required for AWARDED_QUOTE sourcing");
            }
            if (!repo.isAwardedQuoteRevisionEligible(revId)) {
                throw new ProfitabilityValidationException("INVALID_AWARDED_QUOTE_REFERENCE",
                        "Referenced quote revision is not an awarded or eligible supplier quote: " + revId);
            }
        } else if (basis == SourcingBasis.ESTIMATE) {
            BigDecimal estCost = in.estimatedUnitCost() != null ? in.estimatedUnitCost() : existing.estimatedUnitCost();
            String estRationale = in.estimateRationale() != null ? in.estimateRationale() : existing.estimateRationale();
            if (estCost == null || estCost.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Estimated unit cost must be greater than zero");
            }
            if (estRationale == null || estRationale.trim().isEmpty()) {
                throw new IllegalArgumentException("Estimate rationale is mandatory for estimated sourcing");
            }
        }

        repo.updateBomItem(itemId, in);
        return getBomItem(itemId);
    }

    @Transactional
    public void deleteBomItem(UUID bomId, UUID itemId) {
        BomItemView existing = getBomItem(itemId);
        if (!existing.bomId().equals(bomId)) {
            throw new IllegalArgumentException("BOM item does not belong to the specified BOM");
        }
        repo.deleteBomItem(itemId);
    }

    private void validateBomItemCreate(BomItemCreate in) {
        if (in.materialId() == null) {
            throw new IllegalArgumentException("Material ID is required");
        }
        if (in.partName() == null || in.partName().trim().isEmpty()) {
            throw new IllegalArgumentException("Part name cannot be blank");
        }
        if (in.netQuantity() == null || in.netQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Net quantity must be greater than zero");
        }
        if (in.unit() == null || !SUPPORTED_UNITS.contains(in.unit().trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported unit: " + in.unit() + ". Supported: " + SUPPORTED_UNITS);
        }
        if (in.scrapPercentage() != null && (in.scrapPercentage().compareTo(BigDecimal.ZERO) < 0 || in.scrapPercentage().compareTo(BigDecimal.valueOf(100)) >= 0)) {
            throw new IllegalArgumentException("Scrap percentage must be in [0, 100)");
        }
        if (Boolean.TRUE.equals(in.scrapRecoverySupported())) {
            if (in.scrapRecoveryPrice() == null || in.scrapRecoveryPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Scrap recovery price must be non-null and >= 0 when scrap recovery is supported");
            }
        } else {
            if (in.scrapRecoveryPrice() != null) {
                throw new IllegalArgumentException("Scrap recovery price must be null when scrap recovery is not supported");
            }
        }

        if (in.sourcingBasis() == null) {
            throw new IllegalArgumentException("Sourcing basis is required");
        }

        if (in.sourcingBasis() == SourcingBasis.AWARDED_QUOTE) {
            if (in.awardedQuoteRevisionId() == null) {
                throw new IllegalArgumentException("Awarded quote revision ID is required for AWARDED_QUOTE sourcing");
            }
            if (in.estimatedUnitCost() != null || (in.estimateRationale() != null && !in.estimateRationale().trim().isEmpty())) {
                throw new IllegalArgumentException("Awarded quote cannot have estimated unit cost or rationale");
            }
            if (!repo.isAwardedQuoteRevisionEligible(in.awardedQuoteRevisionId())) {
                throw new ProfitabilityValidationException("INVALID_AWARDED_QUOTE_REFERENCE",
                        "Referenced quote revision is not an awarded or eligible supplier quote: " + in.awardedQuoteRevisionId());
            }
        } else if (in.sourcingBasis() == SourcingBasis.ESTIMATE) {
            if (in.awardedQuoteRevisionId() != null) {
                throw new IllegalArgumentException("Estimated sourcing cannot reference an awarded quote revision");
            }
            if (in.estimatedUnitCost() == null || in.estimatedUnitCost().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Estimated unit cost must be greater than zero");
            }
            if (in.estimateRationale() == null || in.estimateRationale().trim().isEmpty()) {
                throw new IllegalArgumentException("Estimate rationale is mandatory for estimated sourcing");
            }
        }
    }

    // ==========================================
    // 4. Costing Workflow
    // ==========================================

    @Transactional
    public CostingSheetView executeCosting(UUID bomId, CostingRunRequest in) {
        BomView bom = getBom(bomId);
        if (bom.items() == null || bom.items().isEmpty()) {
            throw new ProfitabilityValidationException("EMPTY_BOM", "BOM must contain at least one item before costing");
        }

        // Build item tax/cost lookup map from request
        Map<UUID, ItemTaxInput> taxLookup = new HashMap<>();
        if (in.itemTaxInputs() != null) {
            for (ItemTaxInput t : in.itemTaxInputs()) {
                if (t.bomItemId() != null) {
                    taxLookup.put(t.bomItemId(), t);
                }
            }
        }

        List<BomItemCostInput> bomItemInputs = new ArrayList<>();
        for (BomItemView item : bom.items()) {
            ItemTaxInput taxInput = taxLookup.get(item.id());

            BigDecimal unitCost = null;
            String itcStatus = null;
            BigDecimal gstRate = null;

            if (item.sourcingBasis() == SourcingBasis.AWARDED_QUOTE) {
                Map<String, Object> quoteFields = repo.getSupplierQuoteRevisionFields(item.awardedQuoteRevisionId());
                if (quoteFields.containsKey("unitPrice")) {
                    unitCost = new BigDecimal(quoteFields.get("unitPrice").toString());
                }
                if (quoteFields.containsKey("materialTaxRate")) {
                    gstRate = new BigDecimal(quoteFields.get("materialTaxRate").toString());
                }
                if (quoteFields.containsKey("materialItcStatus")) {
                    itcStatus = quoteFields.get("materialItcStatus").toString();
                }

                // Allow caller to explicitly supply/override if missing in fields
                if (taxInput != null) {
                    if (taxInput.itcStatus() != null) itcStatus = taxInput.itcStatus();
                    if (taxInput.gstRate() != null) gstRate = taxInput.gstRate();
                }

                if (unitCost == null || unitCost.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ProfitabilityValidationException("MISSING_COSTING_INPUT",
                            "Missing unit price for awarded quote revision: " + item.awardedQuoteRevisionId());
                }
                if (itcStatus == null || itcStatus.trim().isEmpty()) {
                    throw new ProfitabilityValidationException("MISSING_COSTING_INPUT",
                            "Missing ITC status for awarded quote revision: " + item.awardedQuoteRevisionId());
                }
                if (gstRate == null) {
                    throw new ProfitabilityValidationException("MISSING_COSTING_INPUT",
                            "Missing GST rate for awarded quote revision: " + item.awardedQuoteRevisionId());
                }
            } else {
                // ESTIMATE
                unitCost = item.estimatedUnitCost();
                if (taxInput != null) {
                    itcStatus = taxInput.itcStatus();
                    gstRate = taxInput.gstRate();
                }

                if (itcStatus == null || itcStatus.trim().isEmpty()) {
                    throw new ProfitabilityValidationException("MISSING_COSTING_INPUT",
                            "ITC status is required for estimate BOM item: " + item.partName());
                }
                if (gstRate == null) {
                    throw new ProfitabilityValidationException("MISSING_COSTING_INPUT",
                            "GST rate is required for estimate BOM item: " + item.partName());
                }
            }

            bomItemInputs.add(new BomItemCostInput(
                    item.materialId(),
                    item.partName(),
                    item.netQuantity(),
                    item.unit(),
                    item.scrapPercentage(),
                    item.scrapRecoverySupported(),
                    item.scrapRecoveryPrice(),
                    item.sourcingBasis(),
                    item.awardedQuoteRevisionId(),
                    unitCost,
                    item.estimateRationale(),
                    itcStatus,
                    gstRate
            ));
        }

        CostingRequest engineRequest = new CostingRequest(
                bom.enquiryId(),
                bom.id(),
                bom.batchQuantity(),
                bomItemInputs,
                in.operations() != null ? in.operations() : List.of(),
                in.overheadMultiplier() != null ? in.overheadMultiplier() : BigDecimal.ZERO,
                in.packagingPerPart() != null ? in.packagingPerPart() : BigDecimal.ZERO,
                in.outboundFreightPerPart() != null ? in.outboundFreightPerPart() : BigDecimal.ZERO,
                in.targetMargin(),
                in.minimumMargin(),
                in.quotedSellingPrice(),
                in.outputGstRate() != null ? in.outputGstRate() : new BigDecimal("18.00")
        );

        // Execute frozen deterministic profitability engine
        CostingResult engineResult = engine.calculate(engineRequest);

        // Persist immutable costing sheet
        UUID costingSheetId = repo.insertCostingSheet(
                bom.enquiryId(),
                bom.id(),
                engineResult.batchQuantity(),
                engineResult.materialCostPerPart(),
                engineResult.conversionCostPerPart(),
                engineResult.overheadCostPerPart(),
                engineResult.packagingCostPerPart(),
                engineResult.freightCostPerPart(),
                engineResult.totalCostPerPart(),
                engineResult.targetMargin().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP),
                engineResult.targetSellingPrice(),
                engineResult.quotedSellingPrice(),
                engineResult.realizedMargin(),
                engineResult.marginClassification() != null ? engineResult.marginClassification().name() : null,
                engineResult.calculationVersion(),
                engineResult.snapshot(),
                engineResult.snapshotSha256()
        );

        // If enquiry is OPEN, advance to COSTED
        CustomerEnquiryView enquiry = getEnquiry(bom.enquiryId());
        if ("OPEN".equals(enquiry.status())) {
            repo.updateEnquiryStatus(enquiry.id(), "COSTED");
        }

        return getCostingSheet(costingSheetId);
    }

    public CostingSheetView getCostingSheet(UUID id) {
        return repo.findCostingSheetById(id)
                .orElseThrow(() -> new NoSuchElementException("Costing sheet not found: " + id));
    }

    public List<CostingSheetView> listCostingSheetsForBom(UUID bomId) {
        getBom(bomId);
        return repo.listCostingSheetsByBomId(bomId);
    }

    public CostingResult simulateCosting(UUID bomId, BigDecimal scrapPercentageOverride, BigDecimal targetMarginPctOverride) {
        BomView bom = getBom(bomId);
        if (bom.items() == null || bom.items().isEmpty()) {
            throw new ProfitabilityValidationException("EMPTY_BOM", "BOM must contain at least one item before costing");
        }

        List<BomItemCostInput> bomItemInputs = new ArrayList<>();
        for (BomItemView item : bom.items()) {
            BigDecimal unitCost = null;
            String itcStatus = "CREDITABLE";
            BigDecimal gstRate = new BigDecimal("18.00");

            if (item.sourcingBasis() == SourcingBasis.AWARDED_QUOTE && item.awardedQuoteRevisionId() != null) {
                Map<String, Object> quoteFields = repo.getSupplierQuoteRevisionFields(item.awardedQuoteRevisionId());
                if (quoteFields != null) {
                    if (quoteFields.containsKey("unitPrice")) unitCost = new BigDecimal(quoteFields.get("unitPrice").toString());
                    if (quoteFields.containsKey("materialTaxRate")) gstRate = new BigDecimal(quoteFields.get("materialTaxRate").toString());
                    if (quoteFields.containsKey("materialItcStatus")) itcStatus = quoteFields.get("materialItcStatus").toString();
                }
            } else {
                unitCost = item.estimatedUnitCost();
            }
            if (unitCost == null) unitCost = new BigDecimal("95.00");

            BigDecimal scrapPct = scrapPercentageOverride != null ? scrapPercentageOverride : item.scrapPercentage();

            bomItemInputs.add(new BomItemCostInput(
                    item.materialId(),
                    item.partName(),
                    item.netQuantity(),
                    item.unit(),
                    scrapPct,
                    item.scrapRecoverySupported(),
                    item.scrapRecoveryPrice() != null ? item.scrapRecoveryPrice() : new BigDecimal("25.00"),
                    item.sourcingBasis(),
                    item.awardedQuoteRevisionId(),
                    unitCost,
                    item.estimateRationale(),
                    itcStatus,
                    gstRate
            ));
        }

        BigDecimal targetMargin = targetMarginPctOverride != null
                ? targetMarginPctOverride.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                : new BigDecimal("0.2000");

        CostingRequest engineRequest = new CostingRequest(
                bom.enquiryId(),
                bom.id(),
                bom.batchQuantity(),
                bomItemInputs,
                List.of(
                        new OperationCostInput("Laser Cutting", new BigDecimal("0.50"), new BigDecimal("120.00"), new BigDecimal("0.15"), new BigDecimal("350.00"), new BigDecimal("0.15"), new BigDecimal("150.00")),
                        new OperationCostInput("CNC Bending", new BigDecimal("0.25"), new BigDecimal("100.00"), new BigDecimal("0.10"), new BigDecimal("250.00"), new BigDecimal("0.10"), new BigDecimal("150.00"))
                ),
                new BigDecimal("0.2000"),
                new BigDecimal("15.00"),
                new BigDecimal("25.00"),
                targetMargin,
                new BigDecimal("0.1000"),
                null,
                new BigDecimal("18.00")
        );

        return engine.calculate(engineRequest);
    }

    // ==========================================
    // 5. Customer Quotation Workflow
    // ==========================================

    @Transactional
    public CustomerQuotationView createQuotation(QuotationCreate in) {
        CostingSheetView costing = getCostingSheet(in.costingSheetId());
        CustomerEnquiryView enquiry = getEnquiry(costing.enquiryId());

        if (in.validFrom() == null || in.validUntil() == null) {
            throw new IllegalArgumentException("Quotation validFrom and validUntil are required");
        }
        if (!in.validUntil().isAfter(in.validFrom())) {
            throw new IllegalArgumentException("Quotation validUntil must be after validFrom");
        }
        if (in.paymentTerms() == null || in.paymentTerms().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment terms are required");
        }

        BigDecimal sellingPrice = in.sellingPrice() != null
                ? in.sellingPrice()
                : (costing.quotedPrice() != null ? costing.quotedPrice() : costing.targetPrice());

        if (sellingPrice == null || sellingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Selling price must be greater than zero");
        }

        BigDecimal outputGstRate = in.outputGstRate() != null ? in.outputGstRate() : new BigDecimal("18.00");
        if (outputGstRate.compareTo(BigDecimal.ZERO) < 0 || outputGstRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Output GST rate must be between 0 and 100");
        }

        BigDecimal outputGst = sellingPrice.multiply(outputGstRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal customerPayable = sellingPrice.add(outputGst).setScale(2, RoundingMode.HALF_UP);

        // Realized margin on selling price
        BigDecimal totalCost = costing.totalCost();
        BigDecimal grossProfit = sellingPrice.subtract(totalCost);
        BigDecimal realizedMargin = grossProfit.multiply(BigDecimal.valueOf(100)).divide(sellingPrice, 2, RoundingMode.HALF_UP);

        // Determine margin tier
        String marginClassification;
        if (sellingPrice.compareTo(totalCost) < 0) {
            marginClassification = MarginClassification.LOSS_MAKING.name();
        } else if (costing.marginClassification() != null && costing.quotedPrice() != null && sellingPrice.compareTo(costing.quotedPrice()) == 0) {
            marginClassification = costing.marginClassification();
        } else {
            BigDecimal targetMarginPct = costing.targetMargin(); // e.g. 20.00
            BigDecimal minMarginPct = targetMarginPct != null ? targetMarginPct.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP) : new BigDecimal("10.00");
            if (realizedMargin.compareTo(minMarginPct) < 0) {
                marginClassification = MarginClassification.BELOW_MINIMUM_MARGIN.name();
            } else if (realizedMargin.compareTo(targetMarginPct) < 0) {
                marginClassification = MarginClassification.ACCEPTABLE_MARGIN.name();
            } else {
                marginClassification = MarginClassification.HEALTHY_MARGIN.name();
            }
        }

        UUID quotationId = repo.insertCustomerQuotation(
                costing.enquiryId(),
                costing.id(),
                sellingPrice.setScale(2, RoundingMode.HALF_UP),
                outputGstRate.setScale(2, RoundingMode.HALF_UP),
                customerPayable,
                in.validFrom(),
                in.validUntil(),
                in.paymentTerms(),
                marginClassification
        );

        // If enquiry is COSTED or OPEN, advance to QUOTED
        if ("OPEN".equals(enquiry.status()) || "COSTED".equals(enquiry.status())) {
            repo.updateEnquiryStatus(enquiry.id(), "QUOTED");
        }

        return getCustomerQuotation(quotationId);
    }

    public CustomerQuotationView getCustomerQuotation(UUID id) {
        return repo.findCustomerQuotationById(id)
                .orElseThrow(() -> new NoSuchElementException("Customer quotation not found: " + id));
    }

    public List<CustomerQuotationView> listQuotationsForEnquiry(UUID enquiryId) {
        getEnquiry(enquiryId);
        return repo.listCustomerQuotationsByEnquiryId(enquiryId);
    }

    public List<CustomerQuotationView> listAllQuotations() {
        return repo.listAllCustomerQuotations();
    }
}
