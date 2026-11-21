package org.munim.intelligence;

import org.munim.intelligence.SupplierPerformanceDtos.SupplierScorecardView;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SupplierRecommendationDtos {

    private SupplierRecommendationDtos() {}

    public record SupplierRecommendationList(
            UUID rfqId,
            String rfqMaterialName,
            BigDecimal rfqQuantity,
            String rfqUnit,
            OffsetDateTime requiredDeliveryAt,
            List<SupplierRecommendationView> recommendations,
            String overallProcurementBrief,
            String aiProviderUsed,
            boolean aiGeneratedExplanation
    ) {}

    public record SupplierRecommendationView(
            UUID supplierId,
            String supplierName,
            int rank,
            String recommendationTier,
            String recommendationHeadline,
            UUID quoteId,
            UUID quoteRevisionId,
            BigDecimal quotedUnitPrice,
            BigDecimal quotedEconomicLandedCost,
            BigDecimal quotedCashPayable,
            OffsetDateTime quotedPromisedDeliveryDate,
            Boolean meetsRequiredDeliveryDate,
            SupplierScorecardView historicalScorecard,
            List<String> verifiedStrengths,
            List<String> identifiedRisks,
            List<String> missingEvidence,
            String advisoryExplanation,
            boolean verifiedPerformerBadge,
            boolean costDriftWarning,
            boolean deliveryRiskWarning,
            boolean isMaterialSpecificEvidence,
            BigDecimal compositeSuitabilityScore
    ) {
        public SupplierRecommendationView(
                UUID supplierId, String supplierName, int rank, String recommendationTier, String recommendationHeadline,
                UUID quoteId, UUID quoteRevisionId, BigDecimal quotedUnitPrice, BigDecimal quotedEconomicLandedCost,
                BigDecimal quotedCashPayable, OffsetDateTime quotedPromisedDeliveryDate, Boolean meetsRequiredDeliveryDate,
                SupplierScorecardView historicalScorecard, List<String> verifiedStrengths, List<String> identifiedRisks,
                List<String> missingEvidence, String advisoryExplanation, boolean verifiedPerformerBadge,
                boolean costDriftWarning, boolean deliveryRiskWarning, BigDecimal compositeSuitabilityScore
        ) {
            this(supplierId, supplierName, rank, recommendationTier, recommendationHeadline, quoteId, quoteRevisionId,
                    quotedUnitPrice, quotedEconomicLandedCost, quotedCashPayable, quotedPromisedDeliveryDate,
                    meetsRequiredDeliveryDate, historicalScorecard, verifiedStrengths, identifiedRisks, missingEvidence,
                    advisoryExplanation, verifiedPerformerBadge, costDriftWarning, deliveryRiskWarning, false,
                    compositeSuitabilityScore);
        }
    }

    public record RecommendationContext(
            UUID rfqId,
            String materialName,
            BigDecimal quantity,
            String unit,
            OffsetDateTime requiredDeliveryAt,
            List<SupplierRecommendationCandidate> candidates
    ) {}

    public record SupplierRecommendationCandidate(
            UUID supplierId,
            String supplierName,
            int rank,
            String recommendationTier,
            BigDecimal quotedUnitPrice,
            BigDecimal quotedEconomicLandedCost,
            OffsetDateTime quotedPromisedDeliveryDate,
            Boolean meetsRequiredDeliveryDate,
            SupplierScorecardView scorecard,
            List<String> verifiedStrengths,
            List<String> identifiedRisks,
            List<String> missingEvidence,
            boolean verifiedPerformerBadge,
            boolean costDriftWarning,
            boolean deliveryRiskWarning,
            boolean isMaterialSpecificEvidence,
            BigDecimal suitabilityScore
    ) {
        public SupplierRecommendationCandidate(
                UUID supplierId, String supplierName, int rank, String recommendationTier,
                BigDecimal quotedUnitPrice, BigDecimal quotedEconomicLandedCost, OffsetDateTime quotedPromisedDeliveryDate,
                Boolean meetsRequiredDeliveryDate, SupplierScorecardView scorecard, List<String> verifiedStrengths,
                List<String> identifiedRisks, List<String> missingEvidence, boolean verifiedPerformerBadge,
                boolean costDriftWarning, boolean deliveryRiskWarning
        ) {
            this(supplierId, supplierName, rank, recommendationTier, quotedUnitPrice, quotedEconomicLandedCost,
                    quotedPromisedDeliveryDate, meetsRequiredDeliveryDate, scorecard, verifiedStrengths, identifiedRisks,
                    missingEvidence, verifiedPerformerBadge, costDriftWarning, deliveryRiskWarning, false,
                    new BigDecimal("50.00"));
        }
    }

    public record RecommendationNarrative(
            String overallProcurementBrief,
            List<SupplierAdvisoryItem> supplierAdvisories,
            String aiProviderUsed,
            boolean aiGenerated
    ) {}

    public record SupplierAdvisoryItem(
            UUID supplierId,
            String headline,
            String advisoryExplanation
    ) {}
}
