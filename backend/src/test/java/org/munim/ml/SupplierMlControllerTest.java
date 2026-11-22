package org.munim.ml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.munim.security.DemoAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("postgres-test")
class SupplierMlControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(new DemoAuthFilter())
                .build();
    }

    @Test
    @DisplayName("1. Rejects unauthenticated requests to internal ml-status with 401")
    void testUnauthenticatedStatusRejected() throws Exception {
        mockMvc.perform(get("/api/v1/internal/ml-status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("2. Authenticated OWNER can retrieve ML status safely")
    void testOwnerRetrievesMlStatus() throws Exception {
        mockMvc.perform(get("/api/v1/internal/ml-status")
                        .header("Authorization", "Bearer dev-owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.featureSchemaVersion").value("1.0.0"))
                .andExpect(jsonPath("$.modelAvailable").value(false))
                .andExpect(jsonPath("$.predictionsEnabled").value(false));
    }

    @Test
    @DisplayName("3. Authenticated PROCUREMENT_OPERATOR can retrieve ML status")
    void testProcurementOperatorRetrievesMlStatus() throws Exception {
        mockMvc.perform(get("/api/v1/internal/ml-status")
                        .header("Authorization", "Bearer dev-procurement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelAvailable").value(false));
    }
}
