package org.munim.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.munim.intelligence.SupplierIntelligenceDtos.*;
import org.munim.intelligence.SupplierIntelligenceService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
@RequestMapping({"/api/v1", "/api"})
public class SupplierIntelligenceController {
    private final SupplierIntelligenceService service;
    private final IdempotencyService idempotency;
    private final JdbcTemplate db;

    public SupplierIntelligenceController(SupplierIntelligenceService service, IdempotencyService idempotency, JdbcTemplate db) {
        this.service = service;
        this.idempotency = idempotency;
        this.db = db;
    }

    @PostMapping("/purchase-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderView createPurchaseOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody PurchaseOrderCreate input,
            HttpServletRequest request
    ) {
        var quoteInfo = db.query(
                "SELECT rfq.status, rfq.awarded_quote_id, sq.awarded_revision_id " +
                        "FROM rfqs rfq " +
                        "LEFT JOIN supplier_quotes sq ON sq.id = rfq.awarded_quote_id " +
                        "WHERE rfq.id = ?",
                (rs, n) -> new Object[]{
                        rs.getString("status"),
                        rs.getObject("awarded_quote_id", UUID.class),
                        rs.getObject("awarded_revision_id", UUID.class)
                },
                input.rfqId()
        );
        if (quoteInfo.isEmpty()) {
            throw new NoSuchElementException("RFQ not found: " + input.rfqId());
        }
        Object[] row = quoteInfo.getFirst();
        String status = (String) row[0];
        UUID awardedQuoteId = (UUID) row[1];
        UUID awardedRevId = (UUID) row[2];

        if (!"AWARDED".equals(status) || awardedQuoteId == null || awardedRevId == null) {
            throw new IllegalStateException("Cannot create purchase order: RFQ has not been awarded");
        }

        Supplier<PurchaseOrderView> action = () -> service.createPurchaseOrderForAwardedRfq(
                input.rfqId(), null, awardedRevId, actorId(request)
        );

        if (key != null && !key.isBlank()) {
            return idempotency.execute(role(request), "po.create:" + input.rfqId(), key, input, PurchaseOrderView.class, action);
        }
        return action.get();
    }

    @GetMapping("/purchase-orders")
    public PurchaseOrderPage listPurchaseOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.listPurchaseOrders(status, supplierId, page, size);
    }

    @GetMapping("/purchase-orders/{id}")
    public PurchaseOrderDetailView getPurchaseOrder(@PathVariable UUID id) {
        return service.getPurchaseOrderDetail(id);
    }

    @PostMapping("/purchase-orders/{id}/close")
    public PurchaseOrderView closePurchaseOrder(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody PurchaseOrderCloseInput input,
            HttpServletRequest request
    ) {
        Supplier<PurchaseOrderView> action = () -> service.closePurchaseOrder(id, input, actorId(request));
        if (key != null && !key.isBlank()) {
            return idempotency.execute(role(request), "po.close:" + id, key, input, PurchaseOrderView.class, action);
        }
        return action.get();
    }

    @PostMapping("/purchase-orders/{id}/reopen")
    public PurchaseOrderView reopenPurchaseOrder(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody PurchaseOrderReopenInput input,
            HttpServletRequest request
    ) {
        Supplier<PurchaseOrderView> action = () -> service.reopenPurchaseOrder(id, input, actorId(request));
        if (key != null && !key.isBlank()) {
            return idempotency.execute(role(request), "po.reopen:" + id, key, input, PurchaseOrderView.class, action);
        }
        return action.get();
    }

    @PostMapping("/purchase-orders/{id}/receipts/delivery")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseReceiptView recordDeliveryIntake(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody DeliveryIntakeInput input,
            HttpServletRequest request
    ) {
        Supplier<PurchaseReceiptView> action = () -> service.recordDeliveryIntake(id, input, actorId(request));
        if (key != null && !key.isBlank()) {
            return idempotency.execute(role(request), "receipt.delivery:" + id, key, input, PurchaseReceiptView.class, action);
        }
        return action.get();
    }

    @PutMapping("/purchase-receipts/{id}/quality-inspection")
    public PurchaseReceiptView recordQualityInspection(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody QualityInspectionInput input,
            HttpServletRequest request
    ) {
        Supplier<PurchaseReceiptView> action = () -> service.recordQualityInspection(id, input, actorId(request));
        if (key != null && !key.isBlank()) {
            return idempotency.execute(role(request), "receipt.quality:" + id, key, input, PurchaseReceiptView.class, action);
        }
        return action.get();
    }

    @PutMapping("/purchase-receipts/{id}/invoice-reconciliation")
    public PurchaseReceiptView recordInvoiceReconciliation(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody InvoiceReconciliationInput input,
            HttpServletRequest request
    ) {
        Supplier<PurchaseReceiptView> action = () -> service.recordInvoiceReconciliation(id, input, actorId(request));
        if (key != null && !key.isBlank()) {
            return idempotency.execute(role(request), "receipt.invoice:" + id, key, input, PurchaseReceiptView.class, action);
        }
        return action.get();
    }

    @PostMapping("/purchase-receipts/{id}/verify")
    public PurchaseReceiptView verifyReceipt(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) ReceiptVerificationInput input,
            HttpServletRequest request
    ) {
        Supplier<PurchaseReceiptView> action = () -> service.verifyReceipt(id, input, role(request), actorId(request));
        if (key != null && !key.isBlank()) {
            return idempotency.execute(role(request), "receipt.verify:" + id, key, input != null ? input : new ReceiptVerificationInput(null), PurchaseReceiptView.class, action);
        }
        return action.get();
    }

    @PostMapping("/purchase-receipts/{id}/adjust")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseReceiptView recordAdjustment(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ReceiptAdjustmentInput input,
            HttpServletRequest request
    ) {
        Supplier<PurchaseReceiptView> action = () -> service.recordAdjustment(id, input, role(request), actorId(request));
        if (key != null && !key.isBlank()) {
            return idempotency.execute(role(request), "receipt.adjust:" + id, key, input, PurchaseReceiptView.class, action);
        }
        return action.get();
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
