package org.munim.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.munim.profitability.CommercialDtos.*;
import org.munim.profitability.CommercialService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1", "/api"})
public class CommercialController {

    private final CommercialService service;
    private final IdempotencyService idempotency;

    public CommercialController(CommercialService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    // ==========================================
    // 1. Customer Enquiries
    // ==========================================

    @PostMapping("/customer-enquiries")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerEnquiryView createEnquiry(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody EnquiryCreate input,
            HttpServletRequest request
    ) {
        if (key != null && !key.trim().isEmpty()) {
            return idempotency.execute(role(request), "enquiry.create", key, input, CustomerEnquiryView.class,
                    () -> service.createEnquiry(input));
        }
        return service.createEnquiry(input);
    }

    @GetMapping("/customer-enquiries")
    public Map<String, Object> listEnquiries(@RequestParam(required = false) String status) {
        return Map.of("items", service.listEnquiries(status));
    }

    @GetMapping("/customer-enquiries/{id}")
    public CustomerEnquiryView getEnquiry(@PathVariable UUID id) {
        return service.getEnquiry(id);
    }

    @PatchMapping("/customer-enquiries/{id}")
    public CustomerEnquiryView updateEnquiry(@PathVariable UUID id, @RequestBody EnquiryUpdate input) {
        return service.updateEnquiry(id, input);
    }

    @PutMapping("/customer-enquiries/{id}")
    public CustomerEnquiryView putEnquiry(@PathVariable UUID id, @RequestBody EnquiryUpdate input) {
        return service.updateEnquiry(id, input);
    }

    // ==========================================
    // 2. Bills of Materials (BOMs)
    // ==========================================

    @PostMapping("/customer-enquiries/{id}/boms")
    @ResponseStatus(HttpStatus.CREATED)
    public BomView createBom(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody BomCreate input,
            HttpServletRequest request
    ) {
        if (key != null && !key.trim().isEmpty()) {
            return idempotency.execute(role(request), "bom.create:" + id, key, input, BomView.class,
                    () -> service.createBom(id, input));
        }
        return service.createBom(id, input);
    }

    @GetMapping("/customer-enquiries/{id}/boms")
    public Map<String, Object> listBoms(@PathVariable UUID id) {
        return Map.of("items", service.listBomsForEnquiry(id));
    }

    @GetMapping("/boms/{id}")
    public BomView getBom(@PathVariable UUID id) {
        return service.getBom(id);
    }

    @PostMapping("/boms/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public BomItemView addBomItem(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody BomItemCreate input,
            HttpServletRequest request
    ) {
        if (key != null && !key.trim().isEmpty()) {
            return idempotency.execute(role(request), "bom.item.add:" + id, key, input, BomItemView.class,
                    () -> service.addBomItem(id, input));
        }
        return service.addBomItem(id, input);
    }

    @GetMapping("/boms/{id}/items/{itemId}")
    public BomItemView getBomItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        BomItemView item = service.getBomItem(itemId);
        if (!item.bomId().equals(id)) {
            throw new IllegalArgumentException("BOM item does not belong to the specified BOM");
        }
        return item;
    }

    @PutMapping("/boms/{id}/items/{itemId}")
    public BomItemView updateBomItem(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestBody BomItemUpdate input
    ) {
        return service.updateBomItem(id, itemId, input);
    }

    @DeleteMapping("/boms/{id}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBomItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        service.deleteBomItem(id, itemId);
    }

    // ==========================================
    // 3. Costing Workflow
    // ==========================================

    @PostMapping("/boms/{id}/costings")
    @ResponseStatus(HttpStatus.CREATED)
    public CostingSheetView executeCosting(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CostingRunRequest input,
            HttpServletRequest request
    ) {
        if (key != null && !key.trim().isEmpty()) {
            return idempotency.execute(role(request), "costing.execute:" + id, key, input, CostingSheetView.class,
                    () -> service.executeCosting(id, input));
        }
        return service.executeCosting(id, input);
    }

    @GetMapping("/boms/{id}/costings")
    public Map<String, Object> listCostings(@PathVariable UUID id) {
        return Map.of("items", service.listCostingSheetsForBom(id));
    }

    @GetMapping("/costing-sheets/{id}")
    public CostingSheetView getCostingSheet(@PathVariable UUID id) {
        return service.getCostingSheet(id);
    }

    @PostMapping({"/boms/{id}/simulate-costing", "/boms/{id}/what-if"})
    public CostingResult simulateCosting(
            @PathVariable UUID id,
            @RequestParam(required = false) BigDecimal scrapPercentage,
            @RequestParam(required = false) BigDecimal targetMarginPct
    ) {
        return service.simulateCosting(id, scrapPercentage, targetMarginPct);
    }

    // ==========================================
    // 4. Customer Quotation Workflow
    // ==========================================

    @PostMapping("/customer-quotations")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerQuotationView createQuotation(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody QuotationCreate input,
            HttpServletRequest request
    ) {
        if (key != null && !key.trim().isEmpty()) {
            return idempotency.execute(role(request), "quotation.create:" + input.costingSheetId(), key, input, CustomerQuotationView.class,
                    () -> service.createQuotation(input));
        }
        return service.createQuotation(input);
    }

    @GetMapping("/customer-quotations/{id}")
    public CustomerQuotationView getQuotation(@PathVariable UUID id) {
        return service.getCustomerQuotation(id);
    }

    @GetMapping("/customer-quotations")
    public Map<String, Object> listQuotations(@RequestParam(required = false) UUID enquiryId) {
        if (enquiryId != null) {
            return Map.of("items", service.listQuotationsForEnquiry(enquiryId));
        }
        return Map.of("items", service.listAllQuotations());
    }

    @GetMapping("/customer-enquiries/{id}/quotations")
    public Map<String, Object> listEnquiryQuotations(@PathVariable UUID id) {
        return Map.of("items", service.listQuotationsForEnquiry(id));
    }

    private String role(HttpServletRequest r) {
        Object attr = r.getAttribute("actorRole");
        return attr != null ? attr.toString() : "OWNER";
    }
}
