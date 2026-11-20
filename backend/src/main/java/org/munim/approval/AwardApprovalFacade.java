package org.munim.approval;

import org.munim.api.ApiDtos.ApprovalView;
import org.munim.api.ApiDtos.DecisionCreate;
import org.munim.intelligence.SupplierIntelligenceService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AwardApprovalFacade {
    private final ApprovalService approvalService;
    private final SupplierIntelligenceService supplierIntelligenceService;
    private final JdbcTemplate db;

    public AwardApprovalFacade(ApprovalService approvalService,
                               SupplierIntelligenceService supplierIntelligenceService,
                               JdbcTemplate db) {
        this.approvalService = approvalService;
        this.supplierIntelligenceService = supplierIntelligenceService;
        this.db = db;
    }

    @Transactional(rollbackFor = Exception.class)
    public ApprovalView decide(UUID proposalId, DecisionCreate in, String actorRole, String actorId) {
        // Query proposal type and linked entities
        var propRows = db.query(
                "SELECT cp.type, sq.rfq_id, cp.quote_revision_id " +
                        "FROM commercial_proposals cp " +
                        "LEFT JOIN supplier_quote_revisions sqr ON sqr.id = cp.quote_revision_id " +
                        "LEFT JOIN supplier_quotes sq ON sq.id = sqr.supplier_quote_id " +
                        "WHERE cp.id = ?",
                (rs, n) -> new Object[]{
                        rs.getString("type"),
                        rs.getObject("rfq_id", UUID.class),
                        rs.getObject("quote_revision_id", UUID.class)
                },
                proposalId
        );

        // Execute frozen approval decision
        ApprovalView result = approvalService.decide(proposalId, in, actorRole, actorId);

        // If SUPPLIER_AWARD and APPROVED, create Purchase Order in the same transaction
        if (!propRows.isEmpty()) {
            Object[] row = propRows.getFirst();
            String type = (String) row[0];
            UUID rfqId = (UUID) row[1];
            UUID quoteRevId = (UUID) row[2];

            if ("SUPPLIER_AWARD".equals(type) && ("APPROVE".equalsIgnoreCase(result.decision()) || "APPROVED".equalsIgnoreCase(result.decision())) && rfqId != null && quoteRevId != null) {
                supplierIntelligenceService.createPurchaseOrderForAwardedRfq(rfqId, proposalId, quoteRevId, actorId != null ? actorId : actorRole);
            }
        }

        return result;
    }
}
