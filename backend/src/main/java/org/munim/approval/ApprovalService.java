package org.munim.approval;

import tools.jackson.databind.ObjectMapper;
import org.munim.api.ApiDtos.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ApprovalService {
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public ApprovalService(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    @Transactional
    public ProposalView createAwardProposal(UUID evaluationId, AwardProposalCreate in, String actorRole, String actorId) {
        // 1. Verify evaluation exists and is COMPLETE
        var evalRows = db.query(
                "SELECT id, rfq_id, snapshot_sha256, status, calculation_version FROM quote_evaluations WHERE id = ?",
                (rs, n) -> new Object[]{
                        rs.getObject("id", UUID.class),
                        rs.getObject("rfq_id", UUID.class),
                        rs.getString("snapshot_sha256"),
                        rs.getString("status"),
                        rs.getString("calculation_version")
                },
                evaluationId
        );
        if (evalRows.isEmpty()) {
            throw new NoSuchElementException("Evaluation not found");
        }
        Object[] eval = evalRows.getFirst();
        UUID rfqId = (UUID) eval[1];
        String evalSnapshotHash = (String) eval[2];
        String evalStatus = (String) eval[3];

        if (!"COMPLETE".equals(evalStatus)) {
            throw new IllegalStateException("Cannot create award proposal for an incomplete evaluation");
        }
        if (!evalSnapshotHash.equals(in.expectedSnapshotHash())) {
            throw new IllegalStateException("Evaluation snapshot hash mismatch: expected " + in.expectedSnapshotHash() + " but was " + evalSnapshotHash);
        }

        // 2. Verify selected quote revision is part of this evaluation and eligible
        var lineRows = db.query(
                "SELECT el.id, el.material_base, el.material_gst, el.freight_base, el.freight_gst, el.economic_landed_cost, el.cash_payable, el.currency, " +
                        "sqr.supplier_quote_id, sqr.revision_no, sqr.raw_message, sqr.raw_message_sha256, sqr.fields, sqr.received_at, " +
                        "s.id as supplier_id, s.name as supplier_name, " +
                        "q.specification, q.quantity, q.unit, q.required_procurement_delivery_at " +
                        "FROM evaluation_lines el " +
                        "JOIN supplier_quote_revisions sqr ON sqr.id = el.quote_revision_id " +
                        "JOIN supplier_quotes sq ON sq.id = sqr.supplier_quote_id " +
                        "JOIN suppliers s ON s.id = sq.supplier_id " +
                        "JOIN rfqs q ON q.id = sq.rfq_id " +
                        "WHERE el.evaluation_id = ? AND el.quote_revision_id = ?",
                (rs, n) -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("lineId", rs.getObject("id", UUID.class));
                    map.put("materialBase", rs.getBigDecimal("material_base"));
                    map.put("materialGst", rs.getBigDecimal("material_gst"));
                    map.put("freightBase", rs.getBigDecimal("freight_base"));
                    map.put("freightGst", rs.getBigDecimal("freight_gst"));
                    map.put("economicLandedCost", rs.getBigDecimal("economic_landed_cost"));
                    map.put("cashPayable", rs.getBigDecimal("cash_payable"));
                    map.put("currency", rs.getString("currency"));
                    map.put("quoteId", rs.getObject("supplier_quote_id", UUID.class));
                    map.put("revisionNo", rs.getInt("revision_no"));
                    map.put("rawMessage", rs.getString("raw_message"));
                    map.put("rawMessageSha256", rs.getString("raw_message_sha256"));
                    map.put("fields", read(rs.getString("fields")));
                    map.put("receivedAt", rs.getObject("received_at", OffsetDateTime.class));
                    map.put("supplierId", rs.getObject("supplier_id", UUID.class));
                    map.put("supplierName", rs.getString("supplier_name"));
                    map.put("specification", rs.getString("specification"));
                    map.put("quantity", rs.getBigDecimal("quantity"));
                    map.put("unit", rs.getString("unit"));
                    map.put("requiredDeliveryAt", rs.getObject("required_procurement_delivery_at", OffsetDateTime.class));
                    return map;
                },
                evaluationId, in.quoteRevisionId()
        );
        if (lineRows.isEmpty()) {
            throw new IllegalArgumentException("Selected quote revision is not part of this evaluation");
        }
        Map<String, Object> data = lineRows.getFirst();

        // 3. Assemble complete commercial snapshot payload
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("proposalType", "SUPPLIER_AWARD");
        snapshot.put("evaluationId", evaluationId.toString());
        snapshot.put("evaluationSnapshotHash", evalSnapshotHash);
        snapshot.put("rfqId", rfqId.toString());
        snapshot.put("rfqSpecification", data.get("specification"));
        snapshot.put("rfqQuantity", ((BigDecimal) data.get("quantity")).toPlainString());
        snapshot.put("rfqUnit", data.get("unit"));
        snapshot.put("requiredDeliveryAt", data.get("requiredDeliveryAt") != null ? data.get("requiredDeliveryAt").toString() : null);
        snapshot.put("supplierId", data.get("supplierId").toString());
        snapshot.put("supplierName", data.get("supplierName"));
        snapshot.put("quoteId", data.get("quoteId").toString());
        snapshot.put("quoteRevisionId", in.quoteRevisionId().toString());
        snapshot.put("quoteRevisionNo", data.get("revisionNo"));
        snapshot.put("rawMessage", data.get("rawMessage"));
        snapshot.put("rawMessageSha256", data.get("rawMessageSha256"));
        snapshot.put("materialBase", ((BigDecimal) data.get("materialBase")).toPlainString());
        snapshot.put("materialGst", ((BigDecimal) data.get("materialGst")).toPlainString());
        snapshot.put("freightBase", ((BigDecimal) data.get("freightBase")).toPlainString());
        snapshot.put("freightGst", ((BigDecimal) data.get("freightGst")).toPlainString());
        // Compute next-best savings in Java if other quotes were evaluated
        List<BigDecimal> otherLandedCosts = db.query(
                "SELECT economic_landed_cost FROM evaluation_lines WHERE evaluation_id = ? AND quote_revision_id <> ? ORDER BY economic_landed_cost ASC",
                (rs, n) -> rs.getBigDecimal(1),
                evaluationId, in.quoteRevisionId()
        );
        BigDecimal currentLanded = (BigDecimal) data.get("economicLandedCost");
        BigDecimal savingsVsNextBest = null;
        if (!otherLandedCosts.isEmpty()) {
            BigDecimal nextBest = otherLandedCosts.getFirst();
            if (nextBest.compareTo(currentLanded) > 0) {
                savingsVsNextBest = nextBest.subtract(currentLanded);
            }
        }

        // Compute risk flag in Java based on tax / delivery attributes
        @SuppressWarnings("unchecked")
        Map<String, Object> quoteFields = (Map<String, Object>) data.get("fields");
        String itcStatus = quoteFields != null && quoteFields.get("materialItcStatus") != null
                ? quoteFields.get("materialItcStatus").toString()
                : "CREDITABLE";
        String riskFlag = null;
        if ("NON_CREDITABLE".equalsIgnoreCase(itcStatus)) {
            riskFlag = "Non-Creditable GST (ITC Lost)";
        }

        snapshot.put("economicLandedCost", ((BigDecimal) data.get("economicLandedCost")).toPlainString());
        snapshot.put("cashPayable", ((BigDecimal) data.get("cashPayable")).toPlainString());
        snapshot.put("currency", data.get("currency"));
        snapshot.put("savingsVsNextBest", savingsVsNextBest != null ? savingsVsNextBest.toPlainString() : null);
        snapshot.put("riskFlag", riskFlag);
        snapshot.put("policyVersion", 1);
        snapshot.put("quoteFields", data.get("fields"));

        String snapshotJson = write(snapshot);
        String snapshotHash = sha(snapshotJson);

        // 4. Persist commercial proposal
        UUID proposalId = db.queryForObject(
                "INSERT INTO commercial_proposals(" +
                        "type, entity_type, entity_id, evaluation_id, quote_revision_id, " +
                        "requested_action, requesting_user, status, " +
                        "snapshot_payload, snapshot_hash, policy_version" +
                        ") VALUES ('SUPPLIER_AWARD', 'EVALUATION', ?, ?, ?, 'AWARD_SUPPLIER', ?, 'PENDING', ?::jsonb, ?, 1) RETURNING id",
                UUID.class,
                evaluationId, evaluationId, in.quoteRevisionId(),
                actorRole, snapshotJson, snapshotHash
        );

        // 5. Append audit event
        db.update(
                "INSERT INTO audit_events(" +
                        "actor_id, actor_role, action, entity_type, entity_id, entity_version, " +
                        "reason, policy_version, after_hash, metadata" +
                        ") VALUES (?, ?, 'PROPOSAL_CREATED', 'PROPOSAL', ?, 0, 'Award proposal submitted for owner approval', 1, ?, ?::jsonb)",
                actorId != null ? actorId : actorRole, actorRole, proposalId, snapshotHash, write(Map.of("evaluationId", evaluationId.toString(), "quoteRevisionId", in.quoteRevisionId().toString()))
        );

        return new ProposalView(proposalId, "SUPPLIER_AWARD", "PENDING", snapshotHash, null);
    }

    @Transactional
    public ProposalView createCustomerQuoteProposal(UUID quotationId, String expectedSnapshotHash, String actorRole, String actorId) {
        // 1. Verify customer quotation exists and load joined data
        var quoteRows = db.query(
                "SELECT q.id, q.enquiry_id, q.costing_sheet_id, q.selling_price, q.output_gst_rate, q.customer_payable, " +
                        "q.valid_from, q.valid_until, q.payment_terms, q.margin_classification, q.approval_status, " +
                        "e.customer_name, e.customer_reference, " +
                        "cs.snapshot_sha256, cs.batch_quantity, cs.material_cost, cs.conversion_cost, cs.overhead_cost, " +
                        "cs.packaging_cost, cs.freight_cost, cs.total_cost, cs.target_margin, cs.target_price, " +
                        "cs.quoted_price, cs.realized_margin, cs.calculation_snapshot::text as calc_snapshot_text " +
                        "FROM customer_quotations q " +
                        "JOIN customer_enquiries e ON e.id = q.enquiry_id " +
                        "JOIN costing_sheets cs ON cs.id = q.costing_sheet_id " +
                        "WHERE q.id = ?",
                (rs, n) -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("quotationId", rs.getObject("id", UUID.class));
                    map.put("enquiryId", rs.getObject("enquiry_id", UUID.class));
                    map.put("costingSheetId", rs.getObject("costing_sheet_id", UUID.class));
                    map.put("sellingPrice", rs.getBigDecimal("selling_price"));
                    map.put("outputGstRate", rs.getBigDecimal("output_gst_rate"));
                    map.put("customerPayable", rs.getBigDecimal("customer_payable"));
                    map.put("validFrom", rs.getObject("valid_from", OffsetDateTime.class));
                    map.put("validUntil", rs.getObject("valid_until", OffsetDateTime.class));
                    map.put("paymentTerms", rs.getString("payment_terms"));
                    map.put("marginClassification", rs.getString("margin_classification"));
                    map.put("approvalStatus", rs.getString("approval_status"));
                    map.put("customerName", rs.getString("customer_name"));
                    map.put("customerReference", rs.getString("customer_reference"));
                    map.put("costingSheetSnapshotHash", rs.getString("snapshot_sha256"));
                    map.put("batchQuantity", rs.getBigDecimal("batch_quantity"));
                    map.put("materialCost", rs.getBigDecimal("material_cost"));
                    map.put("conversionCost", rs.getBigDecimal("conversion_cost"));
                    map.put("overheadCost", rs.getBigDecimal("overhead_cost"));
                    map.put("packagingCost", rs.getBigDecimal("packaging_cost"));
                    map.put("freightCost", rs.getBigDecimal("freight_cost"));
                    map.put("totalCost", rs.getBigDecimal("total_cost"));
                    map.put("targetMargin", rs.getBigDecimal("target_margin"));
                    map.put("targetPrice", rs.getBigDecimal("target_price"));
                    map.put("quotedPrice", rs.getBigDecimal("quoted_price"));
                    map.put("realizedMargin", rs.getBigDecimal("realized_margin"));
                    map.put("calcSnapshotText", rs.getString("calc_snapshot_text"));
                    return map;
                },
                quotationId
        );
        if (quoteRows.isEmpty()) {
            throw new NoSuchElementException("Customer quotation not found");
        }
        Map<String, Object> data = quoteRows.getFirst();
        String currentStatus = (String) data.get("approvalStatus");

        if ("APPROVED".equals(currentStatus)) {
            throw new IllegalStateException("Customer quotation is already approved");
        }
        if ("EXPIRED".equals(currentStatus)) {
            throw new IllegalStateException("Customer quotation has expired");
        }

        OffsetDateTime validUntil = (OffsetDateTime) data.get("validUntil");
        if (OffsetDateTime.now().isAfter(validUntil)) {
            throw new IllegalStateException("Customer quotation validity window has expired");
        }

        String costingSheetHash = (String) data.get("costingSheetSnapshotHash");
        if (expectedSnapshotHash != null && !expectedSnapshotHash.isBlank() && !costingSheetHash.equals(expectedSnapshotHash)) {
            throw new IllegalStateException("Costing snapshot hash mismatch: expected " + expectedSnapshotHash + " but was " + costingSheetHash);
        }

        // 2. Assemble complete commercial snapshot payload
        BigDecimal sellingPrice = (BigDecimal) data.get("sellingPrice");
        BigDecimal totalCost = (BigDecimal) data.get("totalCost");
        BigDecimal profit = sellingPrice.subtract(totalCost);
        BigDecimal realizedMargin = (BigDecimal) data.get("realizedMargin");

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("proposalType", "CUSTOMER_QUOTE");
        snapshot.put("quotationId", quotationId.toString());
        snapshot.put("enquiryId", data.get("enquiryId").toString());
        snapshot.put("customerName", data.get("customerName"));
        snapshot.put("customerReference", data.get("customerReference"));
        snapshot.put("costingSheetId", data.get("costingSheetId").toString());
        snapshot.put("costingSheetSnapshotHash", costingSheetHash);
        snapshot.put("batchQuantity", ((BigDecimal) data.get("batchQuantity")).toPlainString());
        snapshot.put("materialCost", ((BigDecimal) data.get("materialCost")).toPlainString());
        snapshot.put("conversionCost", ((BigDecimal) data.get("conversionCost")).toPlainString());
        snapshot.put("overheadCost", ((BigDecimal) data.get("overheadCost")).toPlainString());
        snapshot.put("packagingCost", ((BigDecimal) data.get("packagingCost")).toPlainString());
        snapshot.put("freightCost", ((BigDecimal) data.get("freightCost")).toPlainString());
        snapshot.put("totalCost", totalCost.toPlainString());
        snapshot.put("sellingPrice", sellingPrice.toPlainString());
        snapshot.put("outputGstRate", ((BigDecimal) data.get("outputGstRate")).toPlainString());
        snapshot.put("customerPayable", ((BigDecimal) data.get("customerPayable")).toPlainString());
        snapshot.put("profit", profit.toPlainString());
        snapshot.put("marginPercent", realizedMargin != null ? realizedMargin.toPlainString() : null);
        snapshot.put("marginClassification", data.get("marginClassification"));
        String quoteRiskFlag = null;
        if (realizedMargin != null && realizedMargin.compareTo(new BigDecimal("10.00")) < 0) {
            quoteRiskFlag = "Margin " + realizedMargin.toPlainString() + "% below 10% threshold";
        } else if ("BELOW_MINIMUM_MARGIN".equalsIgnoreCase(String.valueOf(data.get("marginClassification")))) {
            quoteRiskFlag = "Below 10% minimum margin floor";
        }
        snapshot.put("riskFlag", quoteRiskFlag);
        snapshot.put("validFrom", data.get("validFrom").toString());
        snapshot.put("validUntil", validUntil.toString());
        snapshot.put("paymentTerms", data.get("paymentTerms"));
        snapshot.put("policyVersion", 1);
        snapshot.put("calculationSnapshot", read((String) data.get("calcSnapshotText")));

        String snapshotJson = write(snapshot);
        String snapshotHash = sha(snapshotJson);

        // 3. Persist commercial proposal
        UUID proposalId = db.queryForObject(
                "INSERT INTO commercial_proposals(" +
                        "type, entity_type, entity_id, cost_sheet_id, " +
                        "requested_action, requesting_user, status, " +
                        "snapshot_payload, snapshot_hash, policy_version" +
                        ") VALUES ('CUSTOMER_QUOTE', 'CUSTOMER_QUOTATION', ?, ?, 'APPROVE_CUSTOMER_QUOTE', ?, 'PENDING', ?::jsonb, ?, 1) RETURNING id",
                UUID.class,
                quotationId, data.get("costingSheetId"),
                actorRole, snapshotJson, snapshotHash
        );

        // 4. Update customer quotation to PENDING_APPROVAL and link commercial_proposal_id
        db.update(
                "UPDATE customer_quotations SET approval_status = 'PENDING_APPROVAL', commercial_proposal_id = ?, updated_at = now(), version = version + 1 WHERE id = ?",
                proposalId, quotationId
        );

        // 5. Append audit event
        db.update(
                "INSERT INTO audit_events(" +
                        "actor_id, actor_role, action, entity_type, entity_id, entity_version, " +
                        "reason, policy_version, after_hash, metadata" +
                        ") VALUES (?, ?, 'PROPOSAL_CREATED', 'PROPOSAL', ?, 0, 'Customer quotation proposal submitted for owner approval', 1, ?, ?::jsonb)",
                actorId != null ? actorId : actorRole, actorRole, proposalId, snapshotHash,
                write(Map.of("quotationId", quotationId.toString(), "costingSheetId", data.get("costingSheetId").toString(), "enquiryId", data.get("enquiryId").toString()))
        );

        return new ProposalView(proposalId, "CUSTOMER_QUOTE", "PENDING", snapshotHash, null);
    }

    @Transactional
    public ProposalView createCustomerQuoteProposalFromCostSheet(UUID costSheetId, CustomerProposalCreate in, String actorRole, String actorId) {
        var costRows = db.query(
                "SELECT cs.id, cs.enquiry_id, cs.snapshot_sha256, cs.quoted_price, cs.target_price, cs.margin_classification, " +
                        "e.customer_name " +
                        "FROM costing_sheets cs " +
                        "JOIN customer_enquiries e ON e.id = cs.enquiry_id " +
                        "WHERE cs.id = ?",
                (rs, n) -> new Object[]{
                        rs.getObject("id", UUID.class),
                        rs.getObject("enquiry_id", UUID.class),
                        rs.getString("snapshot_sha256"),
                        rs.getBigDecimal("quoted_price"),
                        rs.getBigDecimal("target_price"),
                        rs.getString("margin_classification")
                },
                costSheetId
        );
        if (costRows.isEmpty()) {
            throw new NoSuchElementException("Costing sheet not found");
        }
        Object[] cs = costRows.getFirst();
        UUID enquiryId = (UUID) cs[1];
        String costingHash = (String) cs[2];
        String marginClass = (String) cs[5];

        // Check if a quotation already exists for this costing sheet
        var existingQuotes = db.query(
                "SELECT id, approval_status FROM customer_quotations WHERE costing_sheet_id = ? ORDER BY created_at DESC LIMIT 1",
                (rs, n) -> new Object[]{rs.getObject("id", UUID.class), rs.getString("approval_status")},
                costSheetId
        );
        UUID quotationId;
        if (!existingQuotes.isEmpty() && "DRAFT".equals(existingQuotes.getFirst()[1])) {
            quotationId = (UUID) existingQuotes.getFirst()[0];
        } else {
            BigDecimal selling = in.sellingPrice() != null ? new BigDecimal(in.sellingPrice()) : (BigDecimal) cs[3];
            if (selling == null) selling = (BigDecimal) cs[4];
            BigDecimal outputGstRate = new BigDecimal("18.00");
            BigDecimal gstFactor = BigDecimal.ONE.add(outputGstRate.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
            BigDecimal customerPayable = selling.multiply(gstFactor).setScale(2, RoundingMode.HALF_UP);
            OffsetDateTime validFrom = OffsetDateTime.now();
            OffsetDateTime validUntil = in.validUntil() != null ? in.validUntil() : validFrom.plusDays(30);

            quotationId = db.queryForObject(
                    "INSERT INTO customer_quotations(" +
                            "enquiry_id, costing_sheet_id, selling_price, output_gst_rate, customer_payable, " +
                            "valid_from, valid_until, payment_terms, margin_classification, approval_status) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 'Net 30', ?, 'DRAFT') RETURNING id",
                    UUID.class,
                    enquiryId, costSheetId, selling, outputGstRate, customerPayable,
                    validFrom, validUntil, marginClass
            );
        }

        return createCustomerQuoteProposal(quotationId, costingHash, actorRole, actorId);
    }

    public ApprovalPage listApprovals(String status, int page, int size) {
        int limit = size > 0 ? Math.min(size, 100) : 20;
        int offset = Math.max(page, 0) * limit;

        String countSql = status != null && !status.isBlank()
                ? "SELECT count(*) FROM commercial_proposals WHERE status = ?"
                : "SELECT count(*) FROM commercial_proposals";
        int total = status != null && !status.isBlank()
                ? db.queryForObject(countSql, Integer.class, status)
                : db.queryForObject(countSql, Integer.class);

        String querySql = status != null && !status.isBlank()
                ? "SELECT id, type, status, snapshot_hash, invalidated_at, snapshot_payload::text, created_at FROM commercial_proposals WHERE status = ? ORDER BY created_at DESC LIMIT ? OFFSET ?"
                : "SELECT id, type, status, snapshot_hash, invalidated_at, snapshot_payload::text, created_at FROM commercial_proposals ORDER BY created_at DESC LIMIT ? OFFSET ?";

        List<ApprovalQueueItem> items = status != null && !status.isBlank()
                ? db.query(querySql, (rs, n) -> mapQueueItem(rs), status, limit, offset)
                : db.query(querySql, (rs, n) -> mapQueueItem(rs), limit, offset);

        return new ApprovalPage(items, total);
    }

    public ApprovalDetail getApprovalDetail(UUID proposalOrApprovalId) {
        // Find proposal by id or approval id
        var propRows = db.query(
                "SELECT id, type, status, snapshot_hash, invalidated_at, snapshot_payload::text, created_at FROM commercial_proposals WHERE id = ?",
                (rs, n) -> mapProposalView(rs),
                proposalOrApprovalId
        );
        UUID proposalId;
        ProposalView proposalView;
        Map<String, Object> snapshot;
        if (!propRows.isEmpty()) {
            proposalId = propRows.getFirst().id();
            proposalView = propRows.getFirst();
            String jsonStr = db.queryForObject("SELECT snapshot_payload::text FROM commercial_proposals WHERE id = ?", String.class, proposalId);
            snapshot = read(jsonStr);
        } else {
            // Check if it's an approval id
            var appRows = db.query(
                    "SELECT proposal_id FROM approvals WHERE id = ?",
                    (rs, n) -> rs.getObject("proposal_id", UUID.class),
                    proposalOrApprovalId
            );
            if (appRows.isEmpty()) {
                throw new NoSuchElementException("Approval or Proposal not found");
            }
            proposalId = appRows.getFirst();
            proposalView = db.queryForObject(
                    "SELECT id, type, status, snapshot_hash, invalidated_at, created_at FROM commercial_proposals WHERE id = ?",
                    (rs, n) -> mapProposalView(rs),
                    proposalId
            );
            String jsonStr = db.queryForObject("SELECT snapshot_payload::text FROM commercial_proposals WHERE id = ?", String.class, proposalId);
            snapshot = read(jsonStr);
        }

        // Query append-only decision records
        List<ApprovalView> decisions = db.query(
                "SELECT id, proposal_id, deciding_user, decision_status, snapshot_hash, decided_at FROM approvals WHERE proposal_id = ? ORDER BY decided_at ASC",
                (rs, n) -> new ApprovalView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("proposal_id", UUID.class),
                        rs.getString("deciding_user"),
                        rs.getString("decision_status"),
                        rs.getString("snapshot_hash"),
                        rs.getObject("decided_at", OffsetDateTime.class)
                ),
                proposalId
        );

        return new ApprovalDetail(proposalView, decisions, snapshot);
    }

    @Transactional
    public ApprovalView decide(UUID proposalId, DecisionCreate in, String actorRole, String actorId) {
        // 1. Authorize: OWNER only
        if (!"OWNER".equals(actorRole)) {
            throw new SecurityException("Only OWNER may decide commercial proposals");
        }

        // 2. Validate decision input
        String decision = in.decision() != null ? in.decision().trim().toUpperCase() : "";
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            throw new IllegalArgumentException("Decision must be APPROVE or REJECT");
        }
        if ("REJECT".equals(decision) && (in.reason() == null || in.reason().isBlank())) {
            throw new IllegalArgumentException("Decision reason is required for rejection");
        }

        // 3. Row lock on proposal
        var rows = db.query(
                "SELECT id, type, entity_type, entity_id, evaluation_id, quote_revision_id, status, " +
                        "snapshot_payload::text as snapshot_text, snapshot_hash, policy_version, version " +
                        "FROM commercial_proposals WHERE id = ? FOR UPDATE",
                (rs, n) -> new Object[]{
                        rs.getObject("id", UUID.class),
                        rs.getString("type"),
                        rs.getString("entity_type"),
                        rs.getObject("entity_id", UUID.class),
                        rs.getObject("evaluation_id", UUID.class),
                        rs.getObject("quote_revision_id", UUID.class),
                        rs.getString("status"),
                        rs.getString("snapshot_text"),
                        rs.getString("snapshot_hash"),
                        rs.getInt("policy_version"),
                        rs.getInt("version")
                },
                proposalId
        );
        if (rows.isEmpty()) {
            throw new NoSuchElementException("Proposal not found");
        }
        Object[] row = rows.getFirst();
        String type = (String) row[1];
        String currentStatus = (String) row[6];
        String snapshotText = (String) row[7];
        String storedHash = (String) row[8];
        int version = (Integer) row[10];

        // 4. Verify PENDING status
        if (!"PENDING".equals(currentStatus)) {
            throw new IllegalStateException("Proposal is not in PENDING state (current: " + currentStatus + ")");
        }

        // 5. Verify snapshot hash
        if (!storedHash.equals(in.expectedSnapshotHash())) {
            throw new IllegalStateException("Snapshot hash mismatch: proposal content has changed or is stale");
        }

        // 6. Verify quote validity for supplier award or customer quotation
        UUID quoteRevId = (UUID) row[5];
        if ("SUPPLIER_AWARD".equals(type) && quoteRevId != null) {
            String fieldsStr = db.queryForObject("SELECT fields::text FROM supplier_quote_revisions WHERE id = ?", String.class, quoteRevId);
            Map<String, Object> fields = read(fieldsStr);
            if (fields.containsKey("validFrom") && fields.containsKey("validUntil")) {
                OffsetDateTime validFrom = OffsetDateTime.parse(fields.get("validFrom").toString());
                OffsetDateTime validUntil = OffsetDateTime.parse(fields.get("validUntil").toString());
                OffsetDateTime now = OffsetDateTime.now();
                if (now.isBefore(validFrom) || now.isAfter(validUntil)) {
                    throw new IllegalStateException("Supplier quote validity window has expired at decision time");
                }
            }
        } else if ("CUSTOMER_QUOTE".equals(type)) {
            UUID quotationId = (UUID) row[3];
            var quoteRows = db.query(
                    "SELECT q.id, q.approval_status, q.valid_from, q.valid_until, cs.snapshot_sha256 " +
                            "FROM customer_quotations q " +
                            "JOIN costing_sheets cs ON cs.id = q.costing_sheet_id " +
                            "WHERE q.id = ?",
                    (rs, n) -> new Object[]{
                            rs.getObject("id", UUID.class),
                            rs.getString("approval_status"),
                            rs.getObject("valid_from", OffsetDateTime.class),
                            rs.getObject("valid_until", OffsetDateTime.class),
                            rs.getString("snapshot_sha256")
                    },
                    quotationId
            );
            if (quoteRows.isEmpty()) {
                throw new IllegalStateException("Linked customer quotation not found");
            }
            Object[] qRow = quoteRows.getFirst();
            OffsetDateTime validFrom = (OffsetDateTime) qRow[2];
            OffsetDateTime validUntil = (OffsetDateTime) qRow[3];
            String currentCostingHash = (String) qRow[4];

            OffsetDateTime nowCheck = OffsetDateTime.now();
            if (nowCheck.isBefore(validFrom) || nowCheck.isAfter(validUntil)) {
                throw new IllegalStateException("Customer quotation validity window has expired at decision time");
            }

            Map<String, Object> snapshotData = read(snapshotText);
            String expectedCostingHash = (String) snapshotData.get("costingSheetSnapshotHash");
            if (expectedCostingHash != null && !expectedCostingHash.equals(currentCostingHash)) {
                throw new IllegalStateException("Underlying costing sheet snapshot has changed or is stale");
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        String decidingUser = actorId != null ? actorId : actorRole;

        // 7. Execute State Transition
        if ("APPROVE".equals(decision)) {
            // Update proposal to APPROVED
            db.update(
                    "UPDATE commercial_proposals SET status = 'APPROVED', deciding_user = ?, decision_reason = ?, decided_at = ?, updated_at = ?, version = version + 1 WHERE id = ?",
                    decidingUser, in.reason(), now, now, proposalId
            );

            // Transition commercial entity for SUPPLIER_AWARD
            if ("SUPPLIER_AWARD".equals(type) && quoteRevId != null) {
                var quoteInfo = db.queryForObject(
                        "SELECT sq.id as quote_id, sq.rfq_id FROM supplier_quote_revisions sqr JOIN supplier_quotes sq ON sq.id = sqr.supplier_quote_id WHERE sqr.id = ?",
                        (rs, n) -> new UUID[]{rs.getObject("quote_id", UUID.class), rs.getObject("rfq_id", UUID.class)},
                        quoteRevId
                );
                UUID quoteId = quoteInfo[0];
                UUID rfqId = quoteInfo[1];

                // Update RFQ to AWARDED
                db.update("UPDATE rfqs SET awarded_quote_id = ?, status = 'AWARDED', version = version + 1 WHERE id = ?", quoteId, rfqId);

                // Update supplier_quotes to record awarded_revision_id
                db.update("UPDATE supplier_quotes SET awarded_revision_id = ? WHERE id = ?", quoteRevId, quoteId);

                // Invalidate competing proposals for the same RFQ
                db.update(
                        "UPDATE commercial_proposals SET status = 'STALE', invalidated_at = ?, invalidation_reason = 'COMPETING_SUPPLIER_AWARDED', updated_at = ?, version = version + 1 " +
                                "WHERE id != ? AND evaluation_id IN (SELECT id FROM quote_evaluations WHERE rfq_id = ?) AND status = 'PENDING'",
                        now, now, proposalId, rfqId
                );
            } else if ("CUSTOMER_QUOTE".equals(type)) {
                UUID quotationId = (UUID) row[3];
                db.update(
                        "UPDATE customer_quotations SET approval_status = 'APPROVED', updated_at = ?, version = version + 1 WHERE id = ?",
                        now, quotationId
                );

                db.update(
                        "UPDATE commercial_proposals SET status = 'STALE', invalidated_at = ?, invalidation_reason = 'SUPERSEDED_BY_APPROVED_PROPOSAL', updated_at = ?, version = version + 1 " +
                                "WHERE id != ? AND entity_id = ? AND type = 'CUSTOMER_QUOTE' AND status = 'PENDING'",
                        now, now, proposalId, quotationId
                );
            }

            // Append to approvals log
            UUID approvalId = db.queryForObject(
                    "INSERT INTO approvals(" +
                            "proposal_id, entity_type, entity_id, requested_action, requesting_user, " +
                            "decision_status, deciding_user, decision_reason, snapshot_payload, snapshot_hash, decided_at" +
                            ") VALUES (?, ?, ?, 'DECISION', ?, 'APPROVED', ?, ?, ?::jsonb, ?, ?) RETURNING id",
                    UUID.class,
                    proposalId, row[2], row[3], decidingUser,
                    decidingUser, in.reason(), snapshotText, storedHash, now
            );

            // Append to audit_events
            db.update(
                    "INSERT INTO audit_events(" +
                            "actor_id, actor_role, action, entity_type, entity_id, entity_version, " +
                            "reason, policy_version, before_hash, after_hash, metadata, occurred_at" +
                            ") VALUES (?, ?, 'PROPOSAL_APPROVED', 'PROPOSAL', ?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                    decidingUser, actorRole, proposalId, version + 1,
                    in.reason() != null ? in.reason() : "Approved by owner",
                    row[9], storedHash, storedHash,
                    write(Map.of("approvalId", approvalId.toString(), "decision", "APPROVE")), now
            );

            return new ApprovalView(approvalId, proposalId, decidingUser, "APPROVE", storedHash, now);

        } else {
            // REJECT
            db.update(
                    "UPDATE commercial_proposals SET status = 'REJECTED', deciding_user = ?, decision_reason = ?, decided_at = ?, updated_at = ?, version = version + 1 WHERE id = ?",
                    decidingUser, in.reason(), now, now, proposalId
            );

            if ("CUSTOMER_QUOTE".equals(type)) {
                UUID quotationId = (UUID) row[3];
                db.update(
                        "UPDATE customer_quotations SET approval_status = 'REJECTED', updated_at = ?, version = version + 1 WHERE id = ?",
                        now, quotationId
                );
            }

            // Append to approvals log
            UUID approvalId = db.queryForObject(
                    "INSERT INTO approvals(" +
                            "proposal_id, entity_type, entity_id, requested_action, requesting_user, " +
                            "decision_status, deciding_user, decision_reason, snapshot_payload, snapshot_hash, decided_at" +
                            ") VALUES (?, ?, ?, 'DECISION', ?, 'REJECTED', ?, ?, ?::jsonb, ?, ?) RETURNING id",
                    UUID.class,
                    proposalId, row[2], row[3], decidingUser,
                    decidingUser, in.reason(), snapshotText, storedHash, now
            );

            // Append to audit_events
            db.update(
                    "INSERT INTO audit_events(" +
                            "actor_id, actor_role, action, entity_type, entity_id, entity_version, " +
                            "reason, policy_version, before_hash, after_hash, metadata, occurred_at" +
                            ") VALUES (?, ?, 'PROPOSAL_REJECTED', 'PROPOSAL', ?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                    decidingUser, actorRole, proposalId, version + 1,
                    in.reason(), row[9], storedHash, storedHash,
                    write(Map.of("approvalId", approvalId.toString(), "decision", "REJECT", "reason", in.reason())), now
            );

            return new ApprovalView(approvalId, proposalId, decidingUser, "REJECT", storedHash, now);
        }
    }

    private ApprovalQueueItem mapQueueItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String type = rs.getString("type");
        String status = rs.getString("status");
        String hash = rs.getString("snapshot_hash");
        OffsetDateTime inv = rs.getObject("invalidated_at", OffsetDateTime.class);
        OffsetDateTime created = rs.getObject("created_at", OffsetDateTime.class);
        Map<String, Object> snapshot = read(rs.getString("snapshot_payload"));

        Map<String, Object> summary = new LinkedHashMap<>();
        if ("CUSTOMER_QUOTE".equals(type)) {
            summary.put("customerName", snapshot.get("customerName"));
            summary.put("quotationId", snapshot.get("quotationId"));
            summary.put("enquiryId", snapshot.get("enquiryId"));
            summary.put("sellingPrice", snapshot.get("sellingPrice"));
            summary.put("customerPayable", snapshot.get("customerPayable"));
            summary.put("marginClassification", snapshot.get("marginClassification"));
            summary.put("validUntil", snapshot.get("validUntil"));
        } else {
            summary.put("supplierName", snapshot.get("supplierName"));
            summary.put("specification", snapshot.get("rfqSpecification"));
            summary.put("quantity", snapshot.get("rfqQuantity"));
            summary.put("unit", snapshot.get("rfqUnit"));
            summary.put("economicLandedCost", snapshot.get("economicLandedCost"));
            summary.put("cashPayable", snapshot.get("cashPayable"));
            summary.put("currency", snapshot.get("currency"));
        }

        return new ApprovalQueueItem(new ProposalView(id, type, status, hash, inv), created, "PENDING".equals(status), summary);
    }

    private ProposalView mapProposalView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProposalView(
                rs.getObject("id", UUID.class),
                rs.getString("type"),
                rs.getString("status"),
                rs.getString("snapshot_hash"),
                rs.getObject("invalidated_at", OffsetDateTime.class)
        );
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String s) {
        try {
            return (Map<String, Object>) json.readValue(s, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String sha(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
