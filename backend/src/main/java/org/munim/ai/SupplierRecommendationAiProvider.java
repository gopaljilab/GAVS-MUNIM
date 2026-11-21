package org.munim.ai;

import org.munim.intelligence.SupplierRecommendationDtos.RecommendationContext;
import org.munim.intelligence.SupplierRecommendationDtos.RecommendationNarrative;

public interface SupplierRecommendationAiProvider {

    String getProviderName();

    RecommendationNarrative generateNarrative(RecommendationContext context);
}
