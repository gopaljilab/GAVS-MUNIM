package org.munim.ai;

import org.munim.intelligence.SupplierRecommendationDtos.RecommendationContext;
import org.munim.intelligence.SupplierRecommendationDtos.RecommendationNarrative;
import org.munim.intelligence.SupplierRecommendationDtos.SupplierAdvisoryItem;
import org.munim.intelligence.SupplierRecommendationDtos.SupplierRecommendationCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MockRecommendationProvider implements SupplierRecommendationAiProvider {

    private final MunimAiConfig config;

    public MockRecommendationProvider(MunimAiConfig config) {
        this.config = config;
    }

    @Override
    public String getProviderName() {
        return "mock-rule-based";
    }

    @Override
    public RecommendationNarrative generateNarrative(RecommendationContext context) {
        if (context.candidates() == null || context.candidates().isEmpty()) {
            return new RecommendationNarrative(
                    "No suppliers have submitted verified quotations for this RFQ requirement (" +
                            context.materialName() + ", " + context.quantity() + " " + context.unit() + ").",
                    List.of(),
                    getProviderName(),
                    false
            );
        }

        List<SupplierAdvisoryItem> items = new ArrayList<>();
        SupplierRecommendationCandidate topCandidate = context.candidates().getFirst();

        StringBuilder briefBuilder = new StringBuilder();
        briefBuilder.append(String.format(
                "Procurement advisory evaluated %d supplier quote(s) for %s %s of %s. ",
                context.candidates().size(),
                context.quantity() != null ? context.quantity().toPlainString() : "0",
                context.unit() != null ? context.unit() : "",
                context.materialName() != null ? context.materialName() : "specified material"
        ));

        if ("RECOMMENDED".equals(topCandidate.recommendationTier())) {
            briefBuilder.append(String.format(
                    "Leading recommendation is %s based on strong empirical delivery and quality outcomes. ",
                    topCandidate.supplierName()
            ));
        } else if ("INSUFFICIENT_EVIDENCE".equals(topCandidate.recommendationTier())) {
            briefBuilder.append(String.format(
                    "Top-ranked option is %s, but confidence is unrated due to lack of historical receipt data. Owner discretion advised. ",
                    topCandidate.supplierName()
            ));
        } else {
            briefBuilder.append(String.format(
                    "%s ranks highest on quote economics, though caution flags should be reviewed prior to award. ",
                    topCandidate.supplierName()
            ));
        }

        if (context.candidates().size() > 1) {
            briefBuilder.append("Trade-offs between quoted pricing and verified historical performance are detailed below.");
        }

        for (SupplierRecommendationCandidate c : context.candidates()) {
            String headline;
            StringBuilder explanation = new StringBuilder();

            if ("RECOMMENDED".equals(c.recommendationTier())) {
                headline = "Primary Recommendation — Strong Contract OTD & Quality Track Record";
                explanation.append(String.format(
                        "%s offers a competitive quotation with demonstrated historical compliance. ",
                        c.supplierName()
                ));
                if (c.scorecard() != null && c.scorecard().contractOtdRate() != null) {
                    explanation.append(String.format(
                            "Verified Contract OTD stands at %s%% across %d order(s). ",
                            c.scorecard().contractOtdRate().toPlainString(),
                            c.scorecard().eligiblePoCount()
                    ));
                }
                if (c.verifiedPerformerBadge()) {
                    explanation.append("Earned Verified Performer status for consistent high delivery and gate acceptance rates. ");
                }
            } else if ("VIABLE_ALTERNATIVE".equals(c.recommendationTier())) {
                headline = "Viable Commercial Alternative";
                explanation.append(String.format(
                        "%s presents a viable alternative option. ",
                        c.supplierName()
                ));
                if (c.quotedEconomicLandedCost() != null) {
                    explanation.append("Provides competitive quote terms, though historical performance rank is secondary. ");
                }
            } else if ("CAUTION_RISK".equals(c.recommendationTier())) {
                headline = "Caution Advised — Historical Variances or Lead-Time Risk";
                explanation.append(String.format(
                        "%s exhibits operational risk factors that require careful review. ",
                        c.supplierName()
                ));
                if (c.costDriftWarning()) {
                    explanation.append("Historical landed costs frequently exceed quoted baselines (cost drift detected). ");
                }
                if (c.deliveryRiskWarning()) {
                    explanation.append("Historical delivery track record reflects recurring delays or promised delivery exceeds deadline. ");
                }
            } else {
                headline = "Insufficient Historical Evidence — Unrated Track Record";
                explanation.append(String.format(
                        "No verified delivery receipts or inspections are on record for %s. ",
                        c.supplierName()
                ));
                explanation.append("Quoted rates are unverified against actual factory outcomes; treat as new vendor sample. ");
            }

            if (Boolean.FALSE.equals(c.meetsRequiredDeliveryDate())) {
                explanation.append("WARNING: Quoted delivery date is AFTER required procurement deadline. ");
            }

            if (c.missingEvidence() != null && !c.missingEvidence().isEmpty()) {
                explanation.append("Missing evidence: ").append(String.join("; ", c.missingEvidence())).append(". ");
            }

            items.add(new SupplierAdvisoryItem(
                    c.supplierId(),
                    headline,
                    explanation.toString().trim()
            ));
        }

        return new RecommendationNarrative(
                briefBuilder.toString().trim(),
                items,
                getProviderName(),
                false
        );
    }
}
