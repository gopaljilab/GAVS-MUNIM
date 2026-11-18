package org.munim.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.munim.api.ApiDtos.*;
import org.munim.approval.ApprovalService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1", "/api"})
public class ApprovalController {
    private final ApprovalService approvalService;
    private final IdempotencyService idempotency;

    public ApprovalController(ApprovalService approvalService, IdempotencyService idempotency) {
        this.approvalService = approvalService;
        this.idempotency = idempotency;
    }

    @PostMapping({"/evaluations/{id}/award-proposals", "/rfqs/{rfqId}/evaluations/{id}/award-proposals"})
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalView createAwardProposal(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AwardProposalCreate input,
            HttpServletRequest request
    ) {
        return idempotency.execute(role(request), "proposal.create:" + id, key, input, ProposalView.class,
                () -> approvalService.createAwardProposal(id, input, role(request), actorId(request)));
    }

    @PostMapping({"/customer-quotations/{id}/submit-approval", "/customer-quotations/{id}/proposals", "/customer-quotations/{id}/quote-proposals"})
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalView submitCustomerQuoteForApproval(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody(required = false) QuotationApprovalSubmission input,
            HttpServletRequest request
    ) {
        String expectedHash = input != null ? input.expectedSnapshotHash() : null;
        return idempotency.execute(role(request), "proposal.quote.create:" + id, key, input, ProposalView.class,
                () -> approvalService.createCustomerQuoteProposal(id, expectedHash, role(request), actorId(request)));
    }

    @PostMapping({"/cost-sheets/{id}/quote-proposals", "/costing-sheets/{id}/quote-proposals"})
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalView createQuoteProposalFromCostSheet(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CustomerProposalCreate input,
            HttpServletRequest request
    ) {
        return idempotency.execute(role(request), "proposal.costsheet.create:" + id, key, input, ProposalView.class,
                () -> approvalService.createCustomerQuoteProposalFromCostSheet(id, input, role(request), actorId(request)));
    }

    @GetMapping("/approvals")
    public ApprovalPage listApprovals(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return approvalService.listApprovals(status, page, size);
    }

    @GetMapping("/approvals/{id}")
    public ApprovalDetail getApproval(@PathVariable UUID id) {
        return approvalService.getApprovalDetail(id);
    }

    @PostMapping({"/proposals/{id}/decision", "/approvals/{id}/decision"})
    public ApprovalView decide(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody DecisionCreate input,
            HttpServletRequest request
    ) {
        return idempotency.execute(role(request), "proposal.decision:" + id, key, input, ApprovalView.class,
                () -> approvalService.decide(id, input, role(request), actorId(request)));
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
