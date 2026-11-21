package org.munim.intelligence;

import org.munim.intelligence.SupplierPerformanceDtos.SupplierScorecardView;
import org.munim.intelligence.SupplierRecommendationDtos.SupplierRecommendationCandidate;
import org.munim.intelligence.SupplierRecommendationDtos.SupplierRecommendationView;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Component
public class SupplierRecommendationEngine {

    public record CandidateInput(
            UUID supplierId,
            String supplierName,
            UUID quoteId,
            UUID quoteRevisionId,
            BigDecimal quotedUnitPrice,
            BigDecimal quotedEconomicLandedCost,
            BigDecimal quotedCashPayable,
            OffsetDateTime quotedPromisedDeliveryDate,
            SupplierScorecardView materialScorecard,
            SupplierScorecardView overallScorecard
    ) {}

    public List<SupplierRecommendationCandidate> rankAndEvaluate(
            OffsetDateTime requiredDeliveryAt,
            List<CandidateInput> inputs
    ) {
        if (inputs == null || inputs.isEmpty()) {
            return Collections.emptyList();
        }

        // Find min landed cost among quoted candidates for relative price normalization
        BigDecimal minLandedCost = inputs.stream()
                .map(CandidateInput::quotedEconomicLandedCost)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);

        List<ScoredCandidate> scored = new ArrayList<>();

        for (CandidateInput in : inputs) {
            // Determine primary scorecard (prefer material-specific if it has evidence; otherwise overall)
            boolean hasMaterialEvidence = in.materialScorecard() != null && in.materialScorecard().eligiblePoCount() > 0;
            SupplierScorecardView activeScorecard = hasMaterialEvidence ? in.materialScorecard() : in.overallScorecard();

            Boolean meetsDelivery = null;
            if (in.quotedPromisedDeliveryDate() != null && requiredDeliveryAt != null) {
                meetsDelivery = !in.quotedPromisedDeliveryDate().isAfter(requiredDeliveryAt);
            }

            // Structured evidence detection
            List<String> strengths = new ArrayList<>();
            List<String> risks = new ArrayList<>();
            List<String> missing = new ArrayList<>();

            boolean verifiedPerformer = false;
            boolean costDriftWarning = false;
            boolean deliveryRiskWarning = false;

            if (activeScorecard != null) {
                if (activeScorecard.contractOtdRate() != null) {
                    if (activeScorecard.contractOtdRate().compareTo(new BigDecimal("90.00")) >= 0) {
                        strengths.add(String.format("High Contract OTD (%s%%) across %d verified order(s)",
                                activeScorecard.contractOtdRate().setScale(1, RoundingMode.HALF_UP),
                                activeScorecard.eligiblePoCount()));
                    } else if (activeScorecard.contractOtdRate().compareTo(new BigDecimal("80.00")) < 0) {
                        risks.add(String.format("Historical delivery reliability below benchmark (%s%% Contract OTD)",
                                activeScorecard.contractOtdRate().setScale(1, RoundingMode.HALF_UP)));
                        deliveryRiskWarning = true;
                    }
                }

                if (activeScorecard.averagePoDelayDays() != null &&
                        activeScorecard.averagePoDelayDays().compareTo(new BigDecimal("2.0")) > 0) {
                    risks.add(String.format("Average delivery delay on late orders is %s days",
                            activeScorecard.averagePoDelayDays().setScale(1, RoundingMode.HALF_UP)));
                    deliveryRiskWarning = true;
                }

                if (activeScorecard.gateAcceptanceRate() != null &&
                        activeScorecard.gateAcceptanceRate().compareTo(new BigDecimal("98.00")) >= 0) {
                    strengths.add(String.format("High gate inspection acceptance (%s%%)",
                            activeScorecard.gateAcceptanceRate().setScale(1, RoundingMode.HALF_UP)));
                }

                if (activeScorecard.netUsableYieldRate() != null &&
                        activeScorecard.netUsableYieldRate().compareTo(new BigDecimal("98.00")) >= 0) {
                    strengths.add(String.format("High net usable manufacturing yield (%s%%)",
                            activeScorecard.netUsableYieldRate().setScale(1, RoundingMode.HALF_UP)));
                }

                if (activeScorecard.latentDefectRate() != null &&
                        activeScorecard.latentDefectRate().compareTo(new BigDecimal("2.00")) > 0) {
                    risks.add(String.format("Elevated post-inspection latent defect rate (%s%%)",
                            activeScorecard.latentDefectRate().setScale(1, RoundingMode.HALF_UP)));
                }

                if (activeScorecard.costVariancePercentage() != null) {
                    if (activeScorecard.costVariancePercentage().compareTo(BigDecimal.ZERO) < 0) {
                        strengths.add(String.format("Landed costs historically came in %s%% below baseline",
                                activeScorecard.costVariancePercentage().abs().setScale(1, RoundingMode.HALF_UP)));
                    } else if (activeScorecard.costVariancePercentage().compareTo(new BigDecimal("5.00")) > 0) {
                        risks.add(String.format("Historical landed cost overrun drift of +%s%% above baseline",
                                activeScorecard.costVariancePercentage().setScale(1, RoundingMode.HALF_UP)));
                        costDriftWarning = true;
                    }
                }

                // Verified Performer Badge: requires material-specific history, HIGH confidence, OTD >= 95%, Gate Acceptance >= 98%
                if (hasMaterialEvidence &&
                        "HIGH".equalsIgnoreCase(activeScorecard.confidenceTier()) &&
                        activeScorecard.contractOtdRate() != null && activeScorecard.contractOtdRate().compareTo(new BigDecimal("95.00")) >= 0 &&
                        activeScorecard.gateAcceptanceRate() != null && activeScorecard.gateAcceptanceRate().compareTo(new BigDecimal("98.00")) >= 0) {
                    verifiedPerformer = true;
                    strengths.add("MUNIM Verified Performer: Exceptional verified delivery and quality track record on this material");
                }

                // Missing evidence tracking
                if (!hasMaterialEvidence) {
                    if (in.overallScorecard() != null && in.overallScorecard().eligiblePoCount() > 0) {
                        missing.add("Contextual Only: Cross-material factory history used. Zero verified purchase orders on record for this specific material");
                    } else {
                        missing.add("Zero verified purchase orders on record");
                    }
                } else if (activeScorecard.eligiblePoCount() == 0) {
                    missing.add("Zero verified purchase orders on record");
                }
                if (activeScorecard.consignmentCount() == 0) {
                    missing.add("No verified delivery gate arrivals recorded");
                }
                if (activeScorecard.inspectedQuantityTotal() == null || activeScorecard.inspectedQuantityTotal().compareTo(BigDecimal.ZERO) == 0) {
                    missing.add("No physical quality inspection records");
                }
            } else {
                missing.add("No historical performance record available for supplier");
            }

            if (Boolean.FALSE.equals(meetsDelivery)) {
                risks.add("Quoted delivery promise is after required procurement delivery deadline");
                deliveryRiskWarning = true;
            }

            // Calculate deterministic suitability score (0.00 to 100.00)
            BigDecimal suitabilityScore = calculateSuitability(in, activeScorecard, hasMaterialEvidence, minLandedCost, meetsDelivery);

            scored.add(new ScoredCandidate(
                    in,
                    activeScorecard,
                    hasMaterialEvidence,
                    meetsDelivery,
                    verifiedPerformer,
                    costDriftWarning,
                    deliveryRiskWarning,
                    strengths,
                    risks,
                    missing,
                    suitabilityScore
            ));
        }

