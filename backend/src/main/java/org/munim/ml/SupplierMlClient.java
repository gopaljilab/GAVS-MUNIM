package org.munim.ml;

import org.munim.ml.SupplierMlDtos.DeliveryRiskPredictionResponse;
import org.munim.ml.SupplierMlDtos.MlStatusResponse;
import org.munim.ml.SupplierMlDtos.SupplierDeliveryFeatureVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Component
public class SupplierMlClient {

    private static final Logger log = LoggerFactory.getLogger(SupplierMlClient.class);
    private static final String INTERNAL_KEY_HEADER = "X-Internal-ML-Key";

    private final SupplierMlConfig config;
    private final RestTemplate restTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public SupplierMlClient(SupplierMlConfig config) {
        this.config = config;
        this.restTemplate = createRestTemplate(config);
    }

    // Package-private constructor for unit testing with custom RestTemplate
    SupplierMlClient(SupplierMlConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate createRestTemplate(SupplierMlConfig config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getConnectTimeoutMs());
        factory.setReadTimeout(config.getReadTimeoutMs());
        return new RestTemplate(factory);
    }

    public Optional<MlStatusResponse> getStatus() {
        if (!config.isEnabled()) {
            log.debug("ML service integration is disabled by configuration");
            return Optional.of(new MlStatusResponse(
                    "0.1.0", "1.0.0", false, null, "DISABLED", null, false,
                    "DISABLED", "ML service integration is disabled in configuration", 0, "N/A"
            ));
        }

        String url = config.getServiceUrl().replaceAll("/+$", "") + "/api/v1/ml/status";
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<MlStatusResponse> res = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    MlStatusResponse.class
            );
            return Optional.ofNullable(res.getBody());
        } catch (ResourceAccessException e) {
            log.warn("ML microservice connection failed (timeout/offline) at {}: {}", url, e.getMessage());
            return Optional.of(new MlStatusResponse(
                    "UNKNOWN", "1.0.0", false, null, "UNREACHABLE", null, false,
                    "SERVICE_UNAVAILABLE", "ML microservice is offline or unreachable at " + url, 0, "N/A"
            ));
        } catch (HttpStatusCodeException e) {
            log.warn("ML microservice returned HTTP {} error from {}: {}", e.getStatusCode(), url, e.getResponseBodyAsString());
            return Optional.of(new MlStatusResponse(
                    "UNKNOWN", "1.0.0", false, null, "ERROR", null, false,
                    "HTTP_ERROR_" + e.getStatusCode().value(), "ML service returned error: " + e.getStatusText(), 0, "N/A"
            ));
        } catch (Exception e) {
            log.warn("Unexpected error contacting ML microservice at {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<DeliveryRiskPredictionResponse> predictDeliveryRisk(SupplierDeliveryFeatureVector features) {
        if (!config.isEnabled()) {
            return Optional.empty();
        }

        String url = config.getServiceUrl().replaceAll("/+$", "") + "/api/v1/ml/predict/delivery-risk";
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("features", features), headers);

        try {
            ResponseEntity<DeliveryRiskPredictionResponse> res = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    DeliveryRiskPredictionResponse.class
            );
            return Optional.ofNullable(res.getBody());
        } catch (HttpStatusCodeException e) {
            log.info("ML prediction rejected as expected when NO_VALIDATED_MODEL (HTTP {}): {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("ML prediction failed gracefully: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_KEY_HEADER, config.getInternalSecret());
        return headers;
    }
}
