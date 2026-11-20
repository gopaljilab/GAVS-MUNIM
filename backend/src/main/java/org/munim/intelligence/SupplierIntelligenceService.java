package org.munim.intelligence;

import tools.jackson.databind.ObjectMapper;
import org.munim.intelligence.SupplierIntelligenceDtos.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class SupplierIntelligenceService {
    private final SupplierIntelligenceRepository repository;
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public SupplierIntelligenceService(SupplierIntelligenceRepository repository, JdbcTemplate db, ObjectMapper json) {
        this.repository = repository;
        this.db = db;
        this.json = json;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderView createPurchaseOrderForAwardedRfq(UUID rfqId, UUID proposalId, UUID quoteRevisionId, String actor) {
        // Idempotency: Check if PO already exists for this RFQ
        Optional<PurchaseOrderView> existing = repository.findPoByRfqId(rfqId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 1. Fetch RFQ details
        var rfqRows = db.query(
                "SELECT material_id, quantity, unit, required_procurement_delivery_at FROM rfqs WHERE id = ?",
                (rs, n) -> new Object[]{
                        rs.getObject("material_id", UUID.class),
                        rs.getBigDecimal("quantity"),
                        rs.getString("unit"),
                        rs.getObject("required_procurement_delivery_at", OffsetDateTime.class)
                },
                rfqId
        );
        if (rfqRows.isEmpty()) {
            throw new NoSuchElementException("RFQ not found: " + rfqId);
        }
        Object[] rfq = rfqRows.getFirst();
        UUID materialId = (UUID) rfq[0];
        BigDecimal orderedQty = (BigDecimal) rfq[1];
        String unit = (String) rfq[2];
        OffsetDateTime rfqDeadline = (OffsetDateTime) rfq[3];

        // 2. Fetch Supplier Quote Revision & Evaluation details
        var quoteRows = db.query(
                "SELECT sq.supplier_id, sqr.fields::text, " +
                        "el.material_base, el.material_gst, el.freight_base, el.freight_gst, " +
                        "el.economic_landed_cost, el.cash_payable " +
                        "FROM supplier_quote_revisions sqr " +
                        "JOIN supplier_quotes sq ON sq.id = sqr.supplier_quote_id " +
                        "LEFT JOIN evaluation_lines el ON el.quote_revision_id = sqr.id " +
                        "WHERE sqr.id = ?",
                (rs, n) -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("supplierId", rs.getObject("supplier_id", UUID.class));
                    map.put("fieldsText", rs.getString("fields"));
                    map.put("materialBase", rs.getBigDecimal("material_base"));
                    map.put("materialGst", rs.getBigDecimal("material_gst"));
                    map.put("freightBase", rs.getBigDecimal("freight_base"));
                    map.put("freightGst", rs.getBigDecimal("freight_gst"));
                    map.put("economicLandedCost", rs.getBigDecimal("economic_landed_cost"));
                    map.put("cashPayable", rs.getBigDecimal("cash_payable"));
                    return map;
                },
                quoteRevisionId
        );
        if (quoteRows.isEmpty()) {
            throw new NoSuchElementException("Awarded quote revision not found: " + quoteRevisionId);
        }
        Map<String, Object> quoteInfo = quoteRows.getFirst();
        UUID supplierId = (UUID) quoteInfo.get("supplierId");
        Map<String, Object> fields = parseJson(quoteInfo.get("fieldsText"));

        BigDecimal unitPrice = parseDecimal(fields.get("unitPrice"), "unitPrice");
        BigDecimal freightAmount = parseDecimal(fields.get("freightAmount"), "freightAmount");
        String freightBasis = fields.getOrDefault("freightBasis", "SEPARATE").toString();
        BigDecimal materialTaxRate = parseDecimal(fields.getOrDefault("materialTaxRate", "18.00"), "materialTaxRate");
        String materialItcStatus = fields.getOrDefault("materialItcStatus", "CREDITABLE").toString();
        BigDecimal freightTaxRate = parseDecimal(fields.getOrDefault("freightTaxRate", "0.00"), "freightTaxRate");
        String freightItcStatus = fields.getOrDefault("freightItcStatus", "CREDITABLE").toString();

        BigDecimal economicLandedCost = (BigDecimal) quoteInfo.get("economicLandedCost");
        BigDecimal cashPayable = (BigDecimal) quoteInfo.get("cashPayable");
        if (economicLandedCost == null || cashPayable == null) {
            // Calculate if not present from evaluation line
            BigDecimal matBase = unitPrice.multiply(orderedQty).setScale(2, RoundingMode.HALF_UP);
            BigDecimal matGst = matBase.multiply(materialTaxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal frtBase = "INCLUDED_IN_UNIT_PRICE".equals(freightBasis) ? BigDecimal.ZERO.setScale(2) : freightAmount;
            BigDecimal frtGst = frtBase.multiply(freightTaxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal econ = matBase.add(frtBase);
            if ("NON_CREDITABLE".equals(materialItcStatus)) econ = econ.add(matGst);
            if ("NON_CREDITABLE".equals(freightItcStatus)) econ = econ.add(frtGst);
            economicLandedCost = econ;
            cashPayable = matBase.add(matGst).add(frtBase).add(frtGst);
        }

        OffsetDateTime promisedDelivery = null;
        if (fields.containsKey("promisedDeliveryAt") && fields.get("promisedDeliveryAt") != null) {
            promisedDelivery = OffsetDateTime.parse(fields.get("promisedDeliveryAt").toString());
        } else if (fields.containsKey("promisedDeliveryDate") && fields.get("promisedDeliveryDate") != null) {
            promisedDelivery = OffsetDateTime.parse(fields.get("promisedDeliveryDate").toString());
        } else {
            promisedDelivery = rfqDeadline != null ? rfqDeadline : OffsetDateTime.now().plusDays(7);
        }

        String poNumber = repository.generateNextPoNumber();

        // 3. Compute immutable snapshot hash
        Map<String, Object> snapshotData = new LinkedHashMap<>();
        snapshotData.put("poNumber", poNumber);
        snapshotData.put("rfqId", rfqId.toString());
        snapshotData.put("supplierId", supplierId.toString());
        snapshotData.put("quoteRevisionId", quoteRevisionId.toString());
        snapshotData.put("materialId", materialId.toString());
        snapshotData.put("orderedQuantity", orderedQty.toPlainString());
        snapshotData.put("agreedUnitPrice", unitPrice.toPlainString());
        snapshotData.put("agreedFreightAmount", freightAmount.toPlainString());
        snapshotData.put("promisedDeliveryDate", promisedDelivery.toString());
        String snapshotHash = sha256Hex(writeJson(snapshotData));

        try {
            UUID poId = repository.insertPurchaseOrder(
                    poNumber, rfqId, supplierId, quoteRevisionId, proposalId, materialId,
                    orderedQty, unit, unitPrice, freightAmount, freightBasis,
                    materialTaxRate, materialItcStatus, freightTaxRate, freightItcStatus,
                    economicLandedCost, cashPayable, promisedDelivery, "Generated upon RFQ award approval", snapshotHash
            );

            // Audit event
            db.update(
                    "INSERT INTO audit_events(" +
                            "actor_id, actor_role, action, entity_type, entity_id, entity_version, " +
                            "reason, policy_version, metadata, occurred_at" +
                            ") VALUES (?, 'OWNER', 'PURCHASE_ORDER_ISSUED', 'PURCHASE_ORDER', ?, 0, 'Purchase Order created upon RFQ award', 1, ?::jsonb, now())",
                    actor != null ? actor : "system", poId, writeJson(Map.of("poNumber", poNumber, "rfqId", rfqId.toString()))
            );

            return repository.findPoById(poId).orElseThrow();
        } catch (DuplicateKeyException e) {
            // Concurrent PO creation or unique constraint violation -> return existing
            return repository.findPoByRfqId(rfqId).orElseThrow(() -> e);
        }
    }

    public PurchaseOrderPage listPurchaseOrders(String status, UUID supplierId, int page, int size) {
        int limit = size > 0 ? Math.min(size, 100) : 20;
        int offset = Math.max(page, 0) * limit;
        int total = repository.countPurchaseOrders(status, supplierId);
        List<PurchaseOrderView> items = repository.listPurchaseOrders(status, supplierId, limit, offset);
        return new PurchaseOrderPage(items, total);
    }

    public PurchaseOrderDetailView getPurchaseOrderDetail(UUID poId) {
        PurchaseOrderView po = repository.findPoById(poId)
                .orElseThrow(() -> new NoSuchElementException("Purchase Order not found: " + poId));
        List<PurchaseReceiptView> receipts = repository.findReceiptsByPoId(poId);

        BigDecimal cumulativeReceived = repository.getCumulativeOriginalReceivedQuantity(poId);
        BigDecimal deltaReceived = repository.getCumulativeDeltaReceivedQuantity(poId);
        BigDecimal effectiveReceived = cumulativeReceived.add(deltaReceived);
        BigDecimal cumulativeAccepted = repository.getCumulativeAcceptedQuantity(poId);
        BigDecimal cumulativeRejected = repository.getCumulativeRejectedQuantity(poId);

        return new PurchaseOrderDetailView(po, cumulativeReceived, cumulativeAccepted, cumulativeRejected, effectiveReceived, receipts);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderView closePurchaseOrder(UUID poId, PurchaseOrderCloseInput in, String actor) {
        if (in.reason() == null || in.reason().trim().length() < 10) {
            throw new IllegalArgumentException("Closure reason is mandatory and must be at least 10 characters");
        }
        PurchaseOrderView po = repository.findPoById(poId)
                .orElseThrow(() -> new NoSuchElementException("Purchase Order not found: " + poId));

        if ("CANCELLED".equals(po.status())) {
            throw new IllegalStateException("Cannot close a cancelled Purchase Order");
        }

        BigDecimal cumulativeReceived = repository.getCumulativeOriginalReceivedQuantity(poId)
                .add(repository.getCumulativeDeltaReceivedQuantity(poId));
        BigDecimal unfulfilled = po.orderedQuantity().subtract(cumulativeReceived).max(BigDecimal.ZERO);

        OffsetDateTime now = OffsetDateTime.now();
        repository.closePurchaseOrder(poId, unfulfilled, in.reason().trim(), actor, now);

        db.update(
                "INSERT INTO audit_events(" +
                        "actor_id, actor_role, action, entity_type, entity_id, entity_version, " +
                        "reason, policy_version, metadata, occurred_at" +
                        ") VALUES (?, 'OWNER', 'PURCHASE_ORDER_CLOSED', 'PURCHASE_ORDER', ?, ?, ?, 1, ?::jsonb, ?)",
                actor, poId, po.version() + 1, in.reason().trim(),
                writeJson(Map.of("unfulfilledQuantity", unfulfilled.toPlainString(), "reason", in.reason().trim())), now
        );

        return repository.findPoById(poId).orElseThrow();
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderView reopenPurchaseOrder(UUID poId, PurchaseOrderReopenInput in, String actor) {
        if (in.reason() == null || in.reason().trim().length() < 10) {
            throw new IllegalArgumentException("Reopen reason is mandatory and must be at least 10 characters");
        }
        PurchaseOrderView po = repository.findPoById(poId)
                .orElseThrow(() -> new NoSuchElementException("Purchase Order not found: " + poId));

        if (!"COMPLETED".equals(po.status()) || !"PO_SHORT_CLOSED".equals(po.poDeliveryStatus())) {
            throw new IllegalStateException("Only short-closed Purchase Orders may be reopened");
        }

        OffsetDateTime now = OffsetDateTime.now();
        repository.reopenPurchaseOrder(poId);

        db.update(
                "INSERT INTO audit_events(" +
                        "actor_id, actor_role, action, entity_type, entity_id, entity_version, " +
                        "reason, policy_version, metadata, occurred_at" +
                        ") VALUES (?, 'OWNER', 'PURCHASE_ORDER_REOPENED', 'PURCHASE_ORDER', ?, ?, ?, 1, ?::jsonb, ?)",
                actor, poId, po.version() + 1, in.reason().trim(),
                writeJson(Map.of(
                        "reopenReason", in.reason().trim(),
                        "previousClosureReason", po.closureReason() != null ? po.closureReason() : "",
                        "previousUnfulfilledQuantity", po.unfulfilledQuantity().toPlainString()
                )), now
        );

        return repository.findPoById(poId).orElseThrow();
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReceiptView recordDeliveryIntake(UUID poId, DeliveryIntakeInput in, String actor) {
        PurchaseOrderView po = repository.findPoById(poId)
                .orElseThrow(() -> new NoSuchElementException("Purchase Order not found: " + poId));

        if ("CANCELLED".equals(po.status()) || "COMPLETED".equals(po.status())) {
            throw new IllegalStateException("Cannot record delivery on a " + po.status() + " Purchase Order");
        }

        if (in.receivedQuantity() == null || in.receivedQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Received quantity must be greater than zero");
        }

        // Check cumulative over-delivery (+10% limit)
        BigDecimal currentReceived = repository.getCumulativeOriginalReceivedQuantity(poId);
        BigDecimal newCumulative = currentReceived.add(in.receivedQuantity());
        BigDecimal maxAuthorized = po.orderedQuantity().multiply(new BigDecimal("1.10"));

        if (newCumulative.compareTo(maxAuthorized) > 0 && !Boolean.TRUE.equals(in.overDeliveryAuthorized())) {
            throw new IllegalArgumentException("Cumulative received quantity (" + newCumulative +
                    ") exceeds +10% tolerance limit (" + maxAuthorized + "). Manager authorization required.");
        }

        // Calculate delivery schedule against promised delivery date
        long daysDiff = ChronoUnit.DAYS.between(po.promisedDeliveryDate().toLocalDate(), in.actualDeliveryDate().toLocalDate());
        String deliveryStatus;
        if (daysDiff < 0) {
            deliveryStatus = "EARLY";
        } else if (daysDiff == 0) {
            deliveryStatus = "ON_TIME";
        } else {
            deliveryStatus = "DELAYED";
        }
        int delayDays = (int) daysDiff;
        boolean isCritical = delayDays > 3;

        int sequence = repository.getNextReceiptSequence(poId);
        UUID receiptId = repository.insertDeliveryReceipt(
                poId, po.supplierId(), po.materialId(), sequence,
                in.actualDeliveryDate(), in.receivedQuantity(), in.deliveryChallanNo().trim(),
                deliveryStatus, delayDays, isCritical
        );

        // Check initial contractual completion event
        if (po.firstCompletedAt() == null && newCumulative.compareTo(po.orderedQuantity()) >= 0) {
            OffsetDateTime firstCompletedAt = in.actualDeliveryDate();
            String poDeliveryStatus = po.promisedDeliveryDate().toLocalDate().isBefore(in.actualDeliveryDate().toLocalDate())
                    ? "PO_DELAYED" : "PO_ON_TIME";
            repository.updatePoStatus(poId, "PARTIALLY_DELIVERED", firstCompletedAt, poDeliveryStatus);
        } else if ("ISSUED".equals(po.status())) {
            repository.updatePoStatus(poId, "PARTIALLY_DELIVERED", po.firstCompletedAt(), po.poDeliveryStatus());
        }

        return repository.findReceiptById(receiptId).orElseThrow();
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReceiptView recordQualityInspection(UUID receiptId, QualityInspectionInput in, String actor) {
        PurchaseReceiptView receipt = repository.findReceiptById(receiptId)
                .orElseThrow(() -> new NoSuchElementException("Purchase Receipt not found: " + receiptId));

        if (!"ORIGINAL".equals(receipt.recordType())) {
            throw new IllegalStateException("Quality inspection can only be recorded on original receipts");
        }
        if ("VERIFIED".equals(receipt.verificationStatus())) {
            throw new IllegalStateException("Cannot update quality inspection on a verified receipt");
        }

        String status = in.status() != null ? in.status().trim().toUpperCase() : "";
        if (!"INSPECTED".equals(status) && !"NOT_INSPECTED".equals(status)) {
            throw new IllegalArgumentException("Quality status must be INSPECTED or NOT_INSPECTED");
        }

        if ("NOT_INSPECTED".equals(status)) {
            repository.updateQualityInspection(
                    receiptId, "NOT_INSPECTED", null, null, null, null, null,
                    actor, in.inspectorNotes(), OffsetDateTime.now()
            );
            return repository.findReceiptById(receiptId).orElseThrow();
        }

        // INSPECTED validation
        BigDecimal inspected = in.inspectedQuantity();
        BigDecimal accepted = in.acceptedQuantity() != null ? in.acceptedQuantity() : BigDecimal.ZERO;
        BigDecimal rejected = in.rejectedQuantity() != null ? in.rejectedQuantity() : BigDecimal.ZERO;

        if (inspected == null || inspected.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Inspected quantity must be greater than zero");
        }
        if (accepted.compareTo(BigDecimal.ZERO) < 0 || rejected.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Accepted and rejected quantities must be non-negative");
        }
        if (accepted.add(rejected).compareTo(inspected) != 0) {
            throw new IllegalArgumentException("Accepted quantity + rejected quantity must equal inspected quantity");
        }
        if (inspected.compareTo(receipt.receivedQuantity()) > 0) {
            throw new IllegalArgumentException("Inspected quantity (" + inspected + ") cannot exceed received quantity (" + receipt.receivedQuantity() + ")");
        }
        if (rejected.compareTo(BigDecimal.ZERO) > 0 && (in.rejectionReason() == null || in.rejectionReason().trim().isEmpty())) {
            throw new IllegalArgumentException("Rejection reason is mandatory when rejected quantity > 0");
        }

        BigDecimal acceptanceRate = accepted.divide(inspected, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);

        repository.updateQualityInspection(
                receiptId, "INSPECTED", inspected, accepted, rejected, acceptanceRate,
                in.rejectionReason() != null ? in.rejectionReason().trim() : null,
                actor, in.inspectorNotes(), OffsetDateTime.now()
        );

        return repository.findReceiptById(receiptId).orElseThrow();
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReceiptView recordInvoiceReconciliation(UUID receiptId, InvoiceReconciliationInput in, String actor) {
        PurchaseReceiptView receipt = repository.findReceiptById(receiptId)
                .orElseThrow(() -> new NoSuchElementException("Purchase Receipt not found: " + receiptId));

        if (!"ORIGINAL".equals(receipt.recordType())) {
            throw new IllegalStateException("Invoice reconciliation can only be recorded on original receipts");
        }
        if ("VERIFIED".equals(receipt.verificationStatus())) {
            throw new IllegalStateException("Cannot update invoice reconciliation on a verified receipt");
        }

        PurchaseOrderView po = repository.findPoById(receipt.purchaseOrderId()).orElseThrow();

        BigDecimal actualMatBase = in.actualMaterialBase();
        BigDecimal actualMatGst = in.actualMaterialGst() != null ? in.actualMaterialGst() : BigDecimal.ZERO.setScale(2);
        BigDecimal actualFrtBase = in.actualFreightBase() != null ? in.actualFreightBase() : BigDecimal.ZERO.setScale(2);
        BigDecimal actualFrtGst = in.actualFreightGst() != null ? in.actualFreightGst() : BigDecimal.ZERO.setScale(2);
        BigDecimal actualOther = in.actualOtherExpenses() != null ? in.actualOtherExpenses() : BigDecimal.ZERO.setScale(2);
        BigDecimal actualOtherGst = in.actualOtherExpensesGst() != null ? in.actualOtherExpensesGst() : BigDecimal.ZERO.setScale(2);
        BigDecimal otherTaxRate = in.otherExpensesTaxRate() != null ? in.otherExpensesTaxRate() : new BigDecimal("18.00");
        String otherItc = in.otherExpensesItcStatus() != null ? in.otherExpensesItcStatus() : "CREDITABLE";

        // V1 ITC Rules: Non-creditable GST is added to economic landed cost
        BigDecimal economicLanded = actualMatBase.add(actualFrtBase).add(actualOther);
        if ("NON_CREDITABLE".equals(po.materialItcStatus())) economicLanded = economicLanded.add(actualMatGst);
        if ("NON_CREDITABLE".equals(po.freightItcStatus())) economicLanded = economicLanded.add(actualFrtGst);
        if ("NON_CREDITABLE".equals(otherItc)) economicLanded = economicLanded.add(actualOtherGst);
        economicLanded = economicLanded.setScale(2, RoundingMode.HALF_UP);

        BigDecimal cashPaid = actualMatBase.add(actualMatGst)
                .add(actualFrtBase).add(actualFrtGst)
                .add(actualOther).add(actualOtherGst)
                .setScale(2, RoundingMode.HALF_UP);

        // Baseline calculation per Section 4.7
        BigDecimal authorizedQty = receipt.receivedQuantity().min(po.orderedQuantity().multiply(new BigDecimal("1.10")));
        BigDecimal baselineMatBase = authorizedQty.multiply(po.agreedUnitPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal baselineMatGst = "NON_CREDITABLE".equals(po.materialItcStatus())
                ? baselineMatBase.multiply(po.materialTaxRate()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);

        // Quoted freight allocation capped at PO commitment
        BigDecimal baselineFrtBase = receipt.receiptSequence() == 1 ? po.agreedFreightAmount() : BigDecimal.ZERO.setScale(2);
        BigDecimal baselineFrtGst = "NON_CREDITABLE".equals(po.freightItcStatus())
                ? baselineFrtBase.multiply(po.freightTaxRate()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);

        BigDecimal baselineLanded = baselineMatBase.add(baselineMatGst).add(baselineFrtBase).add(baselineFrtGst).setScale(2, RoundingMode.HALF_UP);
        BigDecimal costVarianceAmount = economicLanded.subtract(baselineLanded).setScale(2, RoundingMode.HALF_UP);
        BigDecimal costVariancePercentage = baselineLanded.compareTo(BigDecimal.ZERO) > 0
                ? costVarianceAmount.divide(baselineLanded, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);

        repository.updateInvoiceReconciliation(
                receiptId, in.invoiceNumber().trim(), in.invoiceDate(),
                actualMatBase, actualMatGst, actualFrtBase, actualFrtGst,
                actualOther, actualOtherGst, otherTaxRate, otherItc, in.otherExpensesDescription(),
                economicLanded, cashPaid, costVarianceAmount, costVariancePercentage
        );

        return repository.findReceiptById(receiptId).orElseThrow();
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReceiptView verifyReceipt(UUID receiptId, ReceiptVerificationInput in, String actorRole, String actor) {
        if (!"OWNER".equals(actorRole)) {
            throw new SecurityException("Only OWNER may verify purchase receipts");
        }

        PurchaseReceiptView receipt = repository.findReceiptById(receiptId)
                .orElseThrow(() -> new NoSuchElementException("Purchase Receipt not found: " + receiptId));

        if ("VERIFIED".equals(receipt.verificationStatus())) {
            return receipt; // Idempotent
        }

        // Prerequisites: Stage 1 recorded, Stage 2 inspected/not_inspected, Stage 3 reconciled
        if (!"RECORDED".equals(receipt.stageDelivery())) {
            throw new IllegalStateException("Receipt delivery must be recorded before verification");
        }
        if (!"INSPECTED".equals(receipt.stageQuality()) && !"NOT_INSPECTED".equals(receipt.stageQuality())) {
            throw new IllegalStateException("Receipt quality stage must be INSPECTED or NOT_INSPECTED before verification");
        }
        if (!"RECONCILED".equals(receipt.stageInvoice())) {
            throw new IllegalStateException("Receipt invoice must be reconciled before verification");
        }

        // Compute sealed snapshot hash
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("receiptId", receiptId.toString());
        snapshot.put("purchaseOrderId", receipt.purchaseOrderId().toString());
        snapshot.put("receivedQuantity", receipt.receivedQuantity().toPlainString());
        snapshot.put("deliveryStatus", receipt.deliveryStatus());
        snapshot.put("stageQuality", receipt.stageQuality());
        snapshot.put("acceptedQuantity", receipt.acceptedQuantity() != null ? receipt.acceptedQuantity().toPlainString() : null);
        snapshot.put("actualEconomicLandedCost", receipt.actualEconomicLandedCost().toPlainString());
        snapshot.put("actualCashPaid", receipt.actualCashPaid().toPlainString());
        snapshot.put("costVarianceAmount", receipt.costVarianceAmount().toPlainString());
        String snapshotHash = sha256Hex(writeJson(snapshot));

        OffsetDateTime now = OffsetDateTime.now();
        repository.verifyReceipt(receiptId, actor, now, snapshotHash);

        db.update(
                "INSERT INTO audit_events(" +
                        "actor_id, actor_role, action, entity_type, entity_id, entity_version, " +
                        "reason, policy_version, metadata, occurred_at" +
                        ") VALUES (?, 'OWNER', 'RECEIPT_VERIFIED', 'PURCHASE_RECEIPT', ?, 1, 'Owner verified purchase outcome', 1, ?::jsonb, ?)",
                actor, receiptId, writeJson(Map.of("snapshotHash", snapshotHash, "notes", in != null && in.notes() != null ? in.notes() : "")), now
        );

        return repository.findReceiptById(receiptId).orElseThrow();
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseReceiptView recordAdjustment(UUID originalReceiptId, ReceiptAdjustmentInput in, String actorRole, String actor) {
        if (!"OWNER".equals(actorRole)) {
            throw new SecurityException("Only OWNER may record adjustments");
        }
        if (in.correctionReason() == null || in.correctionReason().trim().length() < 10) {
            throw new IllegalArgumentException("Correction reason is mandatory and must be at least 10 characters");
        }

        PurchaseReceiptView original = repository.findReceiptById(originalReceiptId)
                .orElseThrow(() -> new NoSuchElementException("Original receipt not found: " + originalReceiptId));

        if (!"ORIGINAL".equals(original.recordType()) || !"VERIFIED".equals(original.verificationStatus())) {
            throw new IllegalStateException("Adjustments may only be recorded against verified original receipts");
        }

        String adjType = in.adjustmentType() != null ? in.adjustmentType().trim() : "";
        Set<String> validTypes = Set.of("PHYSICAL_RETURN", "PHYSICAL_QUANTITY_CORRECTION", "FINANCIAL_CREDIT_NOTE", "FINANCIAL_DEBIT_NOTE");
        if (!validTypes.contains(adjType)) {
            throw new IllegalArgumentException("Invalid adjustment type: " + in.adjustmentType());
        }

        BigDecimal dReceived = in.deltaReceivedQuantity() != null ? in.deltaReceivedQuantity() : BigDecimal.ZERO.setScale(4);
        BigDecimal dInspected = in.deltaInspectedQuantity() != null ? in.deltaInspectedQuantity() : BigDecimal.ZERO.setScale(4);
        BigDecimal dAccepted = in.deltaAcceptedQuantity() != null ? in.deltaAcceptedQuantity() : BigDecimal.ZERO.setScale(4);
        BigDecimal dRejected = in.deltaRejectedQuantity() != null ? in.deltaRejectedQuantity() : BigDecimal.ZERO.setScale(4);

        // Invariant: Financial-only adjustments must have zero physical deltas
        if (adjType.startsWith("FINANCIAL_")) {
            if (dReceived.signum() != 0 || dInspected.signum() != 0 || dAccepted.signum() != 0 || dRejected.signum() != 0) {
                throw new IllegalArgumentException("Physical quantity deltas must be 0 for financial credit/debit notes");
            }
        }

        BigDecimal dMatBase = in.deltaMaterialBase() != null ? in.deltaMaterialBase() : BigDecimal.ZERO.setScale(2);
        BigDecimal dMatGst = in.deltaMaterialGst() != null ? in.deltaMaterialGst() : BigDecimal.ZERO.setScale(2);
        BigDecimal dFrtBase = in.deltaFreightBase() != null ? in.deltaFreightBase() : BigDecimal.ZERO.setScale(2);
        BigDecimal dFrtGst = in.deltaFreightGst() != null ? in.deltaFreightGst() : BigDecimal.ZERO.setScale(2);
        BigDecimal dOther = in.deltaOtherExpenses() != null ? in.deltaOtherExpenses() : BigDecimal.ZERO.setScale(2);
        BigDecimal dOtherGst = in.deltaOtherExpensesGst() != null ? in.deltaOtherExpensesGst() : BigDecimal.ZERO.setScale(2);

        PurchaseOrderView po = repository.findPoById(original.purchaseOrderId()).orElseThrow();

        BigDecimal dEcon = dMatBase.add(dFrtBase).add(dOther);
        if ("NON_CREDITABLE".equals(po.materialItcStatus())) dEcon = dEcon.add(dMatGst);
        if ("NON_CREDITABLE".equals(po.freightItcStatus())) dEcon = dEcon.add(dFrtGst);
        dEcon = dEcon.setScale(2, RoundingMode.HALF_UP);

        BigDecimal dCash = dMatBase.add(dMatGst).add(dFrtBase).add(dFrtGst).add(dOther).add(dOtherGst).setScale(2, RoundingMode.HALF_UP);

        int sequence = repository.getNextReceiptSequence(original.purchaseOrderId());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("originalReceiptId", originalReceiptId.toString());
        snapshot.put("adjustmentType", adjType);
        snapshot.put("correctionReason", in.correctionReason().trim());
        snapshot.put("deltaReceivedQuantity", dReceived.toPlainString());
        snapshot.put("deltaAcceptedQuantity", dAccepted.toPlainString());
        snapshot.put("deltaEconomicLandedCost", dEcon.toPlainString());
        String snapshotHash = sha256Hex(writeJson(snapshot));

        OffsetDateTime now = OffsetDateTime.now();
        UUID adjId = repository.insertAdjustmentReceipt(
                original.purchaseOrderId(), original.supplierId(), original.materialId(), sequence, originalReceiptId,
                adjType, in.correctionReason().trim(), in.creditNoteNumber(),
                dReceived, dInspected, dAccepted, dRejected,
                dMatBase, dMatGst, dFrtBase, dFrtGst, dOther, dOtherGst, dEcon, dCash,
                actor, now, snapshotHash
        );

        db.update(
                "INSERT INTO audit_events(" +
                        "actor_id, actor_role, action, entity_type, entity_id, entity_version, " +
                        "reason, policy_version, metadata, occurred_at" +
                        ") VALUES (?, 'OWNER', 'RECEIPT_ADJUSTED', 'PURCHASE_RECEIPT', ?, 1, ?, 1, ?::jsonb, ?)",
                actor, adjId, in.correctionReason().trim(), writeJson(Map.of("adjustmentType", adjType, "originalReceiptId", originalReceiptId.toString())), now
        );

        return repository.findReceiptById(adjId).orElseThrow();
    }

    private Map<String, Object> parseJson(Object val) {
        if (val == null) return Collections.emptyMap();
        try {
            return json.readValue(val.toString(), Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String writeJson(Object val) {
        try {
            return json.writeValueAsString(val);
        } catch (Exception e) {
            return "{}";
        }
    }

    private BigDecimal parseDecimal(Object val, String name) {
        if (val == null || val.toString().isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(val.toString().trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid decimal value for " + name + ": " + val);
        }
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 calculation failed", e);
        }
    }
}