        // Sort deterministically:
        // 1. Meets delivery deadline (true first, then null, then false)
        // 2. Suitability score descending
        // 3. Quoted landed cost ascending
        // 4. Supplier name ascending
        scored.sort((a, b) -> {
            int dlComp = compareDeliveryCompliance(a.meetsDelivery, b.meetsDelivery);
            if (dlComp != 0) return dlComp;

            int scoreComp = b.suitabilityScore.compareTo(a.suitabilityScore);
            if (scoreComp != 0) return scoreComp;

            if (a.input.quotedEconomicLandedCost() != null && b.input.quotedEconomicLandedCost() != null) {
                int costComp = a.input.quotedEconomicLandedCost().compareTo(b.input.quotedEconomicLandedCost());
                if (costComp != 0) return costComp;
            } else if (a.input.quotedEconomicLandedCost() != null) {
                return -1;
            } else if (b.input.quotedEconomicLandedCost() != null) {
                return 1;
            }

            return a.input.supplierName().compareToIgnoreCase(b.input.supplierName());
        });

        // Assign ranks and recommendation tiers
        List<SupplierRecommendationCandidate> result = new ArrayList<>(scored.size());
        for (int i = 0; i < scored.size(); i++) {
            ScoredCandidate sc = scored.get(i);
            int rank = i + 1;

            String tier;
            boolean isUnrated = sc.scorecard == null || "UNRATED".equalsIgnoreCase(sc.scorecard.confidenceTier()) || sc.scorecard.eligiblePoCount() == 0;

            if (sc.deliveryRiskWarning || sc.costDriftWarning) {
                tier = "CAUTION_RISK";
            } else if (isUnrated) {
                tier = "INSUFFICIENT_EVIDENCE";
            } else if (rank == 1) {
                tier = "RECOMMENDED";
            } else {
                tier = "VIABLE_ALTERNATIVE";
            }

            result.add(new SupplierRecommendationCandidate(
                    sc.input.supplierId(),
                    sc.input.supplierName(),
                    rank,
                    tier,
                    sc.input.quotedUnitPrice(),
                    sc.input.quotedEconomicLandedCost(),
                    sc.input.quotedPromisedDeliveryDate(),
                    sc.meetsDelivery,
                    sc.scorecard,
                    sc.strengths,
                    sc.risks,
                    sc.missing,
                    sc.verifiedPerformer,
                    sc.costDriftWarning,
                    sc.deliveryRiskWarning,
                    sc.hasMaterialEvidence,
                    sc.suitabilityScore
            ));
        }

