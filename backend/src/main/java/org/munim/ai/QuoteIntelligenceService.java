package org.munim.ai;

import tools.jackson.databind.ObjectMapper;
import org.munim.api.ApiDtos.*;
import org.munim.procurement.ProcurementService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class QuoteIntelligenceService {

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final MunimAiConfig config;
    private final MockExtractionProvider mockProvider;
    private final OpenAiExtractionProvider openAiProvider;
    private final ExtractionValidator validator;
    private final ProcurementService procurementService;

    public QuoteIntelligenceService(
            JdbcTemplate db,
            ObjectMapper json,
            MunimAiConfig config,
            MockExtractionProvider mockProvider,
            OpenAiExtractionProvider openAiProvider,
            ExtractionValidator validator,
            ProcurementService procurementService
    ) {
        this.db = db;
        this.json = json;
        this.config = config;
        this.mockProvider = mockProvider;
        this.openAiProvider = openAiProvider;
        this.validator = validator;
        this.procurementService = procurementService;
    }

    private QuoteExtractionProvider getProvider() {
        if ("openai".equalsIgnoreCase(config.getProvider())) {
            return openAiProvider;
        }
        return mockProvider;
    }

    @Transactional
    public ExtractionResult extractQuoteRevision(UUID quoteId, int revision, String actorRole, String actorId) {
        var rows = db.query(
                "SELECT r.id, r.raw_message, r.received_at, r.fields::text, r.version " +
                        "FROM supplier_quote_revisions r " +
                        "WHERE r.supplier_quote_id = ? AND r.revision_no = ?",
                (rs, n) -> Map.of(
                        "id", rs.getObject("id", UUID.class),
                        "rawMessage", rs.getString("raw_message"),
                        "receivedAt", rs.getObject("received_at", OffsetDateTime.class),
                        "fields", read(rs.getString("fields")),
                        "version", rs.getInt("version")
                ),
                quoteId, revision
        );
        if (rows.isEmpty()) {
            throw new NoSuchElementException("Quote revision not found: " + quoteId + " rev " + revision);
        }
        Map<?, ?> row = rows.getFirst();
        UUID revisionId = (UUID) row.get("id");
        String rawMessage = (String) row.get("rawMessage");
        OffsetDateTime receivedAt = (OffsetDateTime) row.get("receivedAt");
        @SuppressWarnings("unchecked")
        Map<String, Object> existingFields = new LinkedHashMap<>((Map<String, Object>) row.get("fields"));

        QuoteExtractionProvider provider = getProvider();
        long startTime = System.currentTimeMillis();

        // 1. Execute extraction
        QuoteExtractionProvider.RawExtraction raw = provider.extract(
                rawMessage,
                Map.of("receivedAt", receivedAt != null ? receivedAt : OffsetDateTime.now(), "quoteId", quoteId, "revision", revision)
        );
        long durationMs = System.currentTimeMillis() - startTime;

        // 2. Validate extracted fields against evidence and business rules
        ExtractionValidator.ValidationReport report = validator.validate(rawMessage, raw);

        // 3. Persist extraction record
        UUID extractionId = db.queryForObject(
                "INSERT INTO quote_extractions(" +
                        "quote_revision_id, provider, model_version, prompt_version, schema_version, " +
                        "raw_response, candidates, missing_fields, ambiguities, validation_status, " +
                        "validation_errors, duration_ms" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?) RETURNING id",
                UUID.class,
                revisionId, provider.getProviderName(), raw.modelVersion(), raw.promptVersion(), config.getSchemaVersion(),
                raw.rawResponse(), write(report.validatedCandidates()), write(report.missingFields()),
                write(report.ambiguities()), report.validationStatus(), write(report.validationErrors()), durationMs
        );

        // 4. Update quote revision fields with extracted verified candidates (if not already manual)
        for (var entry : report.validatedCandidates().entrySet()) {
            String k = entry.getKey();
            FieldValue f = entry.getValue();
            if (f.value() != null && "EXTRACTED".equals(f.status())) {
                if (!existingFields.containsKey(k) || existingFields.get(k) == null) {
                    existingFields.put(k, f.value());
                }
            }
        }

        // Assess completeness
        List<String> requiredKeys = List.of("unitPrice", "quotedQuantity", "unit", "priceBasis", "materialTaxRate", "freightBasis", "validUntil", "promisedDeliveryAt");
        List<String> missing = new ArrayList<>();
        for (String k : requiredKeys) {
            if (!existingFields.containsKey(k) || existingFields.get(k) == null || existingFields.get(k).toString().isBlank()) {
                missing.add(k);
            }
        }
        String completenessStatus = missing.isEmpty() ? "COMPLETE" : "INCOMPLETE";

        db.update(
                "UPDATE supplier_quote_revisions SET extraction_id = ?, fields = ?::jsonb, completeness_status = ?, state = 'EXTRACTED', version = version + 1 WHERE id = ?",
                extractionId, write(existingFields), completenessStatus, revisionId
        );

        // 5. Audit event
        db.update(
                "INSERT INTO audit_events(actor_id, actor_role, action, entity_type, entity_id, entity_version, reason, policy_version, metadata) " +
                        "VALUES (?, ?, 'QUOTE_EXTRACTED', 'QUOTE_REVISION', ?, ?, 'AI extraction performed on supplier quote message', 1, ?::jsonb)",
                actorId != null ? actorId : actorRole, actorRole, revisionId, (Integer) row.get("version") + 1,
                write(Map.of("extractionId", extractionId.toString(), "provider", provider.getProviderName(), "validationStatus", report.validationStatus()))
        );

        return new ExtractionResult(
                extractionId,
                revisionId,
                report.validatedCandidates(),
                report.missingFields(),
                report.ambiguities(),
                report.validationStatus(),
                report.validationErrors(),
                raw.providerStatus(),
                raw.modelVersion(),
                raw.promptVersion(),
                durationMs
        );
    }

    public ExtractionResult getLatestExtraction(UUID quoteId, int revision) {
        var rows = db.query(
                "SELECT e.id, e.quote_revision_id, e.candidates::text, e.missing_fields::text, " +
                        "e.ambiguities::text, e.validation_status, e.validation_errors::text, " +
                        "e.provider, e.model_version, e.prompt_version, e.duration_ms " +
                        "FROM quote_extractions e " +
                        "JOIN supplier_quote_revisions r ON r.id = e.quote_revision_id " +
                        "WHERE r.supplier_quote_id = ? AND r.revision_no = ? " +
                        "ORDER BY e.created_at DESC LIMIT 1",
                (rs, n) -> new ExtractionResult(
                        rs.getObject("id", UUID.class),
                        rs.getObject("quote_revision_id", UUID.class),
                        readCandidateMap(rs.getString("candidates")),
                        readList(rs.getString("missing_fields")),
                        readList(rs.getString("ambiguities")),
                        rs.getString("validation_status"),
                        readList(rs.getString("validation_errors")),
                        rs.getString("provider"),
                        rs.getString("model_version"),
                        rs.getString("prompt_version"),
                        rs.getLong("duration_ms")
                ),
                quoteId, revision
        );
        if (rows.isEmpty()) {
            throw new NoSuchElementException("No extraction found for quote " + quoteId + " rev " + revision);
        }
        return rows.getFirst();
    }

    @Transactional
    public QuoteView correctFields(UUID quoteId, int revision, QuoteFieldCorrection correction, String actorRole, String actorId) {
        var rows = db.query(
                "SELECT id, fields::text, version, state FROM supplier_quote_revisions WHERE supplier_quote_id = ? AND revision_no = ? FOR UPDATE",
                (rs, n) -> Map.of(
                        "id", rs.getObject("id", UUID.class),
                        "fields", read(rs.getString("fields")),
                        "version", rs.getInt("version"),
                        "state", rs.getString("state")
                ),
                quoteId, revision
        );
        if (rows.isEmpty()) throw new NoSuchElementException("Quote revision not found");
        Map<?, ?> row = rows.getFirst();
        UUID revisionId = (UUID) row.get("id");
        int currentVersion = (Integer) row.get("version");

        if (currentVersion != correction.expectedVersion()) {
            throw new IllegalStateException("Quote revision version mismatch (expected: " + correction.expectedVersion() + ", current: " + currentVersion + ")");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> fields = new LinkedHashMap<>((Map<String, Object>) row.get("fields"));
        // Merge corrections
        for (var e : correction.fields().entrySet()) {
            fields.put(e.getKey(), e.getValue());
        }

        // Check completeness
        List<String> requiredKeys = List.of("unitPrice", "quotedQuantity", "unit", "priceBasis", "materialTaxRate", "freightBasis", "validUntil", "promisedDeliveryAt");
        List<String> missing = new ArrayList<>();
        for (String k : requiredKeys) {
            if (!fields.containsKey(k) || fields.get(k) == null || fields.get(k).toString().isBlank()) {
                missing.add(k);
            }
        }
        String completenessStatus = missing.isEmpty() ? "COMPLETE" : "INCOMPLETE";
        String newState = missing.isEmpty() ? "VERIFIED" : "NEEDS_REVIEW";

        db.update(
                "UPDATE supplier_quote_revisions SET fields = ?::jsonb, completeness_status = ?, state = ?, version = version + 1 WHERE id = ?",
                write(fields), completenessStatus, newState, revisionId
        );

        // Audit event
        db.update(
                "INSERT INTO audit_events(actor_id, actor_role, action, entity_type, entity_id, entity_version, reason, policy_version, metadata) " +
                        "VALUES (?, ?, 'QUOTE_FIELDS_CORRECTED', 'QUOTE_REVISION', ?, ?, ?, 1, ?::jsonb)",
                actorId != null ? actorId : actorRole, actorRole, revisionId, currentVersion + 1,
                correction.correctionReason(), write(Map.of("correctedKeys", correction.fields().keySet()))
        );

        return procurementService.getQuote(quoteId, revision);
    }

    public UUID getRevisionId(UUID quoteId, int revision) {
        var rows = db.query("SELECT id FROM supplier_quote_revisions WHERE supplier_quote_id = ? AND revision_no = ?", (rs, n) -> rs.getObject("id", UUID.class), quoteId, revision);
        if (rows.isEmpty()) throw new NoSuchElementException("Quote revision not found: " + quoteId + " rev " + revision);
        return rows.getFirst();
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

    @SuppressWarnings("unchecked")
    private Map<String, FieldValue> readCandidateMap(String s) {
        try {
            Map<?, ?> map = json.readValue(s, Map.class);
            Map<String, FieldValue> res = new LinkedHashMap<>();
            for (var e : map.entrySet()) {
                Map<?, ?> v = (Map<?, ?>) e.getValue();
                res.put(e.getKey().toString(), new FieldValue(
                        v.get("value"),
                        v.get("confidence") instanceof Number n ? n.doubleValue() : null,
                        v.get("evidence") != null ? v.get("evidence").toString() : null,
                        v.get("source") != null ? v.get("source").toString() : "EXTRACTED",
                        v.get("status") != null ? v.get("status").toString() : "EXTRACTED"
                ));
            }
            return res;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
