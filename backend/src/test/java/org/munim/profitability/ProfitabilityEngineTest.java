package org.munim.profitability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.munim.profitability.ProfitabilityDtos.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProfitabilityEngineTest {

    private ProfitabilityEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ProfitabilityEngine();
    }

    private BomItemCostInput createStandardBomItem(
            String partName,
            BigDecimal netQty,
            String unit,
            BigDecimal scrapPercent,
            boolean scrapRecovery,
            BigDecimal scrapPrice,
            SourcingBasis basis,
            UUID revisionId,
            BigDecimal unitCost,
            String rationale,
            String itcStatus,
            BigDecimal gstRate
    ) {
        return new BomItemCostInput(
                UUID.randomUUID(),
                partName,
                netQty,
                unit,
                scrapPercent,
                scrapRecovery,
                scrapPrice,
                basis,
                revisionId,
                unitCost,
                rationale,
                itcStatus,
                gstRate
        );
    }

    private OperationCostInput createStandardOperation(
            String name,
            BigDecimal setupHours,
            BigDecimal setupRate,
            BigDecimal cycleHours,
            BigDecimal machineRate,
            BigDecimal labourHours,
            BigDecimal labourRate
    ) {
        return new OperationCostInput(name, setupHours, setupRate, cycleHours, machineRate, labourHours, labourRate);
    }

    // 1. Standard healthy-margin quotation calculation (golden test with known numbers)
    @Test
    @DisplayName("1. Golden test: Standard healthy-margin quotation calculation with known numbers")
    void testStandardHealthyMarginQuotationCalculation() {
        UUID revisionId = UUID.randomUUID();
        BomItemCostInput item = createStandardBomItem(
                "Chassis Base Plate",
                new BigDecimal("2.0000"),
                "kg",
                new BigDecimal("10.00"),
                true,
                new BigDecimal("30.00"),
                SourcingBasis.AWARDED_QUOTE,
                revisionId,
                new BigDecimal("80.00"),
                null,
                "CREDITABLE",
                new BigDecimal("18.00")
        );

        OperationCostInput laser = createStandardOperation(
                "Laser Cutting",
                new BigDecimal("1.00"),
                new BigDecimal("500.00"),
                new BigDecimal("0.05"),
                new BigDecimal("600.00"),
                new BigDecimal("0.05"),
                new BigDecimal("200.00")
        );

        OperationCostInput bending = createStandardOperation(
                "CNC Bending",
                new BigDecimal("0.50"),
                new BigDecimal("400.00"),
                new BigDecimal("0.08"),
                new BigDecimal("500.00"),
                new BigDecimal("0.08"),
                new BigDecimal("250.00")
        );

        CostingRequest request = new CostingRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.0000"),
                List.of(item),
                List.of(laser, bending),
                new BigDecimal("0.15"),
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                new BigDecimal("0.20"),
                new BigDecimal("0.10"),
                new BigDecimal("400.00"),
                new BigDecimal("18.00")
        );

        CostingResult result = engine.calculate(request);

        assertNotNull(result);
        assertEquals(new BigDecimal("100.0000"), result.batchQuantity());

        // Material verification
        assertEquals(1, result.bomItemCosts().size());
        BomItemCostResult bResult = result.bomItemCosts().getFirst();
        assertEquals(new BigDecimal("2.0000"), bResult.netQuantity());
        assertEquals(new BigDecimal("2.2000"), bResult.grossQuantity());
        assertEquals(new BigDecimal("0.2000"), bResult.scrapQuantity());
        assertEquals(new BigDecimal("80.00"), bResult.effectiveMaterialPrice());
        assertEquals(new BigDecimal("176.00"), bResult.grossMaterialCost());
        assertEquals(new BigDecimal("6.00"), bResult.scrapCredit());
        assertEquals(new BigDecimal("170.00"), bResult.netMaterialCost());
        assertEquals(new BigDecimal("170.00"), result.materialCostPerPart());

        // Conversion verification
        assertEquals(new BigDecimal("7.00"), result.setupCostPerPart());
        assertEquals(new BigDecimal("70.00"), result.machineCostPerPart());
        assertEquals(new BigDecimal("30.00"), result.labourCostPerPart());
        assertEquals(new BigDecimal("107.00"), result.conversionCostPerPart());

        // Overhead & Logistics
        assertEquals(new BigDecimal("16.05"), result.overheadCostPerPart());
        assertEquals(new BigDecimal("5.00"), result.packagingCostPerPart());
        assertEquals(new BigDecimal("10.00"), result.freightCostPerPart());

        // Total Cost
        // 170.00 + 107.00 + 16.05 + 5.00 + 10.00 = 308.05
        assertEquals(new BigDecimal("308.05"), result.totalCostPerPart());
        assertEquals(new BigDecimal("30805.00"), result.totalBatchCost());

        // Target Selling Price = 308.05 / 0.80 = 385.0625 -> 385.06
        assertEquals(new BigDecimal("385.06"), result.targetSellingPrice());

        // Quoted Selling Price & Margin
        assertEquals(new BigDecimal("400.00"), result.quotedSellingPrice());
        assertEquals(new BigDecimal("91.95"), result.grossProfitPerPart());
        // Realized Margin = (91.95 / 400.00) * 100 = 22.9875 -> 22.99%
        assertEquals(new BigDecimal("22.99"), result.realizedMargin());
        assertEquals(MarginClassification.HEALTHY_MARGIN, result.marginClassification());

        // Output GST & Customer Payable
        assertEquals(new BigDecimal("72.00"), result.outputGstAmount());
        assertEquals(new BigDecimal("472.00"), result.customerPayable());

        // Integrity
        assertNotNull(result.snapshot());
        assertNotNull(result.snapshotSha256());
        assertEquals(64, result.snapshotSha256().length());
    }

    // 2. Loss-making quotation (quoted price below total cost) -> classified as LOSS_MAKING
    @Test
    @DisplayName("2. Loss-making quotation: Quoted selling price below total production cost")
    void testLossMakingQuotation() {
        BomItemCostInput item = createStandardBomItem(
                "Plate", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("100.00"), null, "CREDITABLE", new BigDecimal("18.00")
        );

        CostingRequest request = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.0000"),
                List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"),
                new BigDecimal("80.00"), // Quoted price below cost of 100.00
                new BigDecimal("18.00")
        );

        CostingResult result = engine.calculate(request);
        assertEquals(new BigDecimal("100.00"), result.totalCostPerPart());
        assertEquals(new BigDecimal("80.00"), result.quotedSellingPrice());
        assertEquals(new BigDecimal("-20.00"), result.grossProfitPerPart());
        assertEquals(new BigDecimal("-25.00"), result.realizedMargin());
        assertEquals(MarginClassification.LOSS_MAKING, result.marginClassification());
    }

    // 3. Below-minimum-margin quotation (quoted price above cost but below minimum threshold)
    @Test
    @DisplayName("3. Below-minimum-margin and Acceptable margin classification tiers")
    void testBelowMinimumAndAcceptableMarginQuotation() {
        BomItemCostInput item = createStandardBomItem(
                "Plate", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("100.00"), null, "CREDITABLE", new BigDecimal("18.00")
        );

        // Cost is 100.00. Target margin is 20% (0.20), minimum margin is 10% (0.10).
        // 3a. Quoted price 105.00 -> Profit = 5.00 -> Margin = (5/105)*100 = 4.76% (< 10%) -> BELOW_MINIMUM_MARGIN
        CostingRequest reqBelowMin = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.0000"),
                List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"),
                new BigDecimal("105.00"),
                new BigDecimal("18.00")
        );
        CostingResult resBelowMin = engine.calculate(reqBelowMin);
        assertEquals(new BigDecimal("4.76"), resBelowMin.realizedMargin());
        assertEquals(MarginClassification.BELOW_MINIMUM_MARGIN, resBelowMin.marginClassification());

        // 3b. Quoted price 115.00 -> Profit = 15.00 -> Margin = (15/115)*100 = 13.04% (>= 10% and < 20%) -> ACCEPTABLE_MARGIN
        CostingRequest reqAcceptable = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.0000"),
                List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"),
                new BigDecimal("115.00"),
                new BigDecimal("18.00")
        );
        CostingResult resAcceptable = engine.calculate(reqAcceptable);
        assertEquals(new BigDecimal("13.04"), resAcceptable.realizedMargin());
        assertEquals(MarginClassification.ACCEPTABLE_MARGIN, resAcceptable.marginClassification());
    }

    // 4. Target-margin pricing calculation (calculating target selling price from desired margin)
    @Test
    @DisplayName("4. Target-margin pricing calculation: P_target = C_total / (1 - m_target)")
    void testTargetMarginPricingCalculation() {
        BomItemCostInput item = createStandardBomItem(
                "Plate", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("100.00"), null, "CREDITABLE", new BigDecimal("18.00")
        );

        // Target margin = 25% (0.25) -> P_target = 100.00 / 0.75 = 133.33
        CostingRequest req25 = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.25"), new BigDecimal("0.10"),
                null, new BigDecimal("18.00")
        );
        CostingResult res25 = engine.calculate(req25);
        assertEquals(new BigDecimal("133.33"), res25.targetSellingPrice());

        // Target margin = 50% (0.50) -> P_target = 100.00 / 0.50 = 200.00
        CostingRequest req50 = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.50"), new BigDecimal("0.10"),
                null, new BigDecimal("18.00")
        );
        CostingResult res50 = engine.calculate(req50);
        assertEquals(new BigDecimal("200.00"), res50.targetSellingPrice());
    }

    // 5. Awarded supplier quote material cost: verify inbound price matches awarded quote
    @Test
    @DisplayName("5. Awarded supplier quote material cost: Inbound price matches awarded quote")
    void testAwardedSupplierQuoteMaterialCost() {
        UUID quoteRevId = UUID.randomUUID();
        BigDecimal inboundRate = new BigDecimal("145.75");
        BomItemCostInput item = createStandardBomItem(
                "Aluminium Sheet 2mm", new BigDecimal("5.0000"), "sheet", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, quoteRevId, inboundRate, null, "CREDITABLE", new BigDecimal("18.00")
        );

        CostingRequest req = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.0000"),
                List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        );

        CostingResult result = engine.calculate(req);
        assertEquals(inboundRate, result.bomItemCosts().getFirst().effectiveMaterialPrice());
        assertEquals(new BigDecimal("728.75"), result.materialCostPerPart());
    }

    // 6. Owner-estimated material cost: verify estimate rationale is captured and tagged
    @Test
    @DisplayName("6. Owner-estimated material cost: Captures estimate rationale")
    void testOwnerEstimatedMaterialCost() {
        String rationale = "Phone inquiry with Jindal dealer on 2026-11-16; expected delivery within 3 days";
        BigDecimal estimateRate = new BigDecimal("82.50");
        BomItemCostInput item = createStandardBomItem(
                "CRCA Coil 1.2mm", new BigDecimal("10.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.ESTIMATE, null, estimateRate, rationale, "CREDITABLE", new BigDecimal("18.00")
        );

        CostingRequest req = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.0000"),
                List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        );

        CostingResult result = engine.calculate(req);
        assertEquals(new BigDecimal("825.00"), result.materialCostPerPart());
    }

    // 7. Missing source cost: rejection when neither awarded quote nor estimate is provided
    @Test
    @DisplayName("7. Rejection of invalid sourcing: missing quote revision, missing rationale, or zero cost")
    void testMissingSourceCostRejection() {
        // 7a. AWARDED_QUOTE without quote revision id
        BomItemCostInput missingRevId = createStandardBomItem(
                "Part A", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, null, new BigDecimal("50.00"), null, "CREDITABLE", new BigDecimal("18.00")
        );
        CostingRequest req1 = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(missingRevId), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        );
        ProfitabilityValidationException ex1 = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(req1));
        assertEquals("INVALID_SOURCING_BASIS", ex1.getErrorCode());

        // 7b. ESTIMATE without rationale
        BomItemCostInput missingRationale = createStandardBomItem(
                "Part B", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.ESTIMATE, null, new BigDecimal("50.00"), "   ", "CREDITABLE", new BigDecimal("18.00")
        );
        CostingRequest req2 = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(missingRationale), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        );
        ProfitabilityValidationException ex2 = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(req2));
        assertEquals("MISSING_ESTIMATE_RATIONALE", ex2.getErrorCode());

        // 7c. ESTIMATE masquerading with awarded revision id
        BomItemCostInput estimateWithRev = createStandardBomItem(
                "Part C", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.ESTIMATE, UUID.randomUUID(), new BigDecimal("50.00"), "Valid rationale", "CREDITABLE", new BigDecimal("18.00")
        );
        CostingRequest req3 = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(estimateWithRev), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        );
        ProfitabilityValidationException ex3 = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(req3));
        assertEquals("INVALID_SOURCING_BASIS", ex3.getErrorCode());

        // 7d. Zero unit cost
        BomItemCostInput zeroCost = createStandardBomItem(
                "Part D", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), BigDecimal.ZERO, null, "CREDITABLE", new BigDecimal("18.00")
        );
        CostingRequest req4 = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(zeroCost), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        );
        ProfitabilityValidationException ex4 = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(req4));
        assertEquals("INVALID_UNIT_COST", ex4.getErrorCode());
    }

    // 8. Scrap percentage calculation: verify gross material increases proportionally
    @Test
    @DisplayName("8. Scrap percentage calculation: Gross material increases proportionally")
    void testScrapPercentageCalculation() {
        BomItemCostInput item = createStandardBomItem(
                "SS304 Sheet", new BigDecimal("10.0000"), "kg", new BigDecimal("25.00"), false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("200.00"), null, "CREDITABLE", new BigDecimal("18.00")
        );

        CostingRequest req = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        );

        CostingResult result = engine.calculate(req);
        BomItemCostResult b = result.bomItemCosts().getFirst();
        assertEquals(new BigDecimal("10.0000"), b.netQuantity());
        assertEquals(new BigDecimal("12.5000"), b.grossQuantity());
        assertEquals(new BigDecimal("2.5000"), b.scrapQuantity());
        assertEquals(new BigDecimal("2500.00"), b.grossMaterialCost());
        assertEquals(new BigDecimal("2500.00"), b.netMaterialCost());
    }

    // 9. Scrap recovery credit: verify net material cost reflects scrap credit when supported
    @Test
    @DisplayName("9. Scrap recovery credit: Net material cost reflects scrap credit when supported")
    void testScrapRecoveryCredit() {
        // Without recovery: net cost = 2500.00
        // With recovery: 2.5000 kg scrap @ 40.00 ₹/kg = 100.00 scrap credit. Net cost = 2400.00
        BomItemCostInput itemWithRecovery = createStandardBomItem(
                "SS304 Sheet", new BigDecimal("10.0000"), "kg", new BigDecimal("25.00"), true, new BigDecimal("40.00"),
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("200.00"), null, "CREDITABLE", new BigDecimal("18.00")
        );

        CostingRequest req = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(itemWithRecovery), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        );

        CostingResult result = engine.calculate(req);
        BomItemCostResult b = result.bomItemCosts().getFirst();
        assertEquals(new BigDecimal("100.00"), b.scrapCredit());
        assertEquals(new BigDecimal("2400.00"), b.netMaterialCost());
        assertEquals(new BigDecimal("2400.00"), result.materialCostPerPart());

        // Validation: scrapRecoverySupported is true but scrapRecoveryPrice is null
        BomItemCostInput invalidRecovery = createStandardBomItem(
                "SS304 Sheet", new BigDecimal("10.0000"), "kg", new BigDecimal("25.00"), true, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("200.00"), null, "CREDITABLE", new BigDecimal("18.00")
        );
        CostingRequest invalidReq = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(invalidRecovery), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        );
        ProfitabilityValidationException ex = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(invalidReq));
        assertEquals("INVALID_SCRAP_PRICE", ex.getErrorCode());
    }

    // 10. Setup cost amortization across batch quantity (test batch sizes: 1, 100, 1000)
    @Test
    @DisplayName("10. Setup cost amortization: Setup cost per part decreases with batch quantity")
    void testSetupCostAmortizationAcrossBatchSizes() {
        BomItemCostInput item = createStandardBomItem(
                "Part", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("50.00"), null, "CREDITABLE", new BigDecimal("18.00")
        );

        OperationCostInput op = createStandardOperation(
                "Punching Setup",
                new BigDecimal("2.00"),
                new BigDecimal("1000.00"), // Total setup pool = 2000.00
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        // Batch 1
        CostingResult r1 = engine.calculate(new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(item), List.of(op), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        ));
        assertEquals(new BigDecimal("2000.00"), r1.setupCostPerPart());

        // Batch 100
        CostingResult r100 = engine.calculate(new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.0000"),
                List.of(item), List.of(op), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        ));
        assertEquals(new BigDecimal("20.00"), r100.setupCostPerPart());

        // Batch 1000
        CostingResult r1000 = engine.calculate(new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000.0000"),
                List.of(item), List.of(op), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        ));
        assertEquals(new BigDecimal("2.00"), r1000.setupCostPerPart());

        assertTrue(r1.setupCostPerPart().compareTo(r100.setupCostPerPart()) > 0);
        assertTrue(r100.setupCostPerPart().compareTo(r1000.setupCostPerPart()) > 0);
    }

    // 11. Machine and direct labour conversion costs
    @Test
    @DisplayName("11. Machine and direct labour conversion costs accurately calculated")
    void testMachineAndDirectLabourConversionCosts() {
        BomItemCostInput item = createStandardBomItem(
                "Part", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("50.00"), null, "CREDITABLE", new BigDecimal("18.00")
        );

        OperationCostInput op1 = createStandardOperation(
                "Welding",
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.50"), new BigDecimal("300.00"), // 150.00 machine
                new BigDecimal("0.50"), new BigDecimal("200.00")  // 100.00 labour
        );

        OperationCostInput op2 = createStandardOperation(
                "Powder Coating",
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.25"), new BigDecimal("400.00"), // 100.00 machine
                new BigDecimal("0.25"), new BigDecimal("160.00")  // 40.00 labour
        );

        CostingResult res = engine.calculate(new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.0000"),
                List.of(item), List.of(op1, op2), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        ));

        assertEquals(new BigDecimal("250.00"), res.machineCostPerPart());
        assertEquals(new BigDecimal("140.00"), res.labourCostPerPart());
        assertEquals(new BigDecimal("390.00"), res.conversionCostPerPart());
    }

    // 12. Factory overhead allocation (applied to conversion cost)
    @Test
    @DisplayName("12. Factory overhead allocation: Multiplier applied to conversion cost")
    void testFactoryOverheadAllocation() {
        BomItemCostInput item = createStandardBomItem(
                "Part", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("50.00"), null, "CREDITABLE", new BigDecimal("18.00")
        );

        OperationCostInput op = createStandardOperation(
                "Milling",
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1.00"), new BigDecimal("200.00"),
                BigDecimal.ZERO, BigDecimal.ZERO
        );

        // Conversion cost = 200.00. Overhead multiplier = 0.20 -> Overhead = 40.00
        CostingResult res = engine.calculate(new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.0000"),
                List.of(item), List.of(op), new BigDecimal("0.20"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        ));

        assertEquals(new BigDecimal("200.00"), res.conversionCostPerPart());
        assertEquals(new BigDecimal("40.00"), res.overheadCostPerPart());
        assertEquals(new BigDecimal("290.00"), res.totalCostPerPart()); // 50 + 200 + 40
    }

    // 13. Packaging and outbound freight additions
    @Test
    @DisplayName("13. Packaging and outbound freight additions")
    void testPackagingAndOutboundFreight() {
        BomItemCostInput item = createStandardBomItem(
                "Part", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("50.00"), null, "CREDITABLE", new BigDecimal("18.00")
        );

        CostingResult res = engine.calculate(new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.0000"),
                List.of(item), List.of(), BigDecimal.ZERO,
                new BigDecimal("12.50"),
                new BigDecimal("35.00"),
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        ));

        assertEquals(new BigDecimal("12.50"), res.packagingCostPerPart());
        assertEquals(new BigDecimal("35.00"), res.freightCostPerPart());
        assertEquals(new BigDecimal("97.50"), res.totalCostPerPart()); // 50.00 + 12.50 + 35.00
    }

    // 14. ITC tax accounting: creditable input GST excluded from cost vs non-creditable included
    @Test
    @DisplayName("14. ITC tax accounting: Creditable GST excluded vs non-creditable included")
    void testItcTaxAccountingCreditableVsNonCreditable() {
        BigDecimal baseCost = new BigDecimal("100.00");
        BigDecimal gstRate = new BigDecimal("18.00");

        // 14a. CREDITABLE: Effective price remains 100.00
        BomItemCostInput creditable = createStandardBomItem(
                "Part", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), baseCost, null, "CREDITABLE", gstRate
        );
        CostingResult resCreditable = engine.calculate(new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(creditable), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        ));
        assertEquals(new BigDecimal("100.00"), resCreditable.materialCostPerPart());

        // 14b. NON_CREDITABLE: Effective price becomes 100.00 * 1.18 = 118.00
        BomItemCostInput nonCreditable = createStandardBomItem(
                "Part", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), baseCost, null, "NON_CREDITABLE", gstRate
        );
        CostingResult resNonCreditable = engine.calculate(new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(nonCreditable), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        ));
        assertEquals(new BigDecimal("118.00"), resNonCreditable.materialCostPerPart());
    }

    // 15. Unknown or missing GST/ITC treatment throws validation exception
    @Test
    @DisplayName("15. Rejection of unknown or invalid ITC status and GST rates")
    void testUnknownOrMissingItcThrowsValidationException() {
        // Missing ITC status
        BomItemCostInput nullItc = createStandardBomItem(
                "Part", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("100.00"), null, null, new BigDecimal("18.00")
        );
        ProfitabilityValidationException ex1 = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(
                new CostingRequest(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                        List.of(nullItc), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00"))
        ));
        assertEquals("INVALID_ITC_STATUS", ex1.getErrorCode());

        // Invalid ITC status
        BomItemCostInput invalidItc = createStandardBomItem(
                "Part", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("100.00"), null, "PARTIAL_CREDIT", new BigDecimal("18.00")
        );
        ProfitabilityValidationException ex2 = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(
                new CostingRequest(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                        List.of(invalidItc), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00"))
        ));
        assertEquals("INVALID_ITC_STATUS", ex2.getErrorCode());

        // Negative GST rate
        BomItemCostInput negGst = createStandardBomItem(
                "Part", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("100.00"), null, "CREDITABLE", new BigDecimal("-5.00")
        );
        ProfitabilityValidationException ex3 = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(
                new CostingRequest(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                        List.of(negGst), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00"))
        ));
        assertEquals("INVALID_GST_RATE", ex3.getErrorCode());
    }

    // 16. Zero and negative selling price rejected
    @Test
    @DisplayName("16. Rejection of zero and negative quoted selling price")
    void testZeroAndNegativeSellingPriceRejected() {
        BomItemCostInput item = createStandardBomItem(
                "Part", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("100.00"), null, "CREDITABLE", new BigDecimal("18.00")
        );

        // Zero price
        CostingRequest reqZero = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), BigDecimal.ZERO, new BigDecimal("18.00")
        );
        ProfitabilityValidationException ex1 = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(reqZero));
        assertEquals("INVALID_SELLING_PRICE", ex1.getErrorCode());

        // Negative price
        CostingRequest reqNeg = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), new BigDecimal("-50.00"), new BigDecimal("18.00")
        );
        ProfitabilityValidationException ex2 = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(reqNeg));
        assertEquals("INVALID_SELLING_PRICE", ex2.getErrorCode());
    }

    // 17. Invalid margin (>= 100% or < 0%) rejected
    @Test
    @DisplayName("17. Rejection of invalid margins (>= 1.0 or < 0)")
    void testInvalidMarginRejected() {
        BomItemCostInput item = createStandardBomItem(
                "Part", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("100.00"), null, "CREDITABLE", new BigDecimal("18.00")
        );

        // targetMargin >= 1.0 (100%)
        CostingRequest req100 = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1.00"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        );
        ProfitabilityValidationException ex1 = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(req100));
        assertEquals("INVALID_MARGIN", ex1.getErrorCode());

        // targetMargin < 0
        CostingRequest reqNeg = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("-0.10"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        );
        ProfitabilityValidationException ex2 = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(reqNeg));
        assertEquals("INVALID_MARGIN", ex2.getErrorCode());

        // minimumMargin > targetMargin
        CostingRequest reqMinExceeds = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.15"), new BigDecimal("0.25"), null, new BigDecimal("18.00")
        );
        ProfitabilityValidationException ex3 = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(reqMinExceeds));
        assertEquals("INVALID_MARGIN", ex3.getErrorCode());
    }

    // 18. Zero and negative batch quantity rejected
    @Test
    @DisplayName("18. Rejection of zero and negative batch quantity")
    void testZeroAndNegativeBatchQuantityRejected() {
        BomItemCostInput item = createStandardBomItem(
                "Part", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("100.00"), null, "CREDITABLE", new BigDecimal("18.00")
        );

        CostingRequest reqZero = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO,
                List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        );
        ProfitabilityValidationException ex1 = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(reqZero));
        assertEquals("INVALID_BATCH_QUANTITY", ex1.getErrorCode());

        CostingRequest reqNeg = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("-5.0000"),
                List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        );
        ProfitabilityValidationException ex2 = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(reqNeg));
        assertEquals("INVALID_BATCH_QUANTITY", ex2.getErrorCode());
    }

    // 19. Rounding behavior with BigDecimal.HALF_UP across multi-item BOMs
    @Test
    @DisplayName("19. Rounding behavior with HALF_UP across multi-item BOMs")
    void testRoundingBehaviorWithHalfUpAcrossMultiItemBom() {
        // Item 1: 0.3333 kg @ 33.33 = 11.108889 -> 11.11
        BomItemCostInput i1 = createStandardBomItem(
                "Item 1", new BigDecimal("0.3333"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("33.33"), null, "CREDITABLE", new BigDecimal("18.00")
        );
        // Item 2: 1.1234 kg @ 10.05 = 11.290170 -> 11.29
        BomItemCostInput i2 = createStandardBomItem(
                "Item 2", new BigDecimal("1.1234"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("10.05"), null, "CREDITABLE", new BigDecimal("18.00")
        );
        // Item 3: 0.5555 kg @ 20.00 = 11.1100 -> 11.11
        BomItemCostInput i3 = createStandardBomItem(
                "Item 3", new BigDecimal("0.5555"), "kg", BigDecimal.ZERO, false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("20.00"), null, "CREDITABLE", new BigDecimal("18.00")
        );

        CostingRequest req = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.0000"),
                List.of(i1, i2, i3), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
        );

        CostingResult res = engine.calculate(req);
        assertEquals(new BigDecimal("11.11"), res.bomItemCosts().get(0).netMaterialCost());
        assertEquals(new BigDecimal("11.29"), res.bomItemCosts().get(1).netMaterialCost());
        assertEquals(new BigDecimal("11.11"), res.bomItemCosts().get(2).netMaterialCost());
        // Total material cost per part: 11.11 + 11.29 + 11.11 = 33.51
        assertEquals(new BigDecimal("33.51"), res.materialCostPerPart());
    }

    // 20. Inconsistent or unsupported units rejected
    @Test
    @DisplayName("20. Rejection of unsupported units (allowed: kg, tonne, sheet, sqm, meter)")
    void testUnsupportedUnitsRejected() {
        for (String invalidUnit : List.of("liters", "pcs", "box", "feet", "inches")) {
            BomItemCostInput item = createStandardBomItem(
                    "Part", new BigDecimal("1.0000"), invalidUnit, BigDecimal.ZERO, false, null,
                    SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("100.00"), null, "CREDITABLE", new BigDecimal("18.00")
            );
            CostingRequest req = new CostingRequest(
                    UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                    List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
            );
            ProfitabilityValidationException ex = assertThrows(ProfitabilityValidationException.class, () -> engine.calculate(req));
            assertEquals("INVALID_UNIT", ex.getErrorCode());
        }

        // Test that all supported units pass
        for (String validUnit : List.of("kg", "tonne", "sheet", "sqm", "meter", "KG", "SHEET")) {
            BomItemCostInput item = createStandardBomItem(
                    "Part", new BigDecimal("1.0000"), validUnit, BigDecimal.ZERO, false, null,
                    SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal("100.00"), null, "CREDITABLE", new BigDecimal("18.00")
            );
            CostingRequest req = new CostingRequest(
                    UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1.0000"),
                    List.of(item), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00")
            );
            assertDoesNotThrow(() -> engine.calculate(req));
        }
    }

    // 21. Parameterized test covering multiple material-operation combinations
    @ParameterizedTest(name = "{index}: material={0}, unit={1}, netQty={2}, scrap%={3}, rate={4}, opName={5}, cycleHrs={6}, mRate={7}")
    @CsvSource({
            "CRCA Steel Sheet, kg, 5.0000, 8.00, 75.00, Laser Cutting, 0.10, 600.00",
            "SS304 Plate, kg, 3.5000, 12.00, 220.00, CNC Bending, 0.05, 500.00",
            "Aluminium 5052, sheet, 2.0000, 5.00, 450.00, Turret Punching, 0.08, 550.00",
            "Galvanized Sheet, sqm, 10.0000, 15.00, 110.00, MIG Welding, 0.15, 400.00"
    })
    @DisplayName("21. Parameterized test: Multi-material, multi-operation matrix")
    void testParameterizedCostingScenarios(
            String matName, String unit, String netQty, String scrapPct, String rate,
            String opName, String cycleHrs, String mRate
    ) {
        BomItemCostInput item = createStandardBomItem(
                matName, new BigDecimal(netQty), unit, new BigDecimal(scrapPct), false, null,
                SourcingBasis.AWARDED_QUOTE, UUID.randomUUID(), new BigDecimal(rate), null, "CREDITABLE", new BigDecimal("18.00")
        );

        OperationCostInput op = createStandardOperation(
                opName,
                new BigDecimal("0.50"), new BigDecimal("400.00"), // 200 setup / 50 batch = 4.00 setup per part
                new BigDecimal(cycleHrs), new BigDecimal(mRate),
                new BigDecimal("0.05"), new BigDecimal("200.00")  // 10.00 labour
        );

        CostingRequest request = new CostingRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("50.0000"),
                List.of(item), List.of(op),
                new BigDecimal("0.10"), new BigDecimal("5.00"), new BigDecimal("8.00"),
                new BigDecimal("0.20"), new BigDecimal("0.10"),
                null, new BigDecimal("18.00")
        );

        CostingResult result = engine.calculate(request);

        assertNotNull(result);
        assertTrue(result.materialCostPerPart().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(result.conversionCostPerPart().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(result.totalCostPerPart().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(result.targetSellingPrice().compareTo(result.totalCostPerPart()) > 0);
        assertNotNull(result.snapshotSha256());
        assertEquals(64, result.snapshotSha256().length());
    }
}
