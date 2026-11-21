package org.munim.intelligence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.munim.intelligence.SupplierPerformanceDtos.SupplierScorecardView;
import org.munim.intelligence.SupplierRecommendationDtos.SupplierRecommendationCandidate;
import org.munim.intelligence.SupplierRecommendationEngine.CandidateInput;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SupplierRecommendationEngineTest — Deterministic Ranking & Evidence Engine")
class SupplierRecommendationEngineTest {

    private SupplierRecommendationEngine engine;
    private OffsetDateTime deadline;

    @BeforeEach
    void setup() {
        engine = new SupplierRecommendationEngine();
        deadline = OffsetDateTime.now().plusDays(5);
    }

    private SupplierScorecardView buildScorecard(
            UUID supplierId,
            String name,
            String tier,
            int eligiblePo,
            BigDecimal otd,
            BigDecimal delayDays,
            BigDecimal gateAccept,
            BigDecimal yield,
            BigDecimal costVariance
    ) {
        return new SupplierScorecardView(
                supplierId,
                name,
                otd,
                eligiblePo,
                eligiblePo > 0 ? (int) Math.round(eligiblePo * (otd != null ? otd.doubleValue() / 100.0 : 1.0)) : 0,
                eligiblePo > 0 ? (int) Math.round(eligiblePo * (otd != null ? (100.0 - otd.doubleValue()) / 100.0 : 0.0)) : 0,
                0,
                delayDays,
                eligiblePo * 2,
                otd,
                new BigDecimal("1.5"),
                new BigDecimal("20.00"),
                gateAccept,
                yield,
                BigDecimal.ZERO,
                new BigDecimal("10000.0000"),
                costVariance,
                new BigDecimal("500000.00"),
                tier,
                "Test explanation"
        );
    }

    @Test
    @DisplayName("1. Empty inputs returns empty recommendation list")
    void testEmptyInputsReturnsEmpty() {
        List<SupplierRecommendationCandidate> res = engine.rankAndEvaluate(deadline, Collections.emptyList());
        assertNotNull(res);
        assertTrue(res.isEmpty());
    }

    @Test
    @DisplayName("2. Verified performer with HIGH confidence and on-time promise ranks #1")
    void testVerifiedPerformerRanksFirst() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();

        // S1: High confidence, 98% OTD, 99% gate acceptance, landed cost ₹50,000
        SupplierScorecardView sc1 = buildScorecard(s1, "High Reliability Vendor", "HIGH", 12,
                new BigDecimal("98.00"), new BigDecimal("0.5"), new BigDecimal("99.50"), new BigDecimal("99.00"), new BigDecimal("-1.00"));

        // S2: Unrated vendor, cheaper landed cost ₹45,000, but 0 historical orders
        SupplierScorecardView sc2 = buildScorecard(s2, "Unknown Cheap Vendor", "UNRATED", 0,
                null, null, null, null, null);

        CandidateInput c1 = new CandidateInput(s1, "High Reliability Vendor", UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.00"), new BigDecimal("50000.00"), new BigDecimal("59000.00"), deadline.minusDays(1), sc1, sc1);

        CandidateInput c2 = new CandidateInput(s2, "Unknown Cheap Vendor", UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("90.00"), new BigDecimal("45000.00"), new BigDecimal("53100.00"), deadline.minusDays(1), sc2, sc2);

        List<SupplierRecommendationCandidate> ranked = engine.rankAndEvaluate(deadline, List.of(c1, c2));

        assertEquals(2, ranked.size());
        SupplierRecommendationCandidate top = ranked.get(0);
        assertEquals(s1, top.supplierId());
        assertEquals(1, top.rank());
        assertEquals("RECOMMENDED", top.recommendationTier());
        assertTrue(top.verifiedPerformerBadge(), "Expected Verified Performer badge for HIGH confidence + >=95% OTD");
        assertFalse(top.costDriftWarning());
        assertFalse(top.deliveryRiskWarning());

        SupplierRecommendationCandidate second = ranked.get(1);
        assertEquals(s2, second.supplierId());
        assertEquals(2, second.rank());
        assertEquals("INSUFFICIENT_EVIDENCE", second.recommendationTier());
        assertFalse(second.verifiedPerformerBadge());
        assertTrue(second.missingEvidence().stream().anyMatch(m -> m.contains("Zero verified purchase orders")));
    }

