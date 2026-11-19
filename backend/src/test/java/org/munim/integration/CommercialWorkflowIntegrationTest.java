package org.munim.integration;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.munim.api.ApiDtos.*;
import org.munim.profitability.CommercialDtos.*;
import org.munim.profitability.ProfitabilityDtos.OperationCostInput;
import org.munim.profitability.ProfitabilityDtos.SourcingBasis;
import org.munim.security.DemoAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("postgres-test")
public class CommercialWorkflowIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private ObjectMapper mapper;

    private MockMvc mockMvc;

    private UUID materialId;
    private UUID awardedRevisionId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilter(new DemoAuthFilter())
                .build();

        materialId = db.queryForObject("SELECT id FROM materials LIMIT 1", UUID.class);

        // Seed an awarded supplier quote revision for AWARDED_QUOTE testing
        seedAwardedSupplierQuoteRevision();
    }

    private void seedAwardedSupplierQuoteRevision() {
        UUID supplierId = db.queryForObject(
                "INSERT INTO suppliers(name, location, is_synthetic) VALUES ('Tata Steel Service Center', 'Jamshedpur', true) RETURNING id",
                UUID.class
        );

        UUID rfqId = db.queryForObject(
                "INSERT INTO rfqs(material_id, specification, quantity, unit, production_lead_days, required_procurement_delivery_at, reference_at, timezone, allow_split_sourcing, status) " +
                        "VALUES (?, 'CRCA IS 513 D', 1000.0000, 'kg', 5, now() + interval '20 days', now(), 'Asia/Kolkata', false, 'AWARDED') " +
                        "RETURNING id",
                UUID.class, materialId
        );

        UUID quoteId = db.queryForObject(
                "INSERT INTO supplier_quotes(rfq_id, supplier_id) VALUES (?, ?) RETURNING id",
                UUID.class, rfqId, supplierId
        );

        Map<String, Object> fields = Map.ofEntries(
                Map.entry("unitPrice", "85.50"),
                Map.entry("priceBasis", "EXCLUSIVE"),
                Map.entry("materialTaxRate", "18.00"),
                Map.entry("materialItcStatus", "CREDITABLE"),
                Map.entry("wastagePercent", "0"),
                Map.entry("quotedQuantity", "1000"),
                Map.entry("purchaseIncrement", "1"),
                Map.entry("moq", "100"),
                Map.entry("freightAmount", "0.00"),
                Map.entry("freightBasis", "EX_WORKS"),
                Map.entry("freightPriceBasis", "EXCLUSIVE"),
                Map.entry("freightTaxRate", "18.00"),
                Map.entry("freightItcStatus", "CREDITABLE"),
                Map.entry("quoteQuantityBasis", "INPUT_PURCHASED")
        );

        String fieldsJson;
        try {
            fieldsJson = mapper.writeValueAsString(fields);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        awardedRevisionId = db.queryForObject(
                "INSERT INTO supplier_quote_revisions(supplier_quote_id, rfq_id, revision_no, state, received_at, raw_message, raw_message_sha256, fields, completeness_status) " +
                        "VALUES (?, ?, 1, 'VERIFIED', now(), 'Quote message', 'sha256fake', ?::jsonb, 'COMPLETE') " +
                        "RETURNING id",
                UUID.class, quoteId, rfqId, fieldsJson
        );

        db.update("UPDATE supplier_quotes SET current_revision_id = ?, awarded_revision_id = ? WHERE id = ?",
                awardedRevisionId, awardedRevisionId, quoteId);
        db.update("UPDATE rfqs SET awarded_quote_id = ? WHERE id = ?", quoteId, rfqId);
    }

    // ==========================================
    // 1. Customer Enquiry Tests
    // ==========================================

    @Test
    @DisplayName("1.1 Successfully create, retrieve, list, and update customer enquiry")
    void testCustomerEnquiryLifecycle() throws Exception {
        OffsetDateTime delivery = OffsetDateTime.now().plusDays(30);
        EnquiryCreate create = new EnquiryCreate(
                "Godrej Aerospace Ltd",
                "PO-REQ-9921",
                "purchase@godrej.com",
                "+91-9876543210",
                delivery,
                "Precision laser cut enclosure panels"
        );

        // 1. Create Enquiry
        MvcResult res = mockMvc.perform(post("/api/v1/customer-enquiries")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn();

        CustomerEnquiryView created = mapper.readValue(res.getResponse().getContentAsString(), CustomerEnquiryView.class);
        assertNotNull(created.id());
        assertEquals("Godrej Aerospace Ltd", created.customerName());
        assertEquals("OPEN", created.status());

        // 2. Retrieve Enquiry by ID
        mockMvc.perform(get("/api/v1/customer-enquiries/" + created.id())
                        .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Godrej Aerospace Ltd"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        // 3. List Enquiries
        mockMvc.perform(get("/api/v1/customer-enquiries?status=OPEN")
                        .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        // 4. Update Permitted Details
        EnquiryUpdate update = new EnquiryUpdate(
                "PO-REQ-9921-REV1",
                "procurement@godrej.com",
                null,
                null,
                "Updated notes for drawing revision B",
                null
        );

        mockMvc.perform(patch("/api/v1/customer-enquiries/" + created.id())
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerReference").value("PO-REQ-9921-REV1"))
                .andExpect(jsonPath("$.contactEmail").value("procurement@godrej.com"))
                .andExpect(jsonPath("$.notes").value("Updated notes for drawing revision B"));
    }

    @Test
    @DisplayName("1.2 Enquiry lifecycle state validation rejects illegal transitions")
    void testEnquiryLifecycleValidation() throws Exception {
        EnquiryCreate create = new EnquiryCreate(
                "Bharat Forge", "BF-101", "buyer@bf.com", "+91-9988776655",
                OffsetDateTime.now().plusDays(25), "Forging enclosures"
        );

        MvcResult res = mockMvc.perform(post("/api/v1/customer-enquiries")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn();
        CustomerEnquiryView created = mapper.readValue(res.getResponse().getContentAsString(), CustomerEnquiryView.class);

        // Illegal transition: OPEN directly to WON (must be costed and quoted first)
        mockMvc.perform(patch("/api/v1/customer-enquiries/" + created.id())
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new EnquiryUpdate(null, null, null, null, null, "WON"))))
                .andExpect(status().isConflict());

        // Valid transition: OPEN to LOST
        mockMvc.perform(patch("/api/v1/customer-enquiries/" + created.id())
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new EnquiryUpdate(null, null, null, null, null, "LOST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"));

        // Illegal transition: LOST is terminal, cannot transition back to COSTED
        mockMvc.perform(patch("/api/v1/customer-enquiries/" + created.id())
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new EnquiryUpdate(null, null, null, null, null, "COSTED"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("1.3 Rejection of invalid enquiry inputs: blank name, past delivery date, unknown ID")
    void testEnquiryInputRejections() throws Exception {
        // Blank customer name
        EnquiryCreate blankName = new EnquiryCreate(
                "   ", "REF", "test@test.com", "123", OffsetDateTime.now().plusDays(10), "Notes"
        );
        mockMvc.perform(post("/api/v1/customer-enquiries")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(blankName)))
                .andExpect(status().isBadRequest());

        // Past delivery date
        EnquiryCreate pastDelivery = new EnquiryCreate(
                "Acme", "REF", "test@test.com", "123", OffsetDateTime.now().minusDays(5), "Notes"
        );
        mockMvc.perform(post("/api/v1/customer-enquiries")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(pastDelivery)))
                .andExpect(status().isBadRequest());

        // Unknown enquiry ID
        mockMvc.perform(get("/api/v1/customer-enquiries/" + UUID.randomUUID())
                        .header("Authorization", "Bearer dev-procurement"))
                .andExpect(status().isNotFound());
    }

    // ==========================================
    // 2. Bill of Materials (BOM) & Items Tests
    // ==========================================

    @Test
    @DisplayName("2.1 BOM creation and item management with AWARDED_QUOTE and ESTIMATE sourcing")
    void testBomAndItemManagement() throws Exception {
        // Create enquiry
        EnquiryCreate enqCreate = new EnquiryCreate(
                "L&T Defence", "LT-BOM-01", "lt@defence.com", "9876543210",
                OffsetDateTime.now().plusDays(40), "Rack fabrication"
        );
        MvcResult enqRes = mockMvc.perform(post("/api/v1/customer-enquiries")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(enqCreate)))
                .andExpect(status().isCreated()).andReturn();
        CustomerEnquiryView enquiry = mapper.readValue(enqRes.getResponse().getContentAsString(), CustomerEnquiryView.class);

        // 1. Create BOM
        BomCreate bomCreate = new BomCreate(1, new BigDecimal("100.0000"));
        MvcResult bomRes = mockMvc.perform(post("/api/v1/customer-enquiries/" + enquiry.id() + "/boms")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bomCreate)))
                .andExpect(status().isCreated()).andReturn();
        BomView bom = mapper.readValue(bomRes.getResponse().getContentAsString(), BomView.class);
        assertNotNull(bom.id());
        assertEquals(1, bom.bomVersion());
        assertEquals(new BigDecimal("100.0000"), bom.batchQuantity());

        // 2. Add BOM Item with AWARDED_QUOTE sourcing
        BomItemCreate awardedItem = new BomItemCreate(
                materialId,
                "Side Panel Left",
                new BigDecimal("2.5000"),
                "kg",
                new BigDecimal("8.00"),
                true,
                new BigDecimal("25.00"),
                SourcingBasis.AWARDED_QUOTE,
                awardedRevisionId,
                null,
                null
        );
        mockMvc.perform(post("/api/v1/boms/" + bom.id() + "/items")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(awardedItem)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partName").value("Side Panel Left"))
                .andExpect(jsonPath("$.sourcingBasis").value("AWARDED_QUOTE"))
                .andExpect(jsonPath("$.awardedQuoteRevisionId").value(awardedRevisionId.toString()));

        // 3. Add BOM Item with ESTIMATE sourcing
        BomItemCreate estimateItem = new BomItemCreate(
                materialId,
                "Top Cover",
                new BigDecimal("1.2000"),
                "kg",
                new BigDecimal("5.00"),
                false,
                null,
                SourcingBasis.ESTIMATE,
                null,
                new BigDecimal("92.00"),
                "Local steel yard oral quotation dated Nov 15 2026"
        );
        mockMvc.perform(post("/api/v1/boms/" + bom.id() + "/items")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(estimateItem)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partName").value("Top Cover"))
                .andExpect(jsonPath("$.sourcingBasis").value("ESTIMATE"))
                .andExpect(jsonPath("$.estimatedUnitCost").value(92.00))
                .andExpect(jsonPath("$.estimateRationale").value("Local steel yard oral quotation dated Nov 15 2026"));

        // 4. Retrieve BOM with items
        mockMvc.perform(get("/api/v1/boms/" + bom.id())
                        .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    @DisplayName("2.2 Sourcing validations: reject fake awarded revision and estimate without rationale")
    void testBomItemSourcingRejections() throws Exception {
        EnquiryCreate enqCreate = new EnquiryCreate(
                "Tata Motors", "TM-001", "tm@motors.com", "9998887776",
                OffsetDateTime.now().plusDays(30), "Chassis bracket"
        );
        MvcResult enqRes = mockMvc.perform(post("/api/v1/customer-enquiries")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(enqCreate)))
                .andExpect(status().isCreated()).andReturn();
        UUID enquiryId = mapper.readValue(enqRes.getResponse().getContentAsString(), CustomerEnquiryView.class).id();

        MvcResult bomRes = mockMvc.perform(post("/api/v1/customer-enquiries/" + enquiryId + "/boms")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new BomCreate(1, new BigDecimal("50.0000")))))
                .andExpect(status().isCreated()).andReturn();
        UUID bomId = mapper.readValue(bomRes.getResponse().getContentAsString(), BomView.class).id();

        // 1. Fake / unawarded revision ID
        UUID fakeRevId = UUID.randomUUID();
        BomItemCreate fakeAwarded = new BomItemCreate(
                materialId, "Bracket A", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO,
                false, null, SourcingBasis.AWARDED_QUOTE, fakeRevId, null, null
        );
        mockMvc.perform(post("/api/v1/boms/" + bomId + "/items")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(fakeAwarded)))
                .andExpect(status().isUnprocessableEntity());

        // 2. Estimate without rationale
        BomItemCreate missingRationale = new BomItemCreate(
                materialId, "Bracket B", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO,
                false, null, SourcingBasis.ESTIMATE, null, new BigDecimal("80.00"), "   "
        );
        mockMvc.perform(post("/api/v1/boms/" + bomId + "/items")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(missingRationale)))
                .andExpect(status().isBadRequest());

        // 3. Estimate masquerading with awarded revision ID
        BomItemCreate masquerading = new BomItemCreate(
                materialId, "Bracket C", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO,
                false, null, SourcingBasis.ESTIMATE, awardedRevisionId, new BigDecimal("80.00"), "Rationale"
        );
        mockMvc.perform(post("/api/v1/boms/" + bomId + "/items")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(masquerading)))
                .andExpect(status().isBadRequest());
    }

    // ==========================================
    // 3. Costing Workflow Tests
    // ==========================================

    @Test
    @DisplayName("3.1 Costing workflow: Execute costing, persist snapshot and hash, advance enquiry to COSTED")
    void testCostingExecutionAndSnapshotPersistence() throws Exception {
        // Setup enquiry and BOM with 1 awarded item and 1 estimate item
        EnquiryCreate enqCreate = new EnquiryCreate(
                "Siemens Energy", "SE-2026", "siemens@energy.com", "9876543210",
                OffsetDateTime.now().plusDays(45), "Turbine enclosure"
        );
        MvcResult enqRes = mockMvc.perform(post("/api/v1/customer-enquiries")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(enqCreate)))
                .andExpect(status().isCreated()).andReturn();
        UUID enquiryId = mapper.readValue(enqRes.getResponse().getContentAsString(), CustomerEnquiryView.class).id();

        MvcResult bomRes = mockMvc.perform(post("/api/v1/customer-enquiries/" + enquiryId + "/boms")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new BomCreate(1, new BigDecimal("100.0000")))))
                .andExpect(status().isCreated()).andReturn();
        UUID bomId = mapper.readValue(bomRes.getResponse().getContentAsString(), BomView.class).id();

        // Item 1: Awarded quote item (unitPrice=85.50, taxRate=18, itcStatus=CREDITABLE from quote)
        mockMvc.perform(post("/api/v1/boms/" + bomId + "/items")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new BomItemCreate(
                                materialId, "Panel", new BigDecimal("2.0000"), "kg", new BigDecimal("10.00"),
                                true, new BigDecimal("30.00"), SourcingBasis.AWARDED_QUOTE, awardedRevisionId, null, null
                        ))))
                .andExpect(status().isCreated());

        // Item 2: Estimate item
        MvcResult item2Res = mockMvc.perform(post("/api/v1/boms/" + bomId + "/items")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new BomItemCreate(
                                materialId, "Gasket Strip", new BigDecimal("1.0000"), "meter", BigDecimal.ZERO,
                                false, null, SourcingBasis.ESTIMATE, null, new BigDecimal("50.00"), "Market estimate"
                        ))))
                .andExpect(status().isCreated()).andReturn();
        UUID item2Id = mapper.readValue(item2Res.getResponse().getContentAsString(), BomItemView.class).id();

        // Execute Costing Run
        CostingRunRequest run = new CostingRunRequest(
                List.of(
                        new OperationCostInput("Laser Cutting", new BigDecimal("1.00"), new BigDecimal("500.00"),
                                new BigDecimal("0.05"), new BigDecimal("600.00"), new BigDecimal("0.05"), new BigDecimal("200.00"))
                ),
                new BigDecimal("0.15"), // 15% overhead
                new BigDecimal("5.00"),  // packaging
                new BigDecimal("10.00"), // freight
                new BigDecimal("0.20"),  // 20% target margin
                new BigDecimal("0.10"),  // 10% minimum margin
                new BigDecimal("450.00"), // quoted selling price
                new BigDecimal("18.00"), // output GST
                List.of(
                        new ItemTaxInput(item2Id, "CREDITABLE", new BigDecimal("18.00"))
                )
        );

        MvcResult costRes = mockMvc.perform(post("/api/v1/boms/" + bomId + "/costings")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(run)))
                .andExpect(status().isCreated())
                .andReturn();

        CostingSheetView sheet = mapper.readValue(costRes.getResponse().getContentAsString(), CostingSheetView.class);
        assertNotNull(sheet.id());
        assertEquals(enquiryId, sheet.enquiryId());
        assertEquals(bomId, sheet.bomId());
        assertTrue(sheet.totalCost().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(sheet.targetPrice().compareTo(sheet.totalCost()) > 0);
        assertEquals(new BigDecimal("450.00"), sheet.quotedPrice());
        assertEquals("HEALTHY_MARGIN", sheet.marginClassification());
        assertNotNull(sheet.snapshotSha256());
        assertEquals(64, sheet.snapshotSha256().length());

        // Verify enquiry status advanced to COSTED
        mockMvc.perform(get("/api/v1/customer-enquiries/" + enquiryId)
                        .header("Authorization", "Bearer dev-procurement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COSTED"));

        // Verify retrieval of persisted costing sheet by ID
        mockMvc.perform(get("/api/v1/costing-sheets/" + sheet.id())
                        .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotSha256").value(sheet.snapshotSha256()))
                .andExpect(jsonPath("$.marginClassification").value("HEALTHY_MARGIN"));
    }

    @Test
    @DisplayName("3.2 Missing mandatory costing inputs rejected with structured validation error (no silent zeros)")
    void testMissingCostingInputsRejected() throws Exception {
        EnquiryCreate enqCreate = new EnquiryCreate(
                "Thermax Ltd", "TH-001", "th@thermax.com", "9876543210",
                OffsetDateTime.now().plusDays(20), "Heat exchanger panel"
        );
        MvcResult enqRes = mockMvc.perform(post("/api/v1/customer-enquiries")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(enqCreate)))
                .andExpect(status().isCreated()).andReturn();
        UUID enquiryId = mapper.readValue(enqRes.getResponse().getContentAsString(), CustomerEnquiryView.class).id();

        MvcResult bomRes = mockMvc.perform(post("/api/v1/customer-enquiries/" + enquiryId + "/boms")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new BomCreate(1, new BigDecimal("100.0000")))))
                .andExpect(status().isCreated()).andReturn();
        UUID bomId = mapper.readValue(bomRes.getResponse().getContentAsString(), BomView.class).id();

        // Add Estimate BOM item
        mockMvc.perform(post("/api/v1/boms/" + bomId + "/items")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new BomItemCreate(
                                materialId, "Casing", new BigDecimal("5.0000"), "kg", BigDecimal.ZERO,
                                false, null, SourcingBasis.ESTIMATE, null, new BigDecimal("70.00"), "Estimate"
                        ))))
                .andExpect(status().isCreated());

        // Costing request without providing required ItemTaxInput for the estimate item
        CostingRunRequest missingTax = new CostingRunRequest(
                List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00"),
                List.of() // empty tax inputs!
        );

        mockMvc.perform(post("/api/v1/boms/" + bomId + "/costings")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(missingTax)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MISSING_COSTING_INPUT"));
    }

    // ==========================================
    // 4. Customer Quotation Tests
    // ==========================================

    @Test
    @DisplayName("4.1 Generate customer quotation from costing sheet and advance enquiry to QUOTED")
    void testCustomerQuotationGeneration() throws Exception {
        // Setup enquiry, BOM, item and costing sheet
        EnquiryCreate enqCreate = new EnquiryCreate(
                "BHEL Haridwar", "BHEL-2026", "bhel@gov.in", "9876543210",
                OffsetDateTime.now().plusDays(60), "Stator frames"
        );
        MvcResult enqRes = mockMvc.perform(post("/api/v1/customer-enquiries")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(enqCreate)))
                .andExpect(status().isCreated()).andReturn();
        UUID enquiryId = mapper.readValue(enqRes.getResponse().getContentAsString(), CustomerEnquiryView.class).id();

        MvcResult bomRes = mockMvc.perform(post("/api/v1/customer-enquiries/" + enquiryId + "/boms")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new BomCreate(1, new BigDecimal("50.0000")))))
                .andExpect(status().isCreated()).andReturn();
        UUID bomId = mapper.readValue(bomRes.getResponse().getContentAsString(), BomView.class).id();

        mockMvc.perform(post("/api/v1/boms/" + bomId + "/items")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new BomItemCreate(
                                materialId, "Frame", new BigDecimal("10.0000"), "kg", BigDecimal.ZERO,
                                false, null, SourcingBasis.AWARDED_QUOTE, awardedRevisionId, null, null
                        ))))
                .andExpect(status().isCreated());

        CostingRunRequest run = new CostingRunRequest(
                List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.20"), new BigDecimal("0.10"), new BigDecimal("1100.00"), new BigDecimal("18.00"),
                List.of()
        );
        MvcResult costRes = mockMvc.perform(post("/api/v1/boms/" + bomId + "/costings")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(run)))
                .andExpect(status().isCreated()).andReturn();
        CostingSheetView costing = mapper.readValue(costRes.getResponse().getContentAsString(), CostingSheetView.class);

        // Generate Quotation
        OffsetDateTime from = OffsetDateTime.now();
        OffsetDateTime until = from.plusDays(15);
        QuotationCreate quoteCreate = new QuotationCreate(
                costing.id(),
                from,
                until,
                "30 Days Net from Delivery",
                new BigDecimal("1100.00"),
                new BigDecimal("18.00")
        );

        MvcResult quoteRes = mockMvc.perform(post("/api/v1/customer-quotations")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(quoteCreate)))
                .andExpect(status().isCreated())
                .andReturn();

        CustomerQuotationView quotation = mapper.readValue(quoteRes.getResponse().getContentAsString(), CustomerQuotationView.class);
        assertNotNull(quotation.id());
        assertEquals(enquiryId, quotation.enquiryId());
        assertEquals(costing.id(), quotation.costingSheetId());
        assertEquals(new BigDecimal("1100.00"), quotation.sellingPrice());
        assertEquals(new BigDecimal("18.00"), quotation.outputGstRate());
        // Customer Payable = 1100 + (1100 * 0.18 = 198) = 1298.00
        assertEquals(new BigDecimal("1298.00"), quotation.customerPayable());
        assertEquals("HEALTHY_MARGIN", quotation.marginClassification());
        assertEquals("DRAFT", quotation.approvalStatus()); // NO approval in Step 4

        // Verify enquiry status advanced to QUOTED
        mockMvc.perform(get("/api/v1/customer-enquiries/" + enquiryId)
                        .header("Authorization", "Bearer dev-procurement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUOTED"));

        // Verify quotation retrieval by ID
        mockMvc.perform(get("/api/v1/customer-quotations/" + quotation.id())
                        .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerPayable").value(1298.00));

        // Verify listing quotations for enquiry
        mockMvc.perform(get("/api/v1/customer-enquiries/" + enquiryId + "/quotations")
                        .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("4.2 Quotation validation: reject invalid validity dates and nonexistent costing sheet")
    void testQuotationValidationRejections() throws Exception {
        // Nonexistent costing sheet
        QuotationCreate nonexistentCosting = new QuotationCreate(
                UUID.randomUUID(), OffsetDateTime.now(), OffsetDateTime.now().plusDays(10), "Net 30", new BigDecimal("500.00"), new BigDecimal("18.00")
        );
        mockMvc.perform(post("/api/v1/customer-quotations")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(nonexistentCosting)))
                .andExpect(status().isNotFound());

        // Invalid validity dates: validUntil before validFrom
        EnquiryCreate enqCreate = new EnquiryCreate(
                "HAL Bangalore", "HAL-01", "hal@gov.in", "9876543210",
                OffsetDateTime.now().plusDays(30), "Avionics panel"
        );
        MvcResult enqRes = mockMvc.perform(post("/api/v1/customer-enquiries")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(enqCreate)))
                .andExpect(status().isCreated()).andReturn();
        UUID enquiryId = mapper.readValue(enqRes.getResponse().getContentAsString(), CustomerEnquiryView.class).id();

        MvcResult bomRes = mockMvc.perform(post("/api/v1/customer-enquiries/" + enquiryId + "/boms")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new BomCreate(1, new BigDecimal("10.0000")))))
                .andExpect(status().isCreated()).andReturn();
        UUID bomId = mapper.readValue(bomRes.getResponse().getContentAsString(), BomView.class).id();

        mockMvc.perform(post("/api/v1/boms/" + bomId + "/items")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new BomItemCreate(
                                materialId, "Plate", new BigDecimal("1.0000"), "kg", BigDecimal.ZERO,
                                false, null, SourcingBasis.AWARDED_QUOTE, awardedRevisionId, null, null
                        ))))
                .andExpect(status().isCreated());

        MvcResult costRes = mockMvc.perform(post("/api/v1/boms/" + bomId + "/costings")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CostingRunRequest(
                                List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                                new BigDecimal("0.20"), new BigDecimal("0.10"), null, new BigDecimal("18.00"), List.of()
                        ))))
                .andExpect(status().isCreated()).andReturn();
        UUID costingId = mapper.readValue(costRes.getResponse().getContentAsString(), CostingSheetView.class).id();

        QuotationCreate invalidDates = new QuotationCreate(
                costingId, OffsetDateTime.now(), OffsetDateTime.now().minusDays(1), "Net 30", new BigDecimal("500.00"), new BigDecimal("18.00")
        );
        mockMvc.perform(post("/api/v1/customer-quotations")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(invalidDates)))
                .andExpect(status().isBadRequest());
    }

    // ==========================================
    // 5. Security & Role Tests
    // ==========================================

    @Test
    @DisplayName("5.1 Unauthenticated requests rejected; READ_ONLY_EVALUATOR prohibited from mutating commercial records")
    void testSecurityAndRoles() throws Exception {
        EnquiryCreate create = new EnquiryCreate(
                "BEL", "BEL-101", "bel@gov.in", "9876543210", OffsetDateTime.now().plusDays(30), "Radar bracket"
        );

        // 1. Unauthenticated request
        mockMvc.perform(post("/api/v1/customer-enquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(create)))
                .andExpect(status().isUnauthorized());

        // 2. READ_ONLY_EVALUATOR trying to create customer enquiry
        mockMvc.perform(post("/api/v1/customer-enquiries")
                        .header("Authorization", "Bearer dev-evaluator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(create)))
                .andExpect(status().isForbidden());

        // 3. READ_ONLY_EVALUATOR permitted to read enquiries
        mockMvc.perform(get("/api/v1/customer-enquiries")
                        .header("Authorization", "Bearer dev-evaluator"))
                .andExpect(status().isOk());
    }

    // ==========================================
    // 6. Guided Demo Commercial Journey Replay
    // ==========================================

    @Test
    @DisplayName("6.1 Guided Demo E2E Replay: Enquiry -> BOM -> Costing -> Quotation -> Owner Approval Gateway")
    void test11_guidedDemoFullCommercialJourneyReplay() throws Exception {
        // Step 4 of Guided Demo: Customer Enquiry intake
        EnquiryCreate enquiryReq = new EnquiryCreate(
                "Defence Chassis Corp (Demo Test)",
                "PO-DCC-REPLAY-001",
                "orders@defencechassis.in",
                "+91-9876543210",
                OffsetDateTime.now().plusDays(45),
                "High precision laser cut chassis brackets"
        );
        MvcResult enqRes = mockMvc.perform(post("/api/v1/customer-enquiries")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(enquiryReq)))
                .andExpect(status().isCreated())
                .andReturn();
        CustomerEnquiryView enq = mapper.readValue(enqRes.getResponse().getContentAsString(), CustomerEnquiryView.class);
        assertNotNull(enq.id());

        // Step 5 of Guided Demo: BOM Creation & Sourcing from Awarded Quote
        BomCreate bomReq = new BomCreate(1, new BigDecimal("100.0000"));
        MvcResult bomRes = mockMvc.perform(post("/api/v1/customer-enquiries/" + enq.id() + "/boms")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bomReq)))
                .andExpect(status().isCreated())
                .andReturn();
        BomView bom = mapper.readValue(bomRes.getResponse().getContentAsString(), BomView.class);
        assertNotNull(bom.id());

        BomItemCreate itemReq = new BomItemCreate(
                materialId,
                "Armoured Chassis Bracket 5mm",
                new BigDecimal("2.5000"),
                "kg",
                new BigDecimal("10.00"),
                true,
                new BigDecimal("25.00"),
                SourcingBasis.AWARDED_QUOTE,
                awardedRevisionId,
                null,
                null
        );
        MvcResult itemRes = mockMvc.perform(post("/api/v1/boms/" + bom.id() + "/items")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(itemReq)))
                .andExpect(status().isCreated())
                .andReturn();
        BomItemView bomItem = mapper.readValue(itemRes.getResponse().getContentAsString(), BomItemView.class);
        assertNotNull(bomItem.id());

        // Deterministic Costing Run (CNC Laser + Press Brake, 20% margin)
        CostingRunRequest costReq = new CostingRunRequest(
                List.of(
                        new OperationCostInput("CNC Laser Cutting", new BigDecimal("1.0000"), new BigDecimal("600.00"), new BigDecimal("0.0800"), new BigDecimal("900.00"), new BigDecimal("0.1000"), new BigDecimal("350.00")),
                        new OperationCostInput("Press Brake Bending", new BigDecimal("0.5000"), new BigDecimal("400.00"), new BigDecimal("0.0500"), new BigDecimal("600.00"), new BigDecimal("0.0800"), new BigDecimal("300.00"))
                ),
                new BigDecimal("0.2000"),
                new BigDecimal("15.00"),
                new BigDecimal("20.00"),
                new BigDecimal("0.2000"),
                new BigDecimal("0.1000"),
                null,
                new BigDecimal("18.00"),
                List.of(new ItemTaxInput(bomItem.id(), "CREDITABLE", new BigDecimal("18.00")))
        );
        MvcResult costRes = mockMvc.perform(post("/api/v1/boms/" + bom.id() + "/costings")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(costReq)))
                .andExpect(status().isCreated())
                .andReturn();
        CostingSheetView costing = mapper.readValue(costRes.getResponse().getContentAsString(), CostingSheetView.class);
        assertNotNull(costing.id());
        assertNotNull(costing.snapshotSha256());
        assertEquals("20.00", costing.targetMargin().toPlainString());

        // Step 6 of Guided Demo: Customer Quotation Snapshot
        QuotationCreate quoteReq = new QuotationCreate(
                costing.id(),
                OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now().plusDays(30),
                "50% Advance, 50% on Delivery",
                costing.targetPrice(),
                new BigDecimal("18.00")
        );
        MvcResult quoteRes = mockMvc.perform(post("/api/v1/customer-quotations")
                        .header("Authorization", "Bearer dev-procurement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(quoteReq)))
                .andExpect(status().isCreated())
                .andReturn();
        CustomerQuotationView quotation = mapper.readValue(quoteRes.getResponse().getContentAsString(), CustomerQuotationView.class);
        assertNotNull(quotation.id());

        // Submit to Approvals Gateway with immutable SHA-256 snapshot
        QuotationApprovalSubmission submissionReq = new QuotationApprovalSubmission(costing.snapshotSha256(), "Submit for owner review");
        MvcResult propRes = mockMvc.perform(post("/api/v1/customer-quotations/" + quotation.id() + "/submit-approval")
                        .header("Authorization", "Bearer dev-procurement")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(submissionReq)))
                .andExpect(status().isCreated())
                .andReturn();
        ProposalView proposal = mapper.readValue(propRes.getResponse().getContentAsString(), ProposalView.class);
        assertNotNull(proposal.id());
        assertEquals("PENDING", proposal.status());

        // Step 7 of Guided Demo: Owner Human Approval
        DecisionCreate decision = new DecisionCreate("APPROVE", "Quotation approved at target 20% margin with audited costing snapshot", proposal.snapshotHash());
        mockMvc.perform(post("/api/v1/approvals/" + proposal.id() + "/decision")
                        .header("Authorization", "Bearer dev-owner")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"));

        // Verify quotation status is atomically APPROVED
        mockMvc.perform(get("/api/v1/customer-quotations/" + quotation.id())
                        .header("Authorization", "Bearer dev-owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
    }
}
