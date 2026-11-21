package org.munim.intelligence;

import tools.jackson.databind.ObjectMapper;
import org.munim.ai.MockRecommendationProvider;
import org.munim.ai.MunimAiConfig;
import org.munim.ai.OpenAiRecommendationProvider;
import org.munim.ai.SupplierRecommendationAiProvider;
import org.munim.intelligence.SupplierPerformanceDtos.SupplierScorecardView;
import org.munim.intelligence.SupplierRecommendationDtos.*;
import org.munim.intelligence.SupplierRecommendationEngine.CandidateInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SupplierRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(SupplierRecommendationService.class);

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final MunimAiConfig aiConfig;
    private final SupplierPerformanceService performanceService;
    private final SupplierRecommendationEngine recommendationEngine;
    private final OpenAiRecommendationProvider openAiProvider;
    private final MockRecommendationProvider mockProvider;

    public SupplierRecommendationService(
            JdbcTemplate db,
            ObjectMapper json,
            MunimAiConfig aiConfig,
            SupplierPerformanceService performanceService,
            SupplierRecommendationEngine recommendationEngine,
            OpenAiRecommendationProvider openAiProvider,
            MockRecommendationProvider mockProvider
    ) {
        this.db = db;
        this.json = json;
        this.aiConfig = aiConfig;
        this.performanceService = performanceService;
        this.recommendationEngine = recommendationEngine;
        this.openAiProvider = openAiProvider;
        this.mockProvider = mockProvider;
    }

    public SupplierRecommendationList getRecommendationsForRfq(UUID rfqId) {
        // 1. Fetch RFQ header
        var rfqRows = db.query(
                "SELECT q.id, q.material_id, m.name as material_name, q.quantity, q.unit, " +
                        "q.required_procurement_delivery_at, q.status " +
                        "FROM rfqs q " +
                        "JOIN materials m ON m.id = q.material_id " +
                        "WHERE q.id = ?",
                (rs, n) -> Map.of(
                        "id", rs.getObject("id", UUID.class),
                        "materialId", rs.getObject("material_id", UUID.class),
                        "materialName", rs.getString("material_name"),
                        "quantity", rs.getBigDecimal("quantity"),
                        "unit", rs.getString("unit"),
                        "requiredDeliveryAt", rs.getObject("required_procurement_delivery_at", OffsetDateTime.class)
                ),
                rfqId
        );

        if (rfqRows.isEmpty()) {
            throw new NoSuchElementException("RFQ not found: " + rfqId);
        }

        var rfq = rfqRows.getFirst();
        UUID materialId = (UUID) rfq.get("materialId");
        String materialName = (String) rfq.get("materialName");
        BigDecimal rfqQuantity = (BigDecimal) rfq.get("quantity");
        String rfqUnit = (String) rfq.get("unit");
        OffsetDateTime requiredDeliveryAt = (OffsetDateTime) rfq.get("requiredDeliveryAt");

        // 2. Fetch all quote revisions for this RFQ
        var quoteRows = db.query(
                "SELECT sq.id as quote_id, sq.supplier_id, s.name as supplier_name, " +
                        "r.id as revision_id, r.revision_no, r.fields::text, " +
                        "el.economic_landed_cost, el.cash_payable " +
                        "FROM supplier_quotes sq " +
                        "JOIN suppliers s ON s.id = sq.supplier_id " +
                        "JOIN supplier_quote_revisions r ON r.id = sq.current_revision_id " +
                        "LEFT JOIN quote_evaluations qe ON qe.quote_revision_id = r.id " +
                        "LEFT JOIN evaluation_lines el ON el.evaluation_id = qe.id " +
                        "WHERE sq.rfq_id = ? AND s.active = true",
                (rs, n) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("quoteId", rs.getObject("quote_id", UUID.class));
                    map.put("supplierId", rs.getObject("supplier_id", UUID.class));
                    map.put("supplierName", rs.getString("supplier_name"));
                    map.put("revisionId", rs.getObject("revision_id", UUID.class));
                    map.put("revisionNo", rs.getInt("revision_no"));
                    map.put("fieldsJson", rs.getString("fields"));
                    map.put("economicLandedCost", rs.getBigDecimal("economic_landed_cost"));
                    map.put("cashPayable", rs.getBigDecimal("cash_payable"));
                    return map;
                },
                rfqId
        );

        // 3. Assemble candidate inputs with historical scorecards
        List<CandidateInput> candidateInputs = new ArrayList<>();
        Set<UUID> quotedSupplierIds = new HashSet<>();

        for (var qr : quoteRows) {
            UUID supplierId = (UUID) qr.get("supplierId");
            quotedSupplierIds.add(supplierId);
            String supplierName = (String) qr.get("supplierName");
            UUID quoteId = (UUID) qr.get("quoteId");
            UUID revisionId = (UUID) qr.get("revisionId");
            BigDecimal landedCost = (BigDecimal) qr.get("economicLandedCost");
            BigDecimal cashPayable = (BigDecimal) qr.get("cashPayable");

            Map<String, Object> fields = parseJson(qr.get("fieldsJson"));
            BigDecimal unitPrice = parseBigDecimal(fields.get("unitPrice"));
            OffsetDateTime promisedDelivery = parseOffsetDateTime(fields.get("promisedDeliveryAt"));

            // Retrieve material-specific scorecard and overall scorecard from V2.2 engine
            SupplierScorecardView materialScorecard = performanceService.getSupplierScorecard(supplierId, materialId, null, null);
            SupplierScorecardView overallScorecard = performanceService.getSupplierScorecard(supplierId, null, null, null);

            candidateInputs.add(new CandidateInput(
                    supplierId,
                    supplierName,
                    quoteId,
                    revisionId,
                    unitPrice,
                    landedCost,
                    cashPayable,
                    promisedDelivery,
                    materialScorecard,
                    overallScorecard
            ));
        }

        // If no quotes exist yet on the RFQ, benchmark all other active suppliers for this material
        if (candidateInputs.isEmpty()) {
            var activeSuppliers = db.query(
                    "SELECT id, name FROM suppliers WHERE active = true ORDER BY name",
                    (rs, n) -> Map.of("id", rs.getObject("id", UUID.class), "name", rs.getString("name"))
            );

            for (var as : activeSuppliers) {
                UUID sid = (UUID) as.get("id");
                String sname = (String) as.get("name");

                SupplierScorecardView materialScorecard = performanceService.getSupplierScorecard(sid, materialId, null, null);
                SupplierScorecardView overallScorecard = performanceService.getSupplierScorecard(sid, null, null, null);

                candidateInputs.add(new CandidateInput(
                        sid,
                        sname,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        materialScorecard,
                        overallScorecard
                ));
            }
        }

        // 4. Run deterministic ranking and evaluation
        List<SupplierRecommendationCandidate> rankedCandidates = recommendationEngine.rankAndEvaluate(
                requiredDeliveryAt,
                candidateInputs
        );

        // 5. Generate AI/Deterministic Narrative Brief
        RecommendationContext recContext = new RecommendationContext(
                rfqId,
                materialName,
                rfqQuantity,
                rfqUnit,
                requiredDeliveryAt,
                rankedCandidates
        );

        RecommendationNarrative narrative = generateNarrativeWithFallback(recContext);

        // 6. Map advisories back to candidates to build final view
        Map<UUID, SupplierAdvisoryItem> advisoryMap = narrative.supplierAdvisories().stream()
                .collect(Collectors.toMap(SupplierAdvisoryItem::supplierId, a -> a, (a1, a2) -> a1));

        List<SupplierRecommendationView> views = new ArrayList<>(rankedCandidates.size());
        for (SupplierRecommendationCandidate c : rankedCandidates) {
            SupplierAdvisoryItem adv = advisoryMap.get(c.supplierId());
            String headline = adv != null ? adv.headline() : defaultHeadline(c.recommendationTier());
            String explanation = adv != null && !adv.advisoryExplanation().isBlank()
                    ? adv.advisoryExplanation()
                    : defaultExplanation(c);

            // Re-link cashPayable and unitPrice from candidateInput
            CandidateInput origIn = candidateInputs.stream()
                    .filter(in -> in.supplierId().equals(c.supplierId()))
                    .findFirst()
                    .orElse(null);

            BigDecimal cashPayable = origIn != null ? origIn.quotedCashPayable() : null;

            views.add(new SupplierRecommendationView(
                    c.supplierId(),
                    c.supplierName(),
                    c.rank(),
                    c.recommendationTier(),
                    headline,
                    origIn != null ? origIn.quoteId() : null,
                    origIn != null ? origIn.quoteRevisionId() : null,
                    c.quotedUnitPrice(),
                    c.quotedEconomicLandedCost(),
                    cashPayable,
                    c.quotedPromisedDeliveryDate(),
                    c.meetsRequiredDeliveryDate(),
                    c.scorecard(),
                    c.verifiedStrengths(),
                    c.identifiedRisks(),
                    c.missingEvidence(),
                    explanation,
                    c.verifiedPerformerBadge(),
                    c.costDriftWarning(),
                    c.deliveryRiskWarning(),
                    c.isMaterialSpecificEvidence(),
                    c.suitabilityScore()
            ));
        }

        return new SupplierRecommendationList(
                rfqId,
                materialName,
                rfqQuantity,
                rfqUnit,
                requiredDeliveryAt,
                views,
                narrative.overallProcurementBrief(),
                narrative.aiProviderUsed(),
                narrative.aiGenerated()
        );
    }

    private RecommendationNarrative generateNarrativeWithFallback(RecommendationContext context) {
        if ("openai".equalsIgnoreCase(aiConfig.getProvider())) {
            try {
                return openAiProvider.generateNarrative(context);
            } catch (Exception e) {
                log.warn("OpenAI recommendation narrative generation failed; falling back to deterministic mock provider: {}", e.getMessage());
                RecommendationNarrative fallback = mockProvider.generateNarrative(context);
                return new RecommendationNarrative(
                        fallback.overallProcurementBrief() + " (Offline Rule-Based Advisory Fallback: OpenAI service temporarily unavailable)",
                        fallback.supplierAdvisories(),
                        "offline-fallback",
                        false
                );
            }
        }
        return mockProvider.generateNarrative(context);
    }

    private String defaultHeadline(String tier) {
        return switch (tier) {
            case "RECOMMENDED" -> "Primary Recommendation — Strong Contract OTD & Quality";
            case "VIABLE_ALTERNATIVE" -> "Viable Commercial Alternative";
            case "CAUTION_RISK" -> "Caution Advised — Historical Variance or Lead-Time Risk";
            default -> "Insufficient Historical Evidence — Unrated Track Record";
        };
    }

    private String defaultExplanation(SupplierRecommendationCandidate c) {
        if ("RECOMMENDED".equals(c.recommendationTier())) {
            return c.supplierName() + " demonstrated strong empirical delivery and quality outcomes.";
        } else if ("INSUFFICIENT_EVIDENCE".equals(c.recommendationTier())) {
            return "No verified delivery history on record for " + c.supplierName() + ". Treat quote as unrated sample.";
        } else if ("CAUTION_RISK".equals(c.recommendationTier())) {
            return c.supplierName() + " exhibits cost overrun drift or delivery delay risks.";
        }
        return c.supplierName() + " provides a viable alternative quotation.";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(Object val) {
        if (val == null) return Collections.emptyMap();
        try {
            return json.readValue(val.toString(), Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private BigDecimal parseBigDecimal(Object val) {
        if (val == null) return null;
        try {
            return new BigDecimal(val.toString().trim().replaceAll("[^0-9.-]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private OffsetDateTime parseOffsetDateTime(Object val) {
        if (val == null) return null;
        try {
            return OffsetDateTime.parse(val.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }
}
