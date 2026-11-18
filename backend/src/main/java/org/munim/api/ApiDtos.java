package org.munim.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {}

    public record RfqCreate(@NotNull UUID materialId, @NotBlank String specification,
                            @NotNull @DecimalMin(value="0.0001") BigDecimal quantity,
                            @NotBlank String unit, OffsetDateTime customerRequiredBy,
                            Integer productionLeadDays, OffsetDateTime requiredProcurementDeliveryAt,
                            @NotBlank String timezone, @NotNull Boolean allowSplitSourcing) {}
    public record RfqView(UUID id, UUID materialId, String specification, String quantity, String unit,
                          Integer productionLeadDays, OffsetDateTime customerRequiredBy,
                          OffsetDateTime requiredProcurementDeliveryAt, OffsetDateTime referenceAt,
                          String timezone, String status) {}
    public record SupplierCreate(@NotBlank String name, String location,
                                 @NotNull Boolean isSynthetic) {}
    public record SupplierView(UUID id, String name, String location, boolean isSynthetic, boolean active) {}
    public record QuoteCreate(@NotNull UUID supplierId, @NotNull OffsetDateTime receivedAt, @NotBlank @Size(max=20000) String rawMessage,
                              @NotNull Map<String,Object> manualFields) {}
    public record QuoteView(UUID id, UUID supplierId, UUID revisionId, int revision, String state,
                           String rawMessage, String completenessStatus, List<String> missingFields,
                           Map<String,Object> fields) {}
    public record Completeness(UUID quoteRevisionId, boolean complete, List<String> missingFields,
                               List<String> warnings) {}
    public record EvaluationView(UUID id, UUID rfqId, UUID quoteRevisionId, String status,
                                String calculationVersion, String snapshotSha256,
                                Map<String,Object> inputSnapshot, Map<String,Object> result) {}
    public record EvaluationCreate(@NotNull List<UUID> quoteRevisionIds, int policyVersion) {}
    public record AwardProposalCreate(@NotNull UUID quoteRevisionId, @NotBlank String expectedSnapshotHash) {}
    public record CustomerProposalCreate(int expectedVersion, @NotBlank String sellingPrice, OffsetDateTime validUntil) {}
    public record QuotationApprovalSubmission(String expectedSnapshotHash, String notes) {}
    public record DecisionCreate(@NotBlank String decision, String reason, @NotBlank String expectedSnapshotHash) {}
    public record ProposalView(UUID id, String type, String status, String snapshotHash, OffsetDateTime invalidatedAt) {}
    public record ApprovalView(UUID id, UUID proposalId, String actorId, String decision, String snapshotHash, OffsetDateTime decidedAt) {}
    public record ApprovalQueueItem(ProposalView proposal, OffsetDateTime createdAt, boolean ownerActionRequired, Map<String, Object> summary) {}
    public record ApprovalPage(List<ApprovalQueueItem> items, int total) {}
    public record ApprovalDetail(ProposalView proposal, List<ApprovalView> decisions, Map<String, Object> snapshot) {}
    public record FieldValue(Object value, Double confidence, String evidence, String source, String status) {}
    public record ExtractionResult(UUID id, UUID revisionId, Map<String, FieldValue> candidates, List<String> missingFields, List<String> ambiguities, String validationStatus, List<String> validationErrors, String providerStatus, String modelVersion, String promptVersion, Long durationMs) {}
    public record QuoteFieldCorrection(int expectedVersion, Map<String, Object> fields, @NotBlank String correctionReason) {}
    public record ClarificationDraft(UUID id, UUID quoteRevisionId, String draft, List<String> expectedFields, String status) {}
    public record ClarificationCreate(List<String> expectedFields) {}
    public record ClarificationResponseInput(@NotBlank String responseText, OffsetDateTime receivedAt, Boolean simulated) {}
    public record RawQuoteIntake(@NotNull UUID supplierId, @NotNull OffsetDateTime receivedAt, @NotBlank String rawMessage) {}
    public record Problem(String type, String title, int status, String detail, String code,
                          Map<String,String> fieldErrors, String traceId) {}
}
