package org.munim.ml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.munim.ml.SupplierMlDtos.MlStatusResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SupplierMlClientTest {

    private SupplierMlConfig config;
    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private SupplierMlClient client;

    @BeforeEach
    void setUp() {
        config = new SupplierMlConfig();
        config.setServiceUrl("http://localhost:8000");
        config.setInternalSecret("test-secret-key-123");
        config.setEnabled(true);

        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        client = new SupplierMlClient(config, restTemplate);
    }

    @Test
    @DisplayName("1. Successfully retrieves status sending X-Internal-ML-Key header")
    void testGetStatusSuccess() {
        mockServer.expect(requestTo("http://localhost:8000/api/v1/ml/status"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-ML-Key", "test-secret-key-123"))
                .andRespond(withSuccess("""
                    {
                        "serviceVersion": "0.1.0",
                        "featureSchemaVersion": "1.0.0",
                        "modelAvailable": false,
                        "activeModelVersion": null,
                        "trainingStatus": "IDLE",
                        "latestTrainingRun": null,
                        "predictionsEnabled": false,
                        "status": "NO_VALIDATED_MODEL",
                        "statusMessage": "No validated model",
                        "eligibleHistoricalOutcomesCount": 0,
                        "datasetProvenancePolicy": "STRICT_SEPARATION_SYNTHETIC_VS_REAL"
                    }
                """, MediaType.APPLICATION_JSON));

        Optional<MlStatusResponse> statusOpt = client.getStatus();

        assertTrue(statusOpt.isPresent());
        MlStatusResponse status = statusOpt.get();
        assertEquals("0.1.0", status.serviceVersion());
        assertEquals("1.0.0", status.featureSchemaVersion());
        assertFalse(status.modelAvailable());
        assertEquals("NO_VALIDATED_MODEL", status.status());
        assertFalse(status.predictionsEnabled());

        mockServer.verify();
    }

    @Test
    @DisplayName("2. Handles 500/503 server error from ML service gracefully without throwing")
    void testGetStatusServerError() {
        mockServer.expect(requestTo("http://localhost:8000/api/v1/ml/status"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("Service Unavailable"));

        Optional<MlStatusResponse> statusOpt = client.getStatus();

        assertTrue(statusOpt.isPresent());
        MlStatusResponse status = statusOpt.get();
        assertEquals("HTTP_ERROR_503", status.status());
        assertFalse(status.predictionsEnabled());

        mockServer.verify();
    }

    @Test
    @DisplayName("3. Disabled ML service returns disabled status without network call")
    void testGetStatusWhenDisabled() {
        config.setEnabled(false);

        Optional<MlStatusResponse> statusOpt = client.getStatus();

        assertTrue(statusOpt.isPresent());
        MlStatusResponse status = statusOpt.get();
        assertEquals("DISABLED", status.status());
        assertFalse(status.predictionsEnabled());
    }
}
