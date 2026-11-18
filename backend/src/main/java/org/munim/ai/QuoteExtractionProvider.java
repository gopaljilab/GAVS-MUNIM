package org.munim.ai;

import org.munim.api.ApiDtos.FieldValue;
import java.util.List;
import java.util.Map;

public interface QuoteExtractionProvider {
    String getProviderName();

    record RawExtraction(
            Map<String, FieldValue> candidates,
            List<String> missingFields,
            List<String> ambiguities,
            String providerStatus,
            String modelVersion,
            String promptVersion,
            String rawResponse
    ) {}

    RawExtraction extract(String rawMessage, Map<String, Object> context);
}
