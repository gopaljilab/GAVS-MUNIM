package org.munim.ai;

import org.munim.api.ApiDtos.FieldValue;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class ExtractionValidator {

    private static final Set<String> SUPPORTED_UNITS = Set.of("kg", "tonne", "sheet", "sqm", "meter");
    private static final Set<String> SUPPORTED_GST_RATES = Set.of("0", "5", "12", "18", "28");

    public record ValidationReport(
            Map<String, FieldValue> validatedCandidates,
            List<String> missingFields,
            List<String> ambiguities,
            String validationStatus,
            List<String> validationErrors
    ) {}

    public ValidationReport validate(String rawMessage, QuoteExtractionProvider.RawExtraction raw) {
        Map<String, FieldValue> candidates = new LinkedHashMap<>(raw.candidates());
        Set<String> missing = new LinkedHashSet<>(raw.missingFields());
        List<String> ambiguities = new ArrayList<>(raw.ambiguities());
        List<String> errors = new ArrayList<>();

        String rawLower = rawMessage != null ? rawMessage.toLowerCase() : "";

        // 1. Evidence snippet verification
        for (var entry : new ArrayList<>(candidates.entrySet())) {
            String k = entry.getKey();
            FieldValue f = entry.getValue();
            if ("EXTRACTED".equals(f.source()) && f.evidence() != null && !f.evidence().isBlank() && !"MISSING".equals(f.status())) {
                String evLower = f.evidence().toLowerCase().trim();
                // Check if evidence snippet occurs in the original raw message
                if (!rawLower.contains(evLower)) {
                    errors.add("Evidence snippet not found in original message for field '" + k + "': '" + f.evidence() + "'");
                    // Downgrade status to LOW_CONFIDENCE and lower confidence score
                    candidates.put(k, new FieldValue(f.value(), 0.30, f.evidence(), f.source(), "LOW_CONFIDENCE"));
                }
            }
        }

        // 2. Unit check
        FieldValue unitField = candidates.get("unit");
        if (unitField != null && unitField.value() != null) {
            String u = unitField.value().toString().toLowerCase().trim();
            if (!SUPPORTED_UNITS.contains(u)) {
                errors.add("Unsupported unit: '" + u + "'. MUNIM sheet-metal evaluation only supports: " + SUPPORTED_UNITS);
                candidates.put("unit", new FieldValue(unitField.value(), 0.20, unitField.evidence(), unitField.source(), "LOW_CONFIDENCE"));
            }
        } else {
            missing.add("unit");
        }

        // 3. Quoted Quantity check
        FieldValue qtyField = candidates.get("quotedQuantity");
        if (qtyField != null && qtyField.value() != null) {
            try {
                BigDecimal qty = new BigDecimal(qtyField.value().toString());
                if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add("Invalid quantity: " + qtyField.value() + " (must be greater than 0)");
                }
            } catch (Exception e) {
                errors.add("Invalid numerical quantity: " + qtyField.value());
            }
        } else {
            missing.add("quotedQuantity");
        }

        // 4. Unit Price check
        FieldValue priceField = candidates.get("unitPrice");
        if (priceField != null && priceField.value() != null) {
            try {
                BigDecimal p = new BigDecimal(priceField.value().toString());
                if (p.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add("Invalid price: " + priceField.value() + " (must be greater than 0)");
                }
            } catch (Exception e) {
                errors.add("Invalid numerical unit price: " + priceField.value());
            }
        } else {
            missing.add("unitPrice");
        }

        // 5. Currency check
        FieldValue curField = candidates.get("currency");
        if (curField != null && curField.value() != null) {
            String c = curField.value().toString().trim().toUpperCase();
            if (!"INR".equals(c)) {
                errors.add("Unsupported currency: '" + c + "'. Only INR is supported for domestic procurement.");
            }
        }

        // 6. GST Rate check
        FieldValue gstField = candidates.get("materialTaxRate");
        if (gstField != null && gstField.value() != null) {
            String rateStr = gstField.value().toString().replace("%", "").trim();
            if (!SUPPORTED_GST_RATES.contains(rateStr)) {
                errors.add("Unsupported GST rate: " + rateStr + "%. Standard Indian GST rates are 0%, 5%, 12%, 18%, 28%.");
            }
        } else {
            missing.add("materialTaxRate");
        }

        // 7. Price Basis check
        FieldValue basisField = candidates.get("priceBasis");
        if (basisField == null || basisField.value() == null || (!"EXCLUSIVE".equals(basisField.value()) && !"INCLUSIVE".equals(basisField.value()))) {
            missing.add("priceBasis");
        }

        // 8. Freight Basis & Amount check
        FieldValue frBasisField = candidates.get("freightBasis");
        if (frBasisField == null || frBasisField.value() == null || (!"INCLUDED".equals(frBasisField.value()) && !"SEPARATE".equals(frBasisField.value()))) {
            missing.add("freightBasis");
        } else if ("SEPARATE".equals(frBasisField.value())) {
            FieldValue frAmt = candidates.get("freightAmount");
            if (frAmt == null || frAmt.value() == null) {
                missing.add("freightAmount");
            } else {
                try {
                    BigDecimal fVal = new BigDecimal(frAmt.value().toString());
                    if (fVal.compareTo(BigDecimal.ZERO) < 0) {
                        errors.add("Invalid freight amount: " + frAmt.value());
                    }
                } catch (Exception e) {
                    errors.add("Invalid numerical freight amount: " + frAmt.value());
                }
            }
        }

        // 9. Delivery Date check
        FieldValue delField = candidates.get("promisedDeliveryAt");
        if (delField == null || delField.value() == null || "AMBIGUOUS".equals(delField.status())) {
            missing.add("promisedDeliveryAt");
        }

        // 10. Validity Date check
        FieldValue valField = candidates.get("validUntil");
        if (valField == null || valField.value() == null) {
            missing.add("validUntil");
        }

        String validationStatus = errors.isEmpty() ? "VALID" : "FLAGGED";

        return new ValidationReport(
                candidates,
                new ArrayList<>(missing),
                ambiguities,
                validationStatus,
                errors
        );
    }
}
