package org.munim.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.munim.ai.ClarificationService;
import org.munim.ai.QuoteIntelligenceService;
import org.munim.api.ApiDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class QuoteIntelligenceController {

    private final QuoteIntelligenceService intelligenceService;
    private final ClarificationService clarificationService;
    private final IdempotencyService idempotency;

    public QuoteIntelligenceController(
            QuoteIntelligenceService intelligenceService,
            ClarificationService clarificationService,
            IdempotencyService idempotency
    ) {
        this.intelligenceService = intelligenceService;
        this.clarificationService = clarificationService;
        this.idempotency = idempotency;
    }

    @PostMapping("/quotes/{quoteId}/revisions/{revision}/extract")
    public ExtractionResult extract(
            @PathVariable UUID quoteId,
            @PathVariable int revision,
            @RequestHeader("Idempotency-Key") String key,
            HttpServletRequest request
    ) {
        return idempotency.execute(role(request), "quote.extract:" + quoteId + ":" + revision, key, Map.of(), ExtractionResult.class,
                () -> intelligenceService.extractQuoteRevision(quoteId, revision, role(request), actorId(request)));
    }

    @GetMapping("/quotes/{quoteId}/revisions/{revision}/extraction")
    public ExtractionResult getExtraction(
            @PathVariable UUID quoteId,
            @PathVariable int revision
    ) {
        return intelligenceService.getLatestExtraction(quoteId, revision);
    }

    @PutMapping("/quotes/{quoteId}/revisions/{revision}/fields")
    public QuoteView correctFields(
            @PathVariable UUID quoteId,
            @PathVariable int revision,
            @Valid @RequestBody QuoteFieldCorrection input,
            HttpServletRequest request
    ) {
        return intelligenceService.correctFields(quoteId, revision, input, role(request), actorId(request));
    }

    @PostMapping("/quotes/{quoteId}/revisions/{revision}/clarifications")
    @ResponseStatus(HttpStatus.CREATED)
    public ClarificationDraft createClarification(
            @PathVariable UUID quoteId,
            @PathVariable int revision,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody(required = false) ClarificationCreate input,
            HttpServletRequest request
    ) {
        List<String> fields = input != null && input.expectedFields() != null ? input.expectedFields() : List.of();
        UUID revId = intelligenceService.getRevisionId(quoteId, revision);
        return idempotency.execute(role(request), "clarification.create:" + quoteId + ":" + revision, key, fields, ClarificationDraft.class,
                () -> clarificationService.generateDraft(revId, fields, role(request), actorId(request)));
    }

    @GetMapping("/quotes/{quoteId}/revisions/{revision}/clarifications")
    public Map<String, Object> listClarifications(
            @PathVariable UUID quoteId,
            @PathVariable int revision
    ) {
        UUID revId = intelligenceService.getRevisionId(quoteId, revision);
        return Map.of("items", clarificationService.listClarifications(revId));
    }

    @PostMapping("/quotes/{quoteId}/revisions/{revision}/clarifications/{clarificationId}/response")
    @ResponseStatus(HttpStatus.CREATED)
    public QuoteView respondClarification(
            @PathVariable UUID quoteId,
            @PathVariable int revision,
            @PathVariable UUID clarificationId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ClarificationResponseInput input,
            HttpServletRequest request
    ) {
        UUID revId = intelligenceService.getRevisionId(quoteId, revision);
        return idempotency.execute(role(request), "clarification.response:" + clarificationId, key, input, QuoteView.class,
                () -> clarificationService.simulateResponse(revId, clarificationId, input, role(request), actorId(request)));
    }

    private String role(HttpServletRequest r) {
        Object role = r.getAttribute("actorRole");
        return role != null ? role.toString() : "UNKNOWN";
    }

    private String actorId(HttpServletRequest r) {
        Object id = r.getAttribute("actorId");
        return id != null ? id.toString() : role(r);
    }
}
