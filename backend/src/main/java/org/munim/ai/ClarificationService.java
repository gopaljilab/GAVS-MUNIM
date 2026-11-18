package org.munim.ai;

import tools.jackson.databind.ObjectMapper;
import org.munim.api.ApiDtos.*;
import org.munim.procurement.ProcurementService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ClarificationService {

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final MockExtractionProvider extractor;
    private final ExtractionValidator validator;
    private final ProcurementService procurementService;

    public ClarificationService(JdbcTemplate db, ObjectMapper json, MockExtractionProvider extractor, ExtractionValidator validator, ProcurementService procurementService) {
        this.db = db;
        this.json = json;
        this.extractor = extractor;
        this.validator = validator;
        this.procurementService = procurementService;
    }

    @Transactional
    public ClarificationDraft generateDraft(UUID quoteRevisionId, List<String> expectedFields, String actorRole, String actorId) {
        var rows = db.query(
                "SELECT sqr.id, sqr.supplier_quote_id, sqr.rfq_id, sqr.raw_message, sqr.fields::text, " +
                        "s.name as supplier_name, q.specification, q.quantity, q.unit " +
                        "FROM supplier_quote_revisions sqr " +
                        "JOIN supplier_quotes sq ON sq.id = sqr.supplier_quote_id " +
                        "JOIN suppliers s ON s.id = sq.supplier_id " +
                        "JOIN rfqs q ON q.id = sq.rfq_id " +
                        "WHERE sqr.id = ?",
                (rs, n) -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("supplierQuoteId", rs.getObject("supplier_quote_id", UUID.class));
                    map.put("supplierName", rs.getString("supplier_name"));
                    map.put("specification", rs.getString("specification"));
                    map.put("quantity", rs.getString("quantity"));
                    map.put("unit", rs.getString("unit"));
                    map.put("fields", read(rs.getString("fields")));
                    return map;
                },
                quoteRevisionId
        );
        if (rows.isEmpty()) {
            throw new NoSuchElementException("Quote revision not found: " + quoteRevisionId);
        }
        Map<String, Object> info = rows.getFirst();
        String supplierName = (String) info.get("supplierName");
        String spec = (String) info.get("specification");
        String qty = (String) info.get("quantity");
        String unit = (String) info.get("unit");
        @SuppressWarnings("unchecked")
        Map<String, Object> currentFields = (Map<String, Object>) info.get("fields");

        List<String> missing = expectedFields != null && !expectedFields.isEmpty()
                ? expectedFields
                : List.of("materialTaxRate", "priceBasis", "freightBasis", "validUntil");

        StringBuilder draft = new StringBuilder();
        draft.append("Dear ").append(supplierName).append(",\n\n");
        draft.append("Thank you for your quotation for ").append(spec).append(" (").append(qty).append(" ").append(unit).append(").\n");
        draft.append("To enable deterministic evaluation and eligibility assessment under our procurement policy, please clarify the following commercial points:\n\n");

        int qIdx = 1;
        if (missing.contains("materialTaxRate") || missing.contains("priceBasis")) {
            String price = currentFields.get("unitPrice") != null ? currentFields.get("unitPrice").toString() : "[Quoted Rate]";
            draft.append(qIdx++).append(". GST Rate & Tax Basis: Could you please confirm if the quoted price of ₹").append(price)
                    .append(" is inclusive or exclusive of GST, and what is the applicable GST percentage (e.g. 18%)?\n");
        }
        if (missing.contains("freightBasis") || missing.contains("freightAmount")) {
            draft.append(qIdx++).append(". Freight & Delivery Terms: Is delivery/freight to our factory included in the price or charged separately? If separate, please specify the freight amount.\n");
        }
        if (missing.contains("promisedDeliveryAt")) {
            draft.append(qIdx++).append(". Delivery Schedule: What is the firm dispatch/delivery timeline for the full quantity?\n");
        }
        if (missing.contains("validUntil")) {
            draft.append(qIdx++).append(". Quote Validity: What is the validity period (in days) for this quotation?\n");
        }
        if (missing.contains("paymentTerms")) {
            draft.append(qIdx++).append(". Commercial Payment Terms: Please specify your accepted payment terms (e.g. 15-day credit, advance).\n");
        }

        draft.append("\nPlease reply at your earliest convenience to proceed with evaluation.\n\n");
        draft.append("Regards,\nMUNIM Procurement Team (Draft only - not sent externally)");

        String draftText = draft.toString();

        UUID clarificationId = db.queryForObject(
                "INSERT INTO clarification_messages(quote_revision_id, missing_fields, draft_text, simulated, status) " +
                        "VALUES (?, ?::jsonb, ?, true, 'DRAFT') RETURNING id",
                UUID.class,
                quoteRevisionId, write(missing), draftText
        );

        // Audit event
        db.update(
                "INSERT INTO audit_events(actor_id, actor_role, action, entity_type, entity_id, entity_version, reason, policy_version, metadata) " +
                        "VALUES (?, ?, 'CLARIFICATION_DRAFTED', 'CLARIFICATION', ?, 0, 'Generated clarification draft for missing fields', 1, ?::jsonb)",
                actorId != null ? actorId : actorRole, actorRole, clarificationId,
                write(Map.of("quoteRevisionId", quoteRevisionId.toString(), "missingFields", missing))
        );

        return new ClarificationDraft(clarificationId, quoteRevisionId, draftText, missing, "DRAFT");
    }

    public List<ClarificationDraft> listClarifications(UUID quoteRevisionId) {
        return db.query(
                "SELECT id, quote_revision_id, draft_text, missing_fields::text, status FROM clarification_messages WHERE quote_revision_id = ? ORDER BY created_at DESC",
                (rs, n) -> new ClarificationDraft(
                        rs.getObject("id", UUID.class),
                        rs.getObject("quote_revision_id", UUID.class),
                        rs.getString("draft_text"),
                        readList(rs.getString("missing_fields")),
                        rs.getString("status")
                ),
                quoteRevisionId
        );
    }

    @Transactional
    public QuoteView simulateResponse(UUID quoteRevisionId, UUID clarificationId, ClarificationResponseInput in, String actorRole, String actorId) {
        var rows = db.query(
                "SELECT sqr.supplier_quote_id, sqr.rfq_id, sqr.revision_no, sqr.raw_message, sqr.fields::text, s.id as supplier_id " +
                        "FROM supplier_quote_revisions sqr " +
                        "JOIN supplier_quotes sq ON sq.id = sqr.supplier_quote_id " +
                        "JOIN suppliers s ON s.id = sq.supplier_id " +
                        "WHERE sqr.id = ?",
                (rs, n) -> Map.of(
                        "supplierQuoteId", rs.getObject("supplier_quote_id", UUID.class),
                        "rfqId", rs.getObject("rfq_id", UUID.class),
                        "revisionNo", rs.getInt("revision_no"),
                        "rawMessage", rs.getString("raw_message"),
                        "fields", read(rs.getString("fields")),
                        "supplierId", rs.getObject("supplier_id", UUID.class)
                ),
                quoteRevisionId
        );
        if (rows.isEmpty()) throw new NoSuchElementException("Quote revision not found");
        Map<?, ?> info = rows.getFirst();
        UUID supplierQuoteId = (UUID) info.get("supplierQuoteId");
        UUID rfqId = (UUID) info.get("rfqId");
        int revNo = (Integer) info.get("revisionNo");
        String oldRaw = (String) info.get("rawMessage");
        @SuppressWarnings("unchecked")
        Map<String, Object> oldFields = new LinkedHashMap<>((Map<String, Object>) info.get("fields"));
        UUID supplierId = (UUID) info.get("supplierId");

        OffsetDateTime receivedAt = in.receivedAt() != null ? in.receivedAt() : OffsetDateTime.now();

        // 1. Update clarification status
        db.update(
                "UPDATE clarification_messages SET response_text = ?, received_at = ?, status = 'RESPONDED', updated_at = now() WHERE id = ?",
                in.responseText(), receivedAt, clarificationId
        );

        // 2. Extract clarified fields from response text
        String combinedMessage = oldRaw + "\n[SUPPLIER CLARIFICATION RESPONSE]: " + in.responseText();
        var extraction = extractor.extract(in.responseText(), Map.of("receivedAt", receivedAt));
        var report = validator.validate(in.responseText(), extraction);

        // Merge newly resolved fields into old fields
        for (var entry : report.validatedCandidates().entrySet()) {
            if (entry.getValue().value() != null && !"MISSING".equals(entry.getValue().status())) {
                oldFields.put(entry.getKey(), entry.getValue().value());
            }
        }

        // 3. Determine completeness
        List<String> requiredKeys = List.of("unitPrice", "quotedQuantity", "unit", "priceBasis", "materialTaxRate", "freightBasis", "validUntil", "promisedDeliveryAt");
        List<String> stillMissing = new ArrayList<>();
        for (String k : requiredKeys) {
            if (!oldFields.containsKey(k) || oldFields.get(k) == null || oldFields.get(k).toString().isBlank()) {
                stillMissing.add(k);
            }
        }
        String completenessStatus = stillMissing.isEmpty() ? "COMPLETE" : "INCOMPLETE";
        String newState = stillMissing.isEmpty() ? "VERIFIED" : "NEEDS_REVIEW";

        // 4. Create Revision N+1
        int nextRev = revNo + 1;
        String newSha256 = sha(combinedMessage);
        UUID newRevId = db.queryForObject(
                "INSERT INTO supplier_quote_revisions(" +
                        "supplier_quote_id, rfq_id, revision_no, state, received_at, raw_message, raw_message_sha256, fields, completeness_status, version" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 0) RETURNING id",
                UUID.class,
                supplierQuoteId, rfqId, nextRev, newState, receivedAt, combinedMessage, newSha256, write(oldFields), completenessStatus
        );

        // Update current revision on quote
        db.update("UPDATE supplier_quotes SET current_revision_id = ? WHERE id = ?", newRevId, supplierQuoteId);

        // Audit event
        db.update(
                "INSERT INTO audit_events(actor_id, actor_role, action, entity_type, entity_id, entity_version, reason, policy_version, metadata) " +
                        "VALUES (?, ?, 'CLARIFICATION_RECEIVED', 'QUOTE_REVISION', ?, ?, 'Simulated supplier clarification response received', 1, ?::jsonb)",
                actorId != null ? actorId : actorRole, actorRole, newRevId, nextRev,
                write(Map.of("clarificationId", clarificationId.toString(), "missingResolved", stillMissing.isEmpty()))
        );

        return procurementService.getQuote(supplierQuoteId, nextRev);
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

    @SuppressWarnings("unchecked")
    private List<String> readList(String s) {
        try {
            return (List<String>) json.readValue(s, List.class);
        } catch (Exception e) {
            return List.of();
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
