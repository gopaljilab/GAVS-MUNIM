package org.munim.financial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class FinancialEvaluator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Set<String> REQUIRED = Set.of(
            "unitPrice", "quotedQuantity", "unit", "priceBasis", "materialTaxRate",
            "materialItcStatus", "freightBasis", "freightAmount", "freightTaxRate", "freightPriceBasis",
            "freightItcStatus", "moq", "purchaseIncrement", "wastagePercent",
            "quoteQuantityBasis", "promisedDeliveryAt", "validFrom", "validUntil");

    public Set<String> missing(Map<String,Object> f, String requiredUnit, BigDecimal rfqQty) {
        var missing = new java.util.TreeSet<String>();
        for (String key : REQUIRED) if (f.get(key) == null || f.get(key).toString().isBlank()) missing.add(key);
        for (String key : REQUIRED) if (f.get(key) != null && "UNKNOWN".equalsIgnoreCase(f.get(key).toString())) missing.add(key);
        if (f.get("unit") != null && !requiredUnit.equals(f.get("unit"))) missing.add("unitConversion");
        if (f.get("quotedQuantity") != null && !"UNKNOWN".equalsIgnoreCase(f.get("quotedQuantity").toString()) && decimal(f,"quotedQuantity").compareTo(rfqQty) < 0) missing.add("quantityFeasibility");
        try {
            if (REQUIRED.stream().allMatch(k -> f.get(k) != null && !f.get(k).toString().isBlank() && !"UNKNOWN".equalsIgnoreCase(f.get(k).toString()))) {
                BigDecimal wastage = decimal(f,"wastagePercent").divide(HUNDRED,12,RoundingMode.HALF_UP);
                BigDecimal increment = decimal(f,"purchaseIncrement");
                BigDecimal moq = decimal(f,"moq");
                if (wastage.signum() >= 0 && wastage.compareTo(BigDecimal.ONE) < 0 && increment.signum() > 0 && moq.signum() >= 0) {
                    BigDecimal needed = ceilToIncrement(rfqQty.divide(BigDecimal.ONE.subtract(wastage),12,RoundingMode.HALF_UP),increment)
                            .max(ceilToIncrement(moq,increment));
                    if (decimal(f,"quotedQuantity").compareTo(needed) < 0) missing.add("quantityFeasibility");
                }
            }
        } catch (NumberFormatException ignored) {
            // Invalid numeric input is rejected by evaluation; it is never defaulted to zero.
        }
        for (String key : new String[]{"materialItcStatus","freightItcStatus"}) {
            Object value=f.get(key);
            if (value != null && "UNKNOWN".equals(value.toString())) missing.add(key);
        }
        if (f.get("freightBasis") != null && "UNKNOWN".equals(f.get("freightBasis").toString())) missing.add("freightBasis");
        if (f.get("freightBasis") != null && "INCLUDED_IN_UNIT_PRICE".equals(f.get("freightBasis").toString())) missing.add("freightTaxAllocation");
        if (f.get("priceBasis") != null && !Set.of("EXCLUSIVE","INCLUSIVE").contains(f.get("priceBasis").toString())) missing.add("priceBasis");
        if (f.get("materialItcStatus") != null && !Set.of("CREDITABLE","NON_CREDITABLE").contains(f.get("materialItcStatus").toString())) missing.add("materialItcStatus");
        if (f.get("freightItcStatus") != null && !Set.of("CREDITABLE","NON_CREDITABLE").contains(f.get("freightItcStatus").toString())) missing.add("freightItcStatus");
        if (f.get("freightPriceBasis") != null && !Set.of("EXCLUSIVE","INCLUSIVE").contains(f.get("freightPriceBasis").toString())) missing.add("freightPriceBasis");
        if (f.get("quoteQuantityBasis") != null && !"INPUT_PURCHASED".equals(f.get("quoteQuantityBasis").toString())) missing.add("quoteQuantityBasis");
        return missing;
    }

    public Map<String,Object> evaluate(Map<String,Object> f, BigDecimal requiredQty) {
        return evaluate(f, requiredQty, f.get("unit").toString());
    }

    public Map<String,Object> evaluate(Map<String,Object> f, BigDecimal requiredQty, String requiredUnit) {
        var missing=missing(f, requiredUnit, requiredQty);
        if (!missing.isEmpty()) throw new IncompleteQuoteException(missing);
        BigDecimal unitPrice=decimal(f,"unitPrice");
        if(unitPrice.signum()<=0)throw new IllegalArgumentException("unitPrice must be positive");
        BigDecimal q=requiredQty;
        BigDecimal wastage=decimal(f,"wastagePercent").divide(HUNDRED,12,RoundingMode.HALF_UP);
        if (wastage.signum()<0 || wastage.compareTo(BigDecimal.ONE)>=0) throw new IllegalArgumentException("wastagePercent must be in [0,100)");
        BigDecimal need=requiredQty.divide(BigDecimal.ONE.subtract(wastage),12,RoundingMode.HALF_UP);
        BigDecimal increment=decimal(f,"purchaseIncrement");
        BigDecimal moq=decimal(f,"moq");
        if (increment.signum()<=0 || moq.signum()<0) throw new IllegalArgumentException("Invalid MOQ or purchase increment");
        BigDecimal rounded=ceilToIncrement(need,increment);
        BigDecimal roundedMoq=ceilToIncrement(moq,increment);
        q=rounded.max(roundedMoq);
        BigDecimal quotedQty=decimal(f,"quotedQuantity");
        if (quotedQty.compareTo(q)<0) throw new IllegalArgumentException("quotedQuantity cannot satisfy purchase quantity");

        BigDecimal gross=unitPrice.multiply(q);
        BigDecimal taxRate=decimal(f,"materialTaxRate").divide(HUNDRED,12,RoundingMode.HALF_UP);
        if(taxRate.signum()<0||taxRate.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("materialTaxRate must be between 0 and 100");
        BigDecimal materialBase;
        BigDecimal materialGst;
        if ("EXCLUSIVE".equals(f.get("priceBasis"))) {
            materialBase=money(gross);
            materialGst=money(gross.multiply(taxRate));
        } else {
            materialBase=money(gross.divide(BigDecimal.ONE.add(taxRate),12,RoundingMode.HALF_UP));
            materialGst=money(gross.subtract(materialBase));
        }

        BigDecimal freight=decimal(f,"freightAmount");
        if(freight.signum()<0)throw new IllegalArgumentException("freightAmount cannot be negative");
        String freightBasis=f.get("freightBasis").toString();
        if ("INCLUDED_IN_UNIT_PRICE".equals(freightBasis) && freight.signum()!=0)
            throw new IllegalArgumentException("Included freight must not also have a separate freight amount");
        if (!Set.of("SEPARATE","INCLUDED_IN_UNIT_PRICE").contains(freightBasis))
            throw new IllegalArgumentException("Unsupported freight basis");
        BigDecimal freightBase=money(freight);
        BigDecimal freightTax=money(freight.multiply(decimal(f,"freightTaxRate").divide(HUNDRED,12,RoundingMode.HALF_UP)));
        BigDecimal freightRate=decimal(f,"freightTaxRate").divide(HUNDRED,12,RoundingMode.HALF_UP);
        if(freightRate.signum()<0||freightRate.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("freightTaxRate must be between 0 and 100");
        if ("INCLUSIVE".equals(f.get("freightPriceBasis"))) {
            freightBase=money(freight.divide(BigDecimal.ONE.add(freightRate),12,RoundingMode.HALF_UP));
            freightTax=money(freight.subtract(freightBase));
        } else if (!"EXCLUSIVE".equals(f.get("freightPriceBasis"))) {
            throw new IncompleteQuoteException(Set.of("freightPriceBasis"));
        }
        if ("INCLUDED_IN_UNIT_PRICE".equals(freightBasis)) {
            freightBase=BigDecimal.ZERO.setScale(2); freightTax=BigDecimal.ZERO.setScale(2);
        }
        String mItc=f.get("materialItcStatus").toString();
        String fItc=f.get("freightItcStatus").toString();
        BigDecimal economic=materialBase.add(freightBase);
        if ("NON_CREDITABLE".equals(mItc)) economic=economic.add(materialGst);
        if ("NON_CREDITABLE".equals(fItc)) economic=economic.add(freightTax);
        BigDecimal cash=materialBase.add(materialGst).add(freightBase).add(freightTax);
        economic=money(economic); cash=money(cash);

        Map<String,Object> out=new LinkedHashMap<>();
        out.put("purchaseQuantity",q.setScale(4,RoundingMode.HALF_UP).toPlainString());
        out.put("roundingExcess",money(rounded.subtract(need)).toPlainString());
        out.put("moqExcess",money(q.subtract(rounded)).toPlainString());
        out.put("excessVsUsableDemand",money(q.subtract(requiredQty)).toPlainString());
        out.put("materialBase",materialBase.toPlainString());
        out.put("materialGst",materialGst.toPlainString());
        out.put("freightBase",freightBase.toPlainString());
        out.put("freightGst",freightTax.toPlainString());
        out.put("economicLandedCost",economic.toPlainString());
        out.put("cashPayable",cash.toPlainString());
        out.put("currency","INR");
        return out;
    }
    public static BigDecimal money(BigDecimal v){return v.setScale(2,RoundingMode.HALF_UP);}
    private static BigDecimal decimal(Map<String,Object> f,String key){return new BigDecimal(f.get(key).toString());}
    private static BigDecimal ceilToIncrement(BigDecimal q,BigDecimal i){return q.divide(i,0,RoundingMode.CEILING).multiply(i);}
    public static final class IncompleteQuoteException extends RuntimeException {
        private final Set<String> missing;
        public IncompleteQuoteException(Set<String> missing){super("Quote is incomplete");this.missing=Set.copyOf(missing);}
        public Set<String> missing(){return missing;}
    }
}