    @Test
    @DisplayName("3. Delivery promise after deadline triggers deliveryRiskWarning and ranks lower")
    void testDeliveryPromiseLateTriggersWarning() {
        UUID s1 = UUID.randomUUID();
        SupplierScorecardView sc1 = buildScorecard(s1, "Late Promising Vendor", "MEDIUM", 5,
                new BigDecimal("90.00"), new BigDecimal("1.0"), new BigDecimal("98.00"), new BigDecimal("97.50"), BigDecimal.ZERO);

        CandidateInput c1 = new CandidateInput(s1, "Late Promising Vendor", UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("95.00"), new BigDecimal("48000.00"), new BigDecimal("56640.00"), deadline.plusDays(2), sc1, sc1);

        List<SupplierRecommendationCandidate> ranked = engine.rankAndEvaluate(deadline, List.of(c1));

        assertEquals(1, ranked.size());
        SupplierRecommendationCandidate top = ranked.getFirst();
        assertEquals(Boolean.FALSE, top.meetsRequiredDeliveryDate());
        assertTrue(top.deliveryRiskWarning());
        assertEquals("CAUTION_RISK", top.recommendationTier());
        assertTrue(top.identifiedRisks().stream().anyMatch(r -> r.contains("Quoted delivery promise is after")));
    }

    @Test
    @DisplayName("4. Cost drift warning triggered when historical cost variance exceeds +5%")
    void testCostDriftWarningTriggered() {
        UUID s1 = UUID.randomUUID();
        SupplierScorecardView sc1 = buildScorecard(s1, "Surprise Surcharge Vendor", "MEDIUM", 6,
                new BigDecimal("95.00"), new BigDecimal("1.0"), new BigDecimal("98.00"), new BigDecimal("98.00"), new BigDecimal("7.50"));

        CandidateInput c1 = new CandidateInput(s1, "Surprise Surcharge Vendor", UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("95.00"), new BigDecimal("48000.00"), new BigDecimal("56640.00"), deadline.minusDays(1), sc1, sc1);

        List<SupplierRecommendationCandidate> ranked = engine.rankAndEvaluate(deadline, List.of(c1));

        SupplierRecommendationCandidate res = ranked.getFirst();
        assertTrue(res.costDriftWarning());
        assertEquals("CAUTION_RISK", res.recommendationTier());
        assertTrue(res.identifiedRisks().stream().anyMatch(r -> r.contains("cost overrun drift")));
    }

    @Test
    @DisplayName("5. Material-specific scorecard preferred over overall when material evidence exists")
    void testMaterialSpecificScorecardPreferred() {
        UUID s1 = UUID.randomUUID();

        // Specific material scorecard: 100% OTD, 5 POs
        SupplierScorecardView matScorecard = buildScorecard(s1, "Multi Material Vendor", "MEDIUM", 5,
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("99.00"), new BigDecimal("99.00"), BigDecimal.ZERO);

        // Overall scorecard across all materials: 80% OTD, 20 POs
        SupplierScorecardView overallScorecard = buildScorecard(s1, "Multi Material Vendor", "HIGH", 20,
                new BigDecimal("80.00"), new BigDecimal("3.0"), new BigDecimal("95.00"), new BigDecimal("94.00"), new BigDecimal("3.00"));

        CandidateInput c1 = new CandidateInput(s1, "Multi Material Vendor", UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.00"), new BigDecimal("50000.00"), new BigDecimal("59000.00"), deadline.minusDays(1),
                matScorecard, overallScorecard);

        List<SupplierRecommendationCandidate> ranked = engine.rankAndEvaluate(deadline, List.of(c1));

        SupplierRecommendationCandidate res = ranked.getFirst();
        assertEquals(matScorecard, res.scorecard(), "Expected material-specific scorecard to be used");
        assertTrue(res.isMaterialSpecificEvidence());
        assertFalse(res.missingEvidence().stream().anyMatch(m -> m.contains("Cross-material factory history")));
    }

