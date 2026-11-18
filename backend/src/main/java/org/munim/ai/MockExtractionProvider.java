package org.munim.ai;

import org.munim.api.ApiDtos.FieldValue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MockExtractionProvider implements QuoteExtractionProvider {

    private final MunimAiConfig config;

    public MockExtractionProvider(MunimAiConfig config) {
        this.config = config;
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public RawExtraction extract(String rawMessage, Map<String, Object> context) {
        Map<String, FieldValue> candidates = new LinkedHashMap<>();
        List<String> missingFields = new ArrayList<>();
        List<String> ambiguities = new ArrayList<>();

        if (rawMessage == null || rawMessage.isBlank()) {
            return new RawExtraction(
                    candidates,
                    List.of("unitPrice", "quotedQuantity", "unit", "materialTaxRate", "priceBasis", "freightBasis", "validUntil"),
                    List.of("Empty supplier message"),
                    "DETERMINISTIC_MOCK",
                    config.getModel(),
                    config.getPromptVersion(),
                    "{}"
            );
        }

        String lower = rawMessage.toLowerCase();

        // 1. Security Check: Detect prompt injection / untrusted system overrides
        Pattern injectionPattern = Pattern.compile(
                "(?i)(ignore (all )?previous instructions|system override|system:|award supplier|<script|delete from|drop table|grant all|eval\\()"
        );
        Matcher injMatcher = injectionPattern.matcher(rawMessage);
        if (injMatcher.find()) {
            ambiguities.add("Untrusted instruction detected: '" + injMatcher.group() + "'. Untrusted supplier text is treated strictly as inert data without execution.");
        }

        OffsetDateTime referenceTime = context.containsKey("receivedAt") && context.get("receivedAt") instanceof OffsetDateTime odt
                ? odt
                : OffsetDateTime.now();

        // 2. Unit Price extraction
        // Looks for patterns like "₹95", "rs 95", "95/kg", "95 per kg", "rate 95", "inr 95", "@ 95", "rate: 95", "rate -95/kg"
        Pattern pricePattern = Pattern.compile(
                "(?:(?:₹|rs\\.?|inr|rate:?|@)\\s*(-?[0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]+)?|-?[0-9]+(?:\\.[0-9]+)?))" +
                "|(?:(-?[0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]+)?|-?[0-9]+(?:\\.[0-9]+)?)\\s*(?:/|per)\\s*(?:kg|kilo|tonne|ton|sheet|sqm|meter|litre|box))"
        );
        Matcher priceMatcher = pricePattern.matcher(lower);
        if (priceMatcher.find()) {
            String val = priceMatcher.group(1) != null ? priceMatcher.group(1) : priceMatcher.group(2);
            val = val.replace(",", "");
            int start = priceMatcher.start();
            int end = priceMatcher.end();
            String evidence = rawMessage.substring(start, end);
            candidates.put("unitPrice", new FieldValue(new BigDecimal(val).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), 0.95, evidence, "EXTRACTED", "EXTRACTED"));
        } else {
            // Check for negative or malformed number if present
            Pattern invalidNum = Pattern.compile("(?:₹|rs\\.?|rate)\\s*(-[0-9]+)");
            Matcher invM = invalidNum.matcher(lower);
            if (invM.find()) {
                candidates.put("unitPrice", new FieldValue(invM.group(1), 0.90, rawMessage.substring(invM.start(), invM.end()), "EXTRACTED", "EXTRACTED"));
            } else {
                missingFields.add("unitPrice");
                candidates.put("unitPrice", new FieldValue(null, null, null, "UNKNOWN", "MISSING"));
            }
        }

        // 3. Quoted Quantity & Unit extraction
        Pattern qtyPattern = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?)\\s*(kg|kilos?|tonnes?|tons?|sheets?|sqm|meters?|liters?|litres?|boxes?|gallons?)");
        Matcher qtyMatcher = qtyPattern.matcher(lower);
        if (qtyMatcher.find()) {
            String qVal = qtyMatcher.group(1).replace(",", "");
            String uVal = normalizeUnit(qtyMatcher.group(2));
            int start = qtyMatcher.start();
            int end = qtyMatcher.end();
            String evidence = rawMessage.substring(start, end);

            candidates.put("quotedQuantity", new FieldValue(new BigDecimal(qVal).setScale(4, java.math.RoundingMode.HALF_UP).toPlainString(), 0.95, evidence, "EXTRACTED", "EXTRACTED"));
            candidates.put("unit", new FieldValue(uVal, 0.95, qtyMatcher.group(2), "EXTRACTED", "EXTRACTED"));
        } else {
            missingFields.add("quotedQuantity");
            missingFields.add("unit");
            candidates.put("quotedQuantity", new FieldValue(null, null, null, "UNKNOWN", "MISSING"));
            candidates.put("unit", new FieldValue(null, null, null, "UNKNOWN", "MISSING"));
        }

        // 4. Currency extraction
        Pattern curPattern = Pattern.compile("(?:₹|rs\\.?|inr)");
        Matcher curMatcher = curPattern.matcher(lower);
        if (curMatcher.find()) {
            candidates.put("currency", new FieldValue("INR", 0.99, rawMessage.substring(curMatcher.start(), curMatcher.end()), "EXTRACTED", "EXTRACTED"));
        } else {
            candidates.put("currency", new FieldValue("INR", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
        }

        // 5. GST Rate & GST Price Basis
        // GST rate pattern e.g. "18% gst", "gst 18%", "18% tax", "73% gst"
        Pattern gstPattern = Pattern.compile("([0-9]{1,2})%\\s*(?:gst|tax)|(?:gst|tax)\\s*([0-9]{1,2})%");
        Matcher gstMatcher = gstPattern.matcher(lower);
        if (gstMatcher.find()) {
            String rate = gstMatcher.group(1) != null ? gstMatcher.group(1) : gstMatcher.group(2);
            int start = gstMatcher.start();
            int end = gstMatcher.end();
            String evidence = rawMessage.substring(start, end);
            candidates.put("materialTaxRate", new FieldValue(rate, 0.95, evidence, "EXTRACTED", "EXTRACTED"));
        } else {
            missingFields.add("materialTaxRate");
            candidates.put("materialTaxRate", new FieldValue(null, null, null, "UNKNOWN", "MISSING"));
        }

        // Price Basis pattern: exclusive vs inclusive
        Pattern basisPattern = Pattern.compile("(?:plus\\s*(?:[0-9]{1,2}%\\s*)?gst|\\+\\s*(?:[0-9]{1,2}%\\s*)?gst|gst\\s*(?:[0-9]{1,2}%\\s*)?extra|tax\\s*(?:[0-9]{1,2}%\\s*)?extra|exclusive)");
        Pattern inclPattern = Pattern.compile("(?:incl(?:usive)?\\s*(?:[0-9]{1,2}%\\s*)?gst|gst\\s*(?:[0-9]{1,2}%\\s*)?incl(?:uded)?|with\\s*tax|inclusive)");
        Matcher basisMatcher = basisPattern.matcher(lower);
        Matcher inclMatcher = inclPattern.matcher(lower);
        if (basisMatcher.find()) {
            candidates.put("priceBasis", new FieldValue("EXCLUSIVE", 0.95, rawMessage.substring(basisMatcher.start(), basisMatcher.end()), "EXTRACTED", "EXTRACTED"));
        } else if (inclMatcher.find()) {
            candidates.put("priceBasis", new FieldValue("INCLUSIVE", 0.95, rawMessage.substring(inclMatcher.start(), inclMatcher.end()), "EXTRACTED", "EXTRACTED"));
        } else {
            missingFields.add("priceBasis");
            candidates.put("priceBasis", new FieldValue(null, null, null, "UNKNOWN", "MISSING"));
        }

        // Material ITC status default for evaluation (creditable or non-creditable)
        candidates.put("materialItcStatus", new FieldValue("NON_CREDITABLE", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));

        // 6. Freight Basis & Freight Amount
        Pattern frInclPattern = Pattern.compile("(?:freight\\s*(?:is\\s*)?included|free\\s*(?:delivery|freight|shipping)|freight\\s*incl|bhaada\\s*shamil|bhada\\s*shamil)");
        Pattern frPostNumPattern = Pattern.compile("(?:separate\\s+)?(?:freight|transport|shipping|bhada|bhaada)\\s*(?:is\\s*)?(?:extra|separate|alag\\s*se)?\\s*(?:₹|rs\\.?|inr|:)?\\s*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)(?:\\.[0-9]{1,2})?");
        Pattern frPreNumPattern = Pattern.compile("(?:₹|rs\\.?|inr)?\\s*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)(?:\\.[0-9]{1,2})?\\s*(?:₹|rs\\.?|inr)?\\s*(?:bhada|bhaada|freight|transport|shipping)\\s*(?:alag\\s*se|extra|separate)?");
        Pattern frNoNumPattern = Pattern.compile("(?:separate\\s+(?:freight|transport|shipping|bhada|bhaada)|(?:freight|transport|shipping|bhada|bhaada)\\s*(?:is\\s*)?(?:extra|separate|alag\\s*se))");

        Matcher frInclM = frInclPattern.matcher(lower);
        Matcher frPostM = frPostNumPattern.matcher(lower);
        Matcher frPreM = frPreNumPattern.matcher(lower);
        Matcher frNoNumM = frNoNumPattern.matcher(lower);

        if (frInclM.find()) {
            candidates.put("freightBasis", new FieldValue("INCLUDED", 0.95, rawMessage.substring(frInclM.start(), frInclM.end()), "EXTRACTED", "EXTRACTED"));
            candidates.put("freightAmount", new FieldValue("0.00", 0.95, rawMessage.substring(frInclM.start(), frInclM.end()), "EXTRACTED", "EXTRACTED"));
            candidates.put("freightTaxRate", new FieldValue("0", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
            candidates.put("freightPriceBasis", new FieldValue("EXCLUSIVE", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
            candidates.put("freightItcStatus", new FieldValue("CREDITABLE", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
        } else if (frPostM.find()) {
            String frVal = frPostM.group(1).replace(",", "");
            String evidence = rawMessage.substring(frPostM.start(), frPostM.end());
            candidates.put("freightBasis", new FieldValue("SEPARATE", 0.95, evidence, "EXTRACTED", "EXTRACTED"));
            candidates.put("freightAmount", new FieldValue(new BigDecimal(frVal).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), 0.95, evidence, "EXTRACTED", "EXTRACTED"));
            candidates.put("freightTaxRate", new FieldValue("0", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
            candidates.put("freightPriceBasis", new FieldValue("EXCLUSIVE", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
            candidates.put("freightItcStatus", new FieldValue("CREDITABLE", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
        } else if (frPreM.find()) {
            String frVal = frPreM.group(1).replace(",", "");
            String evidence = rawMessage.substring(frPreM.start(), frPreM.end());
            candidates.put("freightBasis", new FieldValue("SEPARATE", 0.95, evidence, "EXTRACTED", "EXTRACTED"));
            candidates.put("freightAmount", new FieldValue(new BigDecimal(frVal).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), 0.95, evidence, "EXTRACTED", "EXTRACTED"));
            candidates.put("freightTaxRate", new FieldValue("0", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
            candidates.put("freightPriceBasis", new FieldValue("EXCLUSIVE", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
            candidates.put("freightItcStatus", new FieldValue("CREDITABLE", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
        } else if (frNoNumM.find()) {
            candidates.put("freightBasis", new FieldValue("SEPARATE", 0.95, rawMessage.substring(frNoNumM.start(), frNoNumM.end()), "EXTRACTED", "EXTRACTED"));
            missingFields.add("freightAmount");
            candidates.put("freightAmount", new FieldValue(null, null, null, "UNKNOWN", "MISSING"));
            candidates.put("freightTaxRate", new FieldValue("0", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
            candidates.put("freightPriceBasis", new FieldValue("EXCLUSIVE", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
            candidates.put("freightItcStatus", new FieldValue("CREDITABLE", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
        } else {
            missingFields.add("freightBasis");
            candidates.put("freightBasis", new FieldValue(null, null, null, "UNKNOWN", "MISSING"));
            candidates.put("freightAmount", new FieldValue(null, null, null, "UNKNOWN", "MISSING"));
        }

        // 7. Delivery Timeline
        Pattern delSoonPattern = Pattern.compile("(deliver(?:y)?\\s*(?:soon|shortly)|jaldi\\s*(?:bhej|de)|dispatch\\s*next\\s*week)");
        Pattern delDaysPattern = Pattern.compile("(?:deliver(?:y)?|dispatch|reach(?:ing)?)\\s*(?:in\\s*)?([0-9]+)\\s*days?|([0-9]+)[-\\s]*day\\s*delivery");
        Pattern delTomPattern = Pattern.compile("(deliver(?:y)?\\s*tomorrow|tomorrow|kal\\s*(?:de|bhej))");

        Matcher delSoonM = delSoonPattern.matcher(lower);
        Matcher delDaysM = delDaysPattern.matcher(lower);
        Matcher delTomM = delTomPattern.matcher(lower);

        if (delSoonM.find()) {
            ambiguities.add("Ambiguous delivery timeline: '" + rawMessage.substring(delSoonM.start(), delSoonM.end()) + "' does not specify exact number of days.");
            candidates.put("promisedDeliveryAt", new FieldValue(null, 0.40, rawMessage.substring(delSoonM.start(), delSoonM.end()), "EXTRACTED", "AMBIGUOUS"));
            missingFields.add("promisedDeliveryAt");
        } else if (delTomM.find()) {
            OffsetDateTime deliveryDate = referenceTime.plusDays(1);
            candidates.put("promisedDeliveryAt", new FieldValue(deliveryDate.toString(), 0.95, rawMessage.substring(delTomM.start(), delTomM.end()), "EXTRACTED", "EXTRACTED"));
        } else if (delDaysM.find()) {
            String daysStr = delDaysM.group(1) != null ? delDaysM.group(1) : delDaysM.group(2);
            int days = Integer.parseInt(daysStr);
            OffsetDateTime deliveryDate = referenceTime.plusDays(days);
            candidates.put("promisedDeliveryAt", new FieldValue(deliveryDate.toString(), 0.95, rawMessage.substring(delDaysM.start(), delDaysM.end()), "EXTRACTED", "EXTRACTED"));
        } else {
            missingFields.add("promisedDeliveryAt");
            candidates.put("promisedDeliveryAt", new FieldValue(null, null, null, "UNKNOWN", "MISSING"));
        }

        // 8. Quote Validity
        Pattern valPattern = Pattern.compile("valid(?:ity)?\\s*(?:for\\s*)?([0-9]+)\\s*days?|([0-9]+)[-\\s]*day\\s*validity|([0-9]+)\\s*din\\s*valid");
        Matcher valMatcher = valPattern.matcher(lower);
        if (valMatcher.find()) {
            String daysStr = valMatcher.group(1) != null ? valMatcher.group(1) : (valMatcher.group(2) != null ? valMatcher.group(2) : valMatcher.group(3));
            int days = Integer.parseInt(daysStr);
            OffsetDateTime validUntil = referenceTime.plusDays(days);
            candidates.put("validFrom", new FieldValue(referenceTime.toString(), 0.95, rawMessage.substring(valMatcher.start(), valMatcher.end()), "EXTRACTED", "EXTRACTED"));
            candidates.put("validUntil", new FieldValue(validUntil.toString(), 0.95, rawMessage.substring(valMatcher.start(), valMatcher.end()), "EXTRACTED", "EXTRACTED"));
        } else {
            missingFields.add("validUntil");
            candidates.put("validFrom", new FieldValue(referenceTime.toString(), 0.80, null, "SYSTEM_DEFAULT", "EXTRACTED"));
            candidates.put("validUntil", new FieldValue(null, null, null, "UNKNOWN", "MISSING"));
        }

        // 9. Payment Terms
        Pattern payPattern = Pattern.compile("([0-9]+)[-\\s]*days?\\s*payment(?:\\s*terms)?|advance|cash\\s*on\\s*delivery|cod");
        Matcher payMatcher = payPattern.matcher(lower);
        if (payMatcher.find()) {
            candidates.put("paymentTerms", new FieldValue(rawMessage.substring(payMatcher.start(), payMatcher.end()), 0.95, rawMessage.substring(payMatcher.start(), payMatcher.end()), "EXTRACTED", "EXTRACTED"));
        }

        // 10. Material Specification / Grade
        Pattern matPattern = Pattern.compile("(ms\\s*sheet(?:\\s*[0-9]+\\s*mm)?|mild\\s*steel|is\\s*2062(?:\\s*grade)?(?:\\s*[a-c])?|cr4|hr|ss\\s*304|aluminium)");
        Matcher matMatcher = matPattern.matcher(lower);
        if (matMatcher.find()) {
            candidates.put("materialSpecification", new FieldValue(rawMessage.substring(matMatcher.start(), matMatcher.end()), 0.90, rawMessage.substring(matMatcher.start(), matMatcher.end()), "EXTRACTED", "EXTRACTED"));
        }

        // Defaults for operational parameters
        candidates.put("moq", new FieldValue("0", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
        candidates.put("purchaseIncrement", new FieldValue("1", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
        candidates.put("wastagePercent", new FieldValue("0", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));
        candidates.put("quoteQuantityBasis", new FieldValue("INPUT_PURCHASED", 0.90, null, "SYSTEM_DEFAULT", "EXTRACTED"));

        return new RawExtraction(
                candidates,
                missingFields,
                ambiguities,
                "DETERMINISTIC_MOCK",
                config.getModel(),
                config.getPromptVersion(),
                "{\"extracted\":" + candidates.size() + "}"
        );
    }

    private String normalizeUnit(String u) {
        String s = u.toLowerCase().trim();
        if (s.startsWith("kg") || s.startsWith("kilo")) return "kg";
        if (s.startsWith("ton")) return "tonne";
        if (s.startsWith("sheet")) return "sheet";
        if (s.startsWith("meter")) return "meter";
        if (s.startsWith("sqm")) return "sqm";
        return s;
    }
}