        return result;
    }

    private int compareDeliveryCompliance(Boolean a, Boolean b) {
        if (Objects.equals(a, b)) return 0;
        if (Boolean.TRUE.equals(a)) return -1;
        if (Boolean.TRUE.equals(b)) return 1;
        if (a == null) return -1;
        if (b == null) return 1;
        return 0;
    }

    private BigDecimal calculateSuitability(
            CandidateInput in,
            SupplierScorecardView sc,
            boolean hasMaterialEvidence,
            BigDecimal minLandedCost,
            Boolean meetsDelivery
    ) {
        // Suitability is composed of 4 deterministic pillars:
        // Pillar 1: Price competitiveness (35%)
        // Pillar 2: Delivery compliance (30%)
        // Pillar 3: Quality yield (20%)
        // Pillar 4: Confidence volume (15%)

        BigDecimal priceScore;
        if (in.quotedEconomicLandedCost() != null && minLandedCost != null && minLandedCost.compareTo(BigDecimal.ZERO) > 0) {
            // minLanded / thisLanded * 35.0
            priceScore = minLandedCost.multiply(new BigDecimal("35.0"))
                    .divide(in.quotedEconomicLandedCost(), 2, RoundingMode.HALF_UP);
        } else {
            priceScore = new BigDecimal("17.5"); // Neutral
        }

        BigDecimal deliveryScore;
        BigDecimal promisePts = Boolean.TRUE.equals(meetsDelivery) ? new BigDecimal("5.0") : BigDecimal.ZERO;
        if (sc != null && sc.contractOtdRate() != null) {
            BigDecimal otdPts = sc.contractOtdRate().multiply(new BigDecimal("0.25")); // Up to 25 pts
            if (!hasMaterialEvidence) {
                otdPts = otdPts.multiply(new BigDecimal("0.50")); // Cross-material delivery discounted 50%
            }
            deliveryScore = otdPts.add(promisePts);
        } else {
            // Unrated delivery: zero historical delivery points; only current promise points
            deliveryScore = promisePts;
        }

        BigDecimal qualityScore;
        if (sc != null && sc.gateAcceptanceRate() != null) {
            BigDecimal gate = sc.gateAcceptanceRate().multiply(new BigDecimal("0.10"));
            BigDecimal yield = (sc.netUsableYieldRate() != null ? sc.netUsableYieldRate() : sc.gateAcceptanceRate())
                    .multiply(new BigDecimal("0.10"));
            BigDecimal qPts = gate.add(yield);
            if (!hasMaterialEvidence) {
                qPts = qPts.multiply(new BigDecimal("0.50")); // Cross-material quality discounted 50%
            }
            qualityScore = qPts;
        } else {
            // Unrated quality: zero historical quality points (no artificial defaults!)
            qualityScore = BigDecimal.ZERO;
        }

        BigDecimal confidenceScore;
        if (sc != null && sc.confidenceTier() != null) {
            BigDecimal conf = switch (sc.confidenceTier().toUpperCase()) {
                case "HIGH" -> new BigDecimal("15.0");
                case "MEDIUM" -> new BigDecimal("10.0");
                case "LOW" -> new BigDecimal("5.0");
                default -> BigDecimal.ZERO; // UNRATED gets 0.0 pts (no artificial points!)
            };
            if (!hasMaterialEvidence) {
                conf = conf.min(new BigDecimal("5.0")); // Cross-material confidence capped at LOW (5.0 pts)
            }
            confidenceScore = conf;
        } else {
            confidenceScore = BigDecimal.ZERO; // Unrated gets 0.0 pts
        }

        BigDecimal total = priceScore.add(deliveryScore).add(qualityScore).add(confidenceScore);
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static class ScoredCandidate {
        final CandidateInput input;
        final SupplierScorecardView scorecard;
        final boolean hasMaterialEvidence;
        final Boolean meetsDelivery;
        final boolean verifiedPerformer;
        final boolean costDriftWarning;
        final boolean deliveryRiskWarning;
        final List<String> strengths;
        final List<String> risks;
        final List<String> missing;
        final BigDecimal suitabilityScore;

        ScoredCandidate(
                CandidateInput input,
                SupplierScorecardView scorecard,
                boolean hasMaterialEvidence,
                Boolean meetsDelivery,
                boolean verifiedPerformer,
                boolean costDriftWarning,
                boolean deliveryRiskWarning,
                List<String> strengths,
                List<String> risks,
                List<String> missing,
                BigDecimal suitabilityScore
        ) {
            this.input = input;
            this.scorecard = scorecard;
            this.hasMaterialEvidence = hasMaterialEvidence;
            this.meetsDelivery = meetsDelivery;
            this.verifiedPerformer = verifiedPerformer;
            this.costDriftWarning = costDriftWarning;
            this.deliveryRiskWarning = deliveryRiskWarning;
            this.strengths = strengths;
            this.risks = risks;
            this.missing = missing;
            this.suitabilityScore = suitabilityScore;
        }
    }
}
