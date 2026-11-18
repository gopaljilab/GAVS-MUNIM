package org.munim.financial;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class FinancialEvaluatorTest {
    private final FinancialEvaluator calc=new FinancialEvaluator();
    private Map<String,Object> fields(String itc,String freightBasis,String freight){
        Map<String,Object> m=new HashMap<>();
        m.put("unitPrice","100");m.put("quotedQuantity","1000");m.put("unit","kg");
        m.put("priceBasis","EXCLUSIVE");m.put("materialTaxRate","18");m.put("materialItcStatus",itc);
        m.put("freightBasis",freightBasis);m.put("freightAmount",freight);m.put("freightTaxRate","0");
        m.put("freightPriceBasis","EXCLUSIVE");m.put("freightItcStatus","CREDITABLE");
        m.put("moq","0");m.put("purchaseIncrement","1");m.put("wastagePercent","0");
        m.put("quoteQuantityBasis","INPUT_PURCHASED");
        m.put("promisedDeliveryAt","2026-11-20T12:00:00+05:30");
        m.put("validFrom","2026-11-18T12:00:00+05:30");m.put("validUntil","2026-11-30T12:00:00+05:30");return m;
    }
    @Test void nonCreditableMaterialTaxSeparatesEconomicCostAndCash(){
        var r=calc.evaluate(fields("NON_CREDITABLE","SEPARATE","500"),new BigDecimal("100"));
        assertEquals("10000.00",r.get("materialBase"));assertEquals("1800.00",r.get("materialGst"));
        assertEquals("500.00",r.get("freightBase"));assertEquals("12300.00",r.get("economicLandedCost"));
        assertEquals("12300.00",r.get("cashPayable"));
    }
    @Test void creditableMaterialTaxIsCashButNotEconomicCost(){
        var r=calc.evaluate(fields("CREDITABLE","SEPARATE","500"),new BigDecimal("100"));
        assertEquals("10500.00",r.get("economicLandedCost"));assertEquals("12300.00",r.get("cashPayable"));
    }
    @Test void explicitInclusiveUnitPriceExtractsBaseAndTax(){
        var m=fields("NON_CREDITABLE","SEPARATE","0");m.put("priceBasis","INCLUSIVE");
        var r=calc.evaluate(m,new BigDecimal("100"));
        assertEquals("8474.58",r.get("materialBase"));assertEquals("1525.42",r.get("materialGst"));
        assertEquals("10000.00",r.get("economicLandedCost"));
    }
    @Test void freightIncludedWithoutExplicitTaxAllocationCannotBeRanked(){
        var m=fields("CREDITABLE","INCLUDED_IN_UNIT_PRICE","0");
        var e=assertThrows(FinancialEvaluator.IncompleteQuoteException.class,()->calc.evaluate(m,new BigDecimal("100")));
        assertTrue(e.missing().contains("freightTaxAllocation"));
    }
    @Test void wastageAndIncrementRoundPurchaseQuantityUp(){
        var m=fields("CREDITABLE","SEPARATE","0");m.put("wastagePercent","5");
        var r=calc.evaluate(m,new BigDecimal("100"));
        assertEquals("106.0000",r.get("purchaseQuantity"));assertEquals("10600.00",r.get("materialBase"));
        assertEquals("0.74",r.get("roundingExcess"));
    }
    @Test void minimumOrderQuantityAddsVisibleExcess(){
        var m=fields("CREDITABLE","SEPARATE","0");m.put("moq","110");
        var r=calc.evaluate(m,new BigDecimal("100"));
        assertEquals("110.0000",r.get("purchaseQuantity"));assertEquals("10.00",r.get("excessVsUsableDemand"));
        assertEquals("11000.00",r.get("materialBase"));
    }
    @Test void unknownTaxTreatmentBlocksEvaluationInsteadOfBecomingZero(){
        var m=fields("UNKNOWN","SEPARATE","0");
        var e=assertThrows(FinancialEvaluator.IncompleteQuoteException.class,()->calc.evaluate(m,new BigDecimal("100")));
        assertTrue(e.missing().contains("materialItcStatus"));
    }
    @Test void unknownPriceRemainsUnknownAndBlocksEvaluation(){
        var m=fields("CREDITABLE","SEPARATE","0");m.put("unitPrice","UNKNOWN");
        var e=assertThrows(FinancialEvaluator.IncompleteQuoteException.class,()->calc.evaluate(m,new BigDecimal("100")));
        assertTrue(e.missing().contains("unitPrice"));
    }
    @Test void invalidQuantityIncrementIsRejected(){
        var m=fields("CREDITABLE","SEPARATE","0");m.put("purchaseIncrement","0");
        assertThrows(IllegalArgumentException.class,()->calc.evaluate(m,new BigDecimal("100")));
    }
    @Test void insufficientQuotedQuantityIsRejected(){
        var m=fields("CREDITABLE","SEPARATE","0");m.put("quotedQuantity","99");
        assertThrows(FinancialEvaluator.IncompleteQuoteException.class,()->calc.evaluate(m,new BigDecimal("100")));
    }
    @Test void unsupportedUnitConversionBlocksEvaluation(){
        var m=fields("CREDITABLE","SEPARATE","0");m.put("unit","tonne");
        var e=assertThrows(FinancialEvaluator.IncompleteQuoteException.class,()->calc.evaluate(m,new BigDecimal("100"),"kg"));
        assertTrue(e.missing().contains("unitConversion"));
    }
    @Test void quotedQuantityMustCoverWastageAndMoq(){
        var m=fields("CREDITABLE","SEPARATE","0");m.put("moq","110");m.put("quotedQuantity","105");
        assertTrue(calc.missing(m,"kg",new BigDecimal("100")).contains("quantityFeasibility"));
    }
    @Test void repeatedEvaluationIsDeterministic(){
        var m=fields("NON_CREDITABLE","SEPARATE","500");
        assertEquals(calc.evaluate(m,new BigDecimal("100")),calc.evaluate(m,new BigDecimal("100")));
    }
}
