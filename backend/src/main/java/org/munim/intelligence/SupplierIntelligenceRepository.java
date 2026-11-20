package org.munim.intelligence;

import org.munim.intelligence.SupplierIntelligenceDtos.PurchaseOrderView;
import org.munim.intelligence.SupplierIntelligenceDtos.PurchaseReceiptView;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.*;

@Repository
public class SupplierIntelligenceRepository {
    private final JdbcTemplate db;

    public SupplierIntelligenceRepository(JdbcTemplate db) {
        this.db = db;
    }

    private static final RowMapper<PurchaseOrderView> PO_MAPPER = (rs, n) -> new PurchaseOrderView(
            rs.getObject("id", UUID.class),
            rs.getString("po_number"),
            rs.getObject("rfq_id", UUID.class),
            rs.getObject("supplier_id", UUID.class),
            rs.getObject("quote_revision_id", UUID.class),
            rs.getObject("proposal_id", UUID.class),
            rs.getObject("material_id", UUID.class),
            rs.getBigDecimal("ordered_quantity"),
            rs.getString("unit"),
            rs.getBigDecimal("agreed_unit_price"),
            rs.getBigDecimal("agreed_freight_amount"),
            rs.getString("freight_basis"),
            rs.getBigDecimal("material_tax_rate"),
            rs.getString("material_itc_status"),
            rs.getBigDecimal("freight_tax_rate"),
            rs.getString("freight_itc_status"),
            rs.getBigDecimal("quoted_economic_landed_cost"),
            rs.getBigDecimal("quoted_cash_payable"),
            rs.getObject("promised_delivery_date", OffsetDateTime.class),
            rs.getString("status"),
            rs.getBigDecimal("unfulfilled_quantity"),
            rs.getString("closure_reason"),
            rs.getString("closed_by"),
            rs.getObject("closed_at", OffsetDateTime.class),
            rs.getObject("first_completed_at", OffsetDateTime.class),
            rs.getString("po_delivery_status"),
            rs.getString("notes"),
            rs.getString("po_snapshot_hash"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getInt("version")
    );

    private static final RowMapper<PurchaseReceiptView> RECEIPT_MAPPER = (rs, n) -> new PurchaseReceiptView(
            rs.getObject("id", UUID.class),
            rs.getObject("purchase_order_id", UUID.class),
            rs.getObject("supplier_id", UUID.class),
            rs.getObject("material_id", UUID.class),
            rs.getInt("receipt_sequence"),
            rs.getString("record_type"),
            rs.getObject("superseded_receipt_id", UUID.class),
            rs.getString("adjustment_type"),
            rs.getString("correction_reason"),
            rs.getString("stage_delivery"),
            rs.getObject("actual_delivery_date", OffsetDateTime.class),
            rs.getBigDecimal("received_quantity"),
            rs.getString("delivery_challan_no"),
            rs.getString("delivery_status"),
            (Integer) rs.getObject("delay_days"),
            rs.getBoolean("is_critical_delay"),
            rs.getString("stage_quality"),
            rs.getBigDecimal("inspected_quantity"),
            rs.getBigDecimal("accepted_quantity"),
            rs.getBigDecimal("rejected_quantity"),
            rs.getBigDecimal("quality_acceptance_rate"),
            rs.getString("rejection_reason"),
            rs.getString("inspector_id"),
            rs.getString("inspector_notes"),
            rs.getObject("inspected_at", OffsetDateTime.class),
            rs.getString("stage_invoice"),
            rs.getString("invoice_number"),
            rs.getObject("invoice_date", OffsetDateTime.class),
            rs.getBigDecimal("actual_material_base"),
            rs.getBigDecimal("actual_material_gst"),
            rs.getBigDecimal("actual_freight_base"),
            rs.getBigDecimal("actual_freight_gst"),
            rs.getBigDecimal("actual_other_expenses"),
            rs.getBigDecimal("actual_other_expenses_gst"),
            rs.getBigDecimal("other_expenses_tax_rate"),
            rs.getString("other_expenses_itc_status"),
            rs.getString("other_expenses_description"),
            rs.getBigDecimal("actual_economic_landed_cost"),
            rs.getBigDecimal("actual_cash_paid"),
            rs.getBigDecimal("cost_variance_amount"),
            rs.getBigDecimal("cost_variance_percentage"),
            rs.getString("currency"),
            rs.getBigDecimal("delta_received_quantity"),
            rs.getBigDecimal("delta_inspected_quantity"),
            rs.getBigDecimal("delta_accepted_quantity"),
            rs.getBigDecimal("delta_rejected_quantity"),
            rs.getBigDecimal("delta_material_base"),
            rs.getBigDecimal("delta_material_gst"),
            rs.getBigDecimal("delta_freight_base"),
            rs.getBigDecimal("delta_freight_gst"),
            rs.getBigDecimal("delta_other_expenses"),
            rs.getBigDecimal("delta_other_expenses_gst"),
            rs.getBigDecimal("delta_economic_landed_cost"),
            rs.getBigDecimal("delta_cash_paid"),
            rs.getString("credit_note_number"),
            rs.getString("verification_status"),
            rs.getString("verified_by"),
            rs.getObject("verified_at", OffsetDateTime.class),
            rs.getString("outcome_snapshot_hash"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
    );

    public synchronized String generateNextPoNumber() {
        int year = Year.now().getValue();
        String prefix = "PO-" + year + "-";
        Integer maxSeq = db.queryForObject(
                "SELECT COALESCE(MAX(CAST(SUBSTRING(po_number FROM 9) AS INTEGER)), 0) FROM purchase_orders WHERE po_number LIKE ?",
                Integer.class,
                prefix + "%"
        );
        int next = (maxSeq != null ? maxSeq : 0) + 1;
        return String.format("PO-%d-%04d", year, next);
    }

    public Optional<PurchaseOrderView> findPoById(UUID id) {
        try {
            return Optional.ofNullable(db.queryForObject("SELECT * FROM purchase_orders WHERE id = ?", PO_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<PurchaseOrderView> findPoByRfqId(UUID rfqId) {
        try {
            return Optional.ofNullable(db.queryForObject("SELECT * FROM purchase_orders WHERE rfq_id = ?", PO_MAPPER, rfqId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public UUID insertPurchaseOrder(
            String poNumber, UUID rfqId, UUID supplierId, UUID quoteRevisionId, UUID proposalId,
            UUID materialId, BigDecimal orderedQuantity, String unit, BigDecimal agreedUnitPrice,
            BigDecimal agreedFreightAmount, String freightBasis, BigDecimal materialTaxRate,
            String materialItcStatus, BigDecimal freightTaxRate, String freightItcStatus,
            BigDecimal quotedEconomicLandedCost, BigDecimal quotedCashPayable,
            OffsetDateTime promisedDeliveryDate, String notes, String poSnapshotHash
    ) {
        return db.queryForObject(
                "INSERT INTO purchase_orders(" +
                        "po_number, rfq_id, supplier_id, quote_revision_id, proposal_id, material_id, " +
                        "ordered_quantity, unit, agreed_unit_price, agreed_freight_amount, freight_basis, " +
                        "material_tax_rate, material_itc_status, freight_tax_rate, freight_itc_status, " +
                        "quoted_economic_landed_cost, quoted_cash_payable, promised_delivery_date, " +
                        "status, notes, po_snapshot_hash) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ISSUED', ?, ?) RETURNING id",
                UUID.class,
                poNumber, rfqId, supplierId, quoteRevisionId, proposalId, materialId,
                orderedQuantity, unit, agreedUnitPrice, agreedFreightAmount, freightBasis,
                materialTaxRate, materialItcStatus, freightTaxRate, freightItcStatus,
                quotedEconomicLandedCost, quotedCashPayable, promisedDeliveryDate, notes, poSnapshotHash
        );
    }

    public List<PurchaseOrderView> listPurchaseOrders(String status, UUID supplierId, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM purchase_orders WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status.trim());
        }
        if (supplierId != null) {
            sql.append(" AND supplier_id = ?");
            params.add(supplierId);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);
        return db.query(sql.toString(), PO_MAPPER, params.toArray());
    }

    public int countPurchaseOrders(String status, UUID supplierId) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM purchase_orders WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status.trim());
        }
        if (supplierId != null) {
            sql.append(" AND supplier_id = ?");
            params.add(supplierId);
        }
        Integer count = db.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    public void updatePoStatus(UUID poId, String status, OffsetDateTime firstCompletedAt, String poDeliveryStatus) {
        db.update(
                "UPDATE purchase_orders SET status = ?, first_completed_at = ?, po_delivery_status = ?, updated_at = now(), version = version + 1 WHERE id = ?",
                status, firstCompletedAt, poDeliveryStatus, poId
        );
    }

    public void closePurchaseOrder(UUID poId, BigDecimal unfulfilledQuantity, String reason, String closedBy, OffsetDateTime closedAt) {
        db.update(
                "UPDATE purchase_orders SET status = 'COMPLETED', unfulfilled_quantity = ?, closure_reason = ?, closed_by = ?, closed_at = ?, first_completed_at = NULL, po_delivery_status = 'PO_SHORT_CLOSED', updated_at = now(), version = version + 1 WHERE id = ?",
                unfulfilledQuantity, reason, closedBy, closedAt, poId
        );
    }

    public void reopenPurchaseOrder(UUID poId) {
        db.update(
                "UPDATE purchase_orders SET status = 'PARTIALLY_DELIVERED', unfulfilled_quantity = 0.0000, updated_at = now(), version = version + 1 WHERE id = ?",
                poId
        );
    }

    public int getNextReceiptSequence(UUID poId) {
        Integer seq = db.queryForObject(
                "SELECT COALESCE(MAX(receipt_sequence), 0) + 1 FROM purchase_receipts WHERE purchase_order_id = ?",
                Integer.class,
                poId
        );
        return seq != null ? seq : 1;
    }

    public Optional<PurchaseReceiptView> findReceiptById(UUID receiptId) {
        try {
            return Optional.ofNullable(db.queryForObject("SELECT * FROM purchase_receipts WHERE id = ?", RECEIPT_MAPPER, receiptId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<PurchaseReceiptView> findReceiptsByPoId(UUID poId) {
        return db.query("SELECT * FROM purchase_receipts WHERE purchase_order_id = ? ORDER BY receipt_sequence ASC, created_at ASC", RECEIPT_MAPPER, poId);
    }

    public UUID insertDeliveryReceipt(
            UUID poId, UUID supplierId, UUID materialId, int sequence,
            OffsetDateTime actualDeliveryDate, BigDecimal receivedQuantity,
            String deliveryChallanNo, String deliveryStatus, Integer delayDays, boolean isCriticalDelay
    ) {
        return db.queryForObject(
                "INSERT INTO purchase_receipts(" +
                        "purchase_order_id, supplier_id, material_id, receipt_sequence, record_type, " +
                        "stage_delivery, actual_delivery_date, received_quantity, delivery_challan_no, " +
                        "delivery_status, delay_days, is_critical_delay, stage_quality, stage_invoice, verification_status" +
                        ") VALUES (?, ?, ?, ?, 'ORIGINAL', 'RECORDED', ?, ?, ?, ?, ?, ?, 'PENDING', 'PENDING', 'UNVERIFIED') RETURNING id",
                UUID.class,
                poId, supplierId, materialId, sequence,
                actualDeliveryDate, receivedQuantity, deliveryChallanNo,
                deliveryStatus, delayDays, isCriticalDelay
        );
    }

    public void updateQualityInspection(
            UUID receiptId, String status, BigDecimal inspectedQuantity, BigDecimal acceptedQuantity,
            BigDecimal rejectedQuantity, BigDecimal qualityAcceptanceRate, String rejectionReason,
            String inspectorId, String inspectorNotes, OffsetDateTime inspectedAt
    ) {
        db.update(
                "UPDATE purchase_receipts SET stage_quality = ?, inspected_quantity = ?, accepted_quantity = ?, " +
                        "rejected_quantity = ?, quality_acceptance_rate = ?, rejection_reason = ?, " +
                        "inspector_id = ?, inspector_notes = ?, inspected_at = ?, updated_at = now() WHERE id = ?",
                status, inspectedQuantity, acceptedQuantity, rejectedQuantity,
                qualityAcceptanceRate, rejectionReason, inspectorId, inspectorNotes, inspectedAt, receiptId
        );
    }

    public void updateInvoiceReconciliation(
            UUID receiptId, String invoiceNumber, OffsetDateTime invoiceDate,
            BigDecimal actualMaterialBase, BigDecimal actualMaterialGst,
            BigDecimal actualFreightBase, BigDecimal actualFreightGst,
            BigDecimal actualOtherExpenses, BigDecimal actualOtherExpensesGst,
            BigDecimal otherExpensesTaxRate, String otherExpensesItcStatus, String otherExpensesDescription,
            BigDecimal actualEconomicLandedCost, BigDecimal actualCashPaid,
            BigDecimal costVarianceAmount, BigDecimal costVariancePercentage
    ) {
        db.update(
                "UPDATE purchase_receipts SET stage_invoice = 'RECONCILED', invoice_number = ?, invoice_date = ?, " +
                        "actual_material_base = ?, actual_material_gst = ?, actual_freight_base = ?, actual_freight_gst = ?, " +
                        "actual_other_expenses = ?, actual_other_expenses_gst = ?, other_expenses_tax_rate = ?, " +
                        "other_expenses_itc_status = ?, other_expenses_description = ?, actual_economic_landed_cost = ?, " +
                        "actual_cash_paid = ?, cost_variance_amount = ?, cost_variance_percentage = ?, updated_at = now() WHERE id = ?",
                invoiceNumber, invoiceDate, actualMaterialBase, actualMaterialGst,
                actualFreightBase, actualFreightGst, actualOtherExpenses, actualOtherExpensesGst,
                otherExpensesTaxRate, otherExpensesItcStatus, otherExpensesDescription,
                actualEconomicLandedCost, actualCashPaid, costVarianceAmount, costVariancePercentage, receiptId
        );
    }

    public void verifyReceipt(UUID receiptId, String verifiedBy, OffsetDateTime verifiedAt, String outcomeSnapshotHash) {
        db.update(
                "UPDATE purchase_receipts SET verification_status = 'VERIFIED', verified_by = ?, verified_at = ?, " +
                        "outcome_snapshot_hash = ?, updated_at = now() WHERE id = ?",
                verifiedBy, verifiedAt, outcomeSnapshotHash, receiptId
        );
    }

    public UUID insertAdjustmentReceipt(
            UUID poId, UUID supplierId, UUID materialId, int sequence, UUID supersededReceiptId,
            String adjustmentType, String correctionReason, String creditNoteNumber,
            BigDecimal deltaReceivedQuantity, BigDecimal deltaInspectedQuantity,
            BigDecimal deltaAcceptedQuantity, BigDecimal deltaRejectedQuantity,
            BigDecimal deltaMaterialBase, BigDecimal deltaMaterialGst,
            BigDecimal deltaFreightBase, BigDecimal deltaFreightGst,
            BigDecimal deltaOtherExpenses, BigDecimal deltaOtherExpensesGst,
            BigDecimal deltaEconomicLandedCost, BigDecimal deltaCashPaid,
            String verifiedBy, OffsetDateTime verifiedAt, String outcomeSnapshotHash
    ) {
        return db.queryForObject(
                "INSERT INTO purchase_receipts(" +
                        "purchase_order_id, supplier_id, material_id, receipt_sequence, record_type, " +
                        "superseded_receipt_id, adjustment_type, correction_reason, credit_note_number, " +
                        "delta_received_quantity, delta_inspected_quantity, delta_accepted_quantity, delta_rejected_quantity, " +
                        "delta_material_base, delta_material_gst, delta_freight_base, delta_freight_gst, " +
                        "delta_other_expenses, delta_other_expenses_gst, delta_economic_landed_cost, delta_cash_paid, " +
                        "stage_delivery, stage_quality, stage_invoice, verification_status, verified_by, verified_at, outcome_snapshot_hash" +
                        ") VALUES (?, ?, ?, ?, 'CORRECTION_ADJUSTMENT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECORDED', 'INSPECTED', 'RECONCILED', 'VERIFIED', ?, ?, ?) RETURNING id",
                UUID.class,
                poId, supplierId, materialId, sequence, supersededReceiptId,
                adjustmentType, correctionReason, creditNoteNumber,
                deltaReceivedQuantity, deltaInspectedQuantity, deltaAcceptedQuantity, deltaRejectedQuantity,
                deltaMaterialBase, deltaMaterialGst, deltaFreightBase, deltaFreightGst,
                deltaOtherExpenses, deltaOtherExpensesGst, deltaEconomicLandedCost, deltaCashPaid,
                verifiedBy, verifiedAt, outcomeSnapshotHash
        );
    }

    public BigDecimal getCumulativeOriginalReceivedQuantity(UUID poId) {
        BigDecimal val = db.queryForObject(
                "SELECT COALESCE(SUM(received_quantity), 0.0000) FROM purchase_receipts WHERE purchase_order_id = ? AND record_type = 'ORIGINAL' AND stage_delivery = 'RECORDED'",
                BigDecimal.class,
                poId
        );
        return val != null ? val : BigDecimal.ZERO;
    }

    public BigDecimal getCumulativeDeltaReceivedQuantity(UUID poId) {
        BigDecimal val = db.queryForObject(
                "SELECT COALESCE(SUM(delta_received_quantity), 0.0000) FROM purchase_receipts WHERE purchase_order_id = ? AND record_type = 'CORRECTION_ADJUSTMENT'",
                BigDecimal.class,
                poId
        );
        return val != null ? val : BigDecimal.ZERO;
    }

    public BigDecimal getCumulativeAcceptedQuantity(UUID poId) {
        BigDecimal orig = db.queryForObject(
                "SELECT COALESCE(SUM(accepted_quantity), 0.0000) FROM purchase_receipts WHERE purchase_order_id = ? AND record_type = 'ORIGINAL' AND stage_quality = 'INSPECTED'",
                BigDecimal.class,
                poId
        );
        BigDecimal delta = db.queryForObject(
                "SELECT COALESCE(SUM(delta_accepted_quantity), 0.0000) FROM purchase_receipts WHERE purchase_order_id = ? AND record_type = 'CORRECTION_ADJUSTMENT'",
                BigDecimal.class,
                poId
        );
        return (orig != null ? orig : BigDecimal.ZERO).add(delta != null ? delta : BigDecimal.ZERO);
    }

    public BigDecimal getCumulativeRejectedQuantity(UUID poId) {
        BigDecimal orig = db.queryForObject(
                "SELECT COALESCE(SUM(rejected_quantity), 0.0000) FROM purchase_receipts WHERE purchase_order_id = ? AND record_type = 'ORIGINAL' AND stage_quality = 'INSPECTED'",
                BigDecimal.class,
                poId
        );
        BigDecimal delta = db.queryForObject(
                "SELECT COALESCE(SUM(delta_rejected_quantity), 0.0000) FROM purchase_receipts WHERE purchase_order_id = ? AND record_type = 'CORRECTION_ADJUSTMENT'",
                BigDecimal.class,
                poId
        );
        return (orig != null ? orig : BigDecimal.ZERO).add(delta != null ? delta : BigDecimal.ZERO);
    }
}
