package org.munim.ml;

import org.munim.ml.SupplierMlDtos.MlStatusResponse;
import org.munim.ml.SupplierMlDtos.SupplierDeliveryFeatureVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class SupplierMlService {

    private static final Logger log = LoggerFactory.getLogger(SupplierMlService.class);

    private final SupplierMlClient mlClient;
    private final SupplierMlFeatureRepository featureRepository;

    public SupplierMlService(SupplierMlClient mlClient, SupplierMlFeatureRepository featureRepository) {
        this.mlClient = mlClient;
        this.featureRepository = featureRepository;
    }

    public MlStatusResponse getMlEngineStatus() {
        return mlClient.getStatus().orElseGet(() -> new MlStatusResponse(
                "UNKNOWN",
                "1.0.0",
                false,
                null,
                "UNAVAILABLE",
                null,
                false,
                "SERVICE_UNAVAILABLE",
                "ML service is offline or not responding. Procurement operations continue unimpeded.",
                0,
                "STRICT_SEPARATION_SYNTHETIC_VS_REAL"
        ));
    }

    public Optional<SupplierDeliveryFeatureVector> extractPointInTimeFeatures(UUID purchaseOrderId) {
        return featureRepository.extractFeaturesForPurchaseOrder(purchaseOrderId);
    }

    public Optional<SupplierDeliveryFeatureVector> extractPointInTimeFeatures(UUID purchaseOrderId, OffsetDateTime predictionCutoff) {
        return featureRepository.extractFeaturesForPurchaseOrder(purchaseOrderId, predictionCutoff);
    }
}
