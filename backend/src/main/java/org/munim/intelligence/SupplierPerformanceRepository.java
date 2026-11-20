package org.munim.intelligence;

import org.munim.intelligence.SupplierPerformanceDtos.PoPerformanceRecord;
import org.munim.intelligence.SupplierPerformanceDtos.ReceiptPerformanceRecord;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class SupplierPerformanceRepository {

    private final JdbcTemplate db;

    public SupplierPerformanceRepository(JdbcTemplate db) {
        this.db = db;
    }

    public record SupplierHeader(UUID id, String name) {}

    private static final RowMapper<PoPerformanceRecord> PO_RECORD_MAPPER = (rs, n) -> new PoPerformanceRecord(
            (UUID) rs.getObject("id"),
            rs.getString("po_number"),
            (UUID) rs.getObject("supplier_id"),
            (UUID) rs.getObject("material_id"),
            rs.getBigDecimal("ordered_quantity"),
            rs.getObject("promised_delivery_date", OffsetDateTime.class),
            rs.getString("status"),
            rs.getBigDecimal("unfulfilled_quantity"),
            rs.getObject("first_completed_at", OffsetDateTime.class),
            rs.getString("po_delivery_status"),
            rs.getObject("created_at", OffsetDateTime.class)
    );

    private static final RowMapper<ReceiptPerformanceRecord> RECEIPT_RECORD_MAPPER = (rs, n) -> new ReceiptPerformanceRecord(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("purchase_order_id"),
            (UUID) rs.getObject("supplier_id"),
            (UUID) rs.getObject("material_id"),
            rs.getInt("receipt_sequence"),
            rs.getString("record_type"),
            (UUID) rs.getObject("superseded_receipt_id"),
            rs.getString("adjustment_type"),
            rs.getString("stage_delivery"),
            rs.getObject("actual_delivery_date", OffsetDateTime.class),
            rs.getBigDecimal("received_quantity"),
            rs.getString("delivery_status"),
            (Integer) rs.getObject("delay_days"),
            rs.getString("stage_quality"),
            rs.getBigDecimal("inspected_quantity"),
            rs.getBigDecimal("accepted_quantity"),
            rs.getBigDecimal("rejected_quantity"),
            rs.getString("stage_invoice"),
            rs.getBigDecimal("actual_economic_landed_cost"),
            rs.getBigDecimal("cost_variance_amount"),
            rs.getBigDecimal("delta_received_quantity"),
            rs.getBigDecimal("delta_inspected_quantity"),
            rs.getBigDecimal("delta_accepted_quantity"),
            rs.getBigDecimal("delta_rejected_quantity"),
            rs.getBigDecimal("delta_economic_landed_cost"),
            rs.getString("verification_status"),
            rs.getObject("created_at", OffsetDateTime.class)
    );

    public Optional<String> findSupplierName(UUID supplierId) {
        try {
            return Optional.ofNullable(db.queryForObject("SELECT name FROM suppliers WHERE id = ?", String.class, supplierId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<SupplierHeader> findAllActiveSuppliers() {
        return db.query(
                "SELECT id, name FROM suppliers WHERE active = true ORDER BY name",
                (rs, n) -> new SupplierHeader((UUID) rs.getObject("id"), rs.getString("name"))
        );
    }

    public List<PoPerformanceRecord> findPoRecords(UUID supplierId, UUID materialId, OffsetDateTime fromDate, OffsetDateTime toDate) {
        StringBuilder sql = new StringBuilder("SELECT id, po_number, supplier_id, material_id, ordered_quantity, promised_delivery_date, status, unfulfilled_quantity, first_completed_at, po_delivery_status, created_at FROM purchase_orders WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (supplierId != null) {
            sql.append(" AND supplier_id = ?");
            params.add(supplierId);
        }
        if (materialId != null) {
            sql.append(" AND material_id = ?");
            params.add(materialId);
        }
        if (fromDate != null) {
            sql.append(" AND created_at >= ?");
            params.add(fromDate);
        }
        if (toDate != null) {
            sql.append(" AND created_at <= ?");
            params.add(toDate);
        }

        sql.append(" ORDER BY created_at ASC");
        return db.query(sql.toString(), PO_RECORD_MAPPER, params.toArray());
    }

    public List<ReceiptPerformanceRecord> findReceiptRecords(UUID supplierId, UUID materialId, OffsetDateTime fromDate, OffsetDateTime toDate) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, purchase_order_id, supplier_id, material_id, receipt_sequence, record_type, superseded_receipt_id, adjustment_type, " +
                        "stage_delivery, actual_delivery_date, received_quantity, delivery_status, delay_days, " +
                        "stage_quality, inspected_quantity, accepted_quantity, rejected_quantity, " +
                        "stage_invoice, actual_economic_landed_cost, cost_variance_amount, " +
                        "delta_received_quantity, delta_inspected_quantity, delta_accepted_quantity, delta_rejected_quantity, " +
                        "delta_economic_landed_cost, verification_status, created_at " +
                        "FROM purchase_receipts WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (supplierId != null) {
            sql.append(" AND supplier_id = ?");
            params.add(supplierId);
        }
        if (materialId != null) {
            sql.append(" AND material_id = ?");
            params.add(materialId);
        }
        if (fromDate != null) {
            sql.append(" AND created_at >= ?");
            params.add(fromDate);
        }
        if (toDate != null) {
            sql.append(" AND created_at <= ?");
            params.add(toDate);
        }

        sql.append(" ORDER BY created_at ASC");
        return db.query(sql.toString(), RECEIPT_RECORD_MAPPER, params.toArray());
    }
}
