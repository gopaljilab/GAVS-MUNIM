package org.munim.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SupplierMlDtos {

    private SupplierMlDtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MlStatusResponse(
            String serviceVersion,
            String featureSchemaVersion,
            boolean modelAvailable,
            String activeModelVersion,
            String trainingStatus,
            String latestTrainingRun,
            boolean predictionsEnabled,
            String status,
            String statusMessage,
            int eligibleHistoricalOutcomesCount,
            String datasetProvenancePolicy
    ) {}

    public record SupplierDeliveryFeatureVector(
            String featureSchemaVersion,
            OffsetDateTime featureExtractionTimestamp,
            UUID targetPoId,
            UUID supplierId,
            UUID materialId,
            BigDecimal orderedQuantity,
            BigDecimal agreedUnitPrice,
            BigDecimal agreedFreightAmount,
            String freightBasis,
            int promisedLeadDays,
            Integer factoryLeadDays,
            Integer leadTimeSlackDays,
            int histEligiblePoCount,
            int histOntimePoCount,
            BigDecimal histPointInTimeOtdRate,
            BigDecimal histAvgDelayDays,
            BigDecimal histPartialDeliveryRate,
            BigDecimal histNetUsableYield,
            int histMaterialPoCount,
            boolean hasMaterialEvidence
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeliveryRiskPredictionResponse(
            String predictionId,
            String targetPoId,
            String modelVersion,
            double predictedLateProbability,
            String riskCategory,
            String confidenceTier,
            double decisionThreshold,
            List<String> warnings,
            OffsetDateTime predictionTimestamp,
            boolean isAdvisoryOnly
    ) {}
}