    @Test
    @DisplayName("6. Missing material evidence falls back to overall scorecard with explicit missing note and no verified performer badge")
    void testMissingMaterialEvidenceNotesFallback() {
        UUID s1 = UUID.randomUUID();

        // Specific material scorecard: 0 POs (unrated for this material)
        SupplierScorecardView matScorecard = buildScorecard(s1, "General Vendor", "UNRATED", 0,
                null, null, null, null, null);

        // Overall scorecard: 10 POs, HIGH confidence
        SupplierScorecardView overallScorecard = buildScorecard(s1, "General Vendor", "HIGH", 10,
                new BigDecimal("96.00"), new BigDecimal("0.5"), new BigDecimal("98.50"), new BigDecimal("98.00"), BigDecimal.ZERO);

        CandidateInput c1 = new CandidateInput(s1, "General Vendor", UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.00"), new BigDecimal("50000.00"), new BigDecimal("59000.00"), deadline.minusDays(1),
                matScorecard, overallScorecard);

        List<SupplierRecommendationCandidate> ranked = engine.rankAndEvaluate(deadline, List.of(c1));

        SupplierRecommendationCandidate res = ranked.getFirst();
        assertEquals(overallScorecard, res.scorecard(), "Expected overall scorecard fallback");
        assertFalse(res.isMaterialSpecificEvidence(), "Evidence should not be flagged as material-specific");
        assertFalse(res.verifiedPerformerBadge(), "Verified Performer badge must never be granted for cross-material fallback");
        assertTrue(res.missingEvidence().stream().anyMatch(m -> m.contains("Cross-material factory history used")));
    }

    @Test
    @DisplayName("7. Unrated supplier with cheaper quote cannot outrank verified supplier with good track record")
    void testUnratedSupplierCannotOutrankVerifiedSupplier() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();

        // S1: Verified supplier on this material: MEDIUM confidence, 92% OTD, 96% yield, Landed Cost ₹50,000
        SupplierScorecardView sc1 = buildScorecard(s1, "Verified Reliable Steel Ltd", "MEDIUM", 4,
                new BigDecimal("92.00"), new BigDecimal("0.5"), new BigDecimal("98.00"), new BigDecimal("96.00"), new BigDecimal("0.00"));

        // S2: Completely unrated supplier, significantly cheaper landed cost ₹35,000 (30% cheaper!)
        SupplierScorecardView sc2 = buildScorecard(s2, "Untested Cheap Vendor", "UNRATED", 0,
                null, null, null, null, null);

        CandidateInput c1 = new CandidateInput(s1, "Verified Reliable Steel Ltd", UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100.00"), new BigDecimal("50000.00"), new BigDecimal("59000.00"), deadline.minusDays(1), sc1, sc1);

        CandidateInput c2 = new CandidateInput(s2, "Untested Cheap Vendor", UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("70.00"), new BigDecimal("35000.00"), new BigDecimal("41300.00"), deadline.minusDays(1), sc2, sc2);

        List<SupplierRecommendationCandidate> ranked = engine.rankAndEvaluate(deadline, List.of(c1, c2));

        assertEquals(2, ranked.size());
        // Verified supplier with good empirical track record must rank #1 over unrated supplier despite price difference
        assertEquals(s1, ranked.get(0).supplierId(), "Verified supplier must rank #1");
        assertEquals("RECOMMENDED", ranked.get(0).recommendationTier());
        assertTrue(ranked.get(0).isMaterialSpecificEvidence());

        // Unrated supplier must rank #2 and be tagged INSUFFICIENT_EVIDENCE
        assertEquals(s2, ranked.get(1).supplierId(), "Untested cheap vendor must rank #2");
        assertEquals("INSUFFICIENT_EVIDENCE", ranked.get(1).recommendationTier());
        assertFalse(ranked.get(1).isMaterialSpecificEvidence());
        assertTrue(ranked.get(1).suitabilityScore().compareTo(new BigDecimal("45.00")) <= 0, "Unrated score must be strictly bounded without artificial default points");
    }
}
