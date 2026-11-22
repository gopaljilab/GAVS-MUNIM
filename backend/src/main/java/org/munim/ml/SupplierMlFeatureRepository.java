package org.munim.ml;

import org.munim.ml.SupplierMlDtos.SupplierDeliveryFeatureVector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SupplierMlFeatureRepository {

    private final JdbcTemplate db;
    private final NamedParameterJdbcTemplate namedDb;

    public SupplierMlFeatureRepository(JdbcTemplate db) {
        this.db = db;
        this.namedDb = new NamedParameterJdbcTemplate(db);
    }

    /**
     * Extracts point-in-time features for a target purchase order at its creation/issuance moment.
     */
    public Optional<SupplierDeliveryFeatureVector> extractFeaturesForPurchaseOrder(UUID targetPoId) {
        return extractFeaturesForPurchaseOrder(targetPoId, null);
    }

    /**
     * Extracts point-in-time features for a target purchase order evaluated strictly against predictionCutoff T.
     * If predictionCutoff is null, defaults to target_po.created_at (the moment the contract was issued).
     * Enforces complete temporal isolation:
     * - Target PO itself is excluded.
     * - Only historical POs created strictly before cutoff T are considered.
     * - Historical completions contribute only if first_completed_at occurred strictly before T.
     * - Historical short closures contribute only if closed_at occurred strictly before T.
     * - In-flight POs overdue at T contribute delay days evaluated as of T.
     * - In-flight POs not yet due at T are right-censored and excluded from completed/failed outcomes.
     * - Future receipts, quality inspections, and financial adjustments occurring on or after T are strictly excluded.
     * - Missing denominators remain null (Non-Zero Unknown Principle).
     */
    public Optional<SupplierDeliveryFeatureVector> extractFeaturesForPurchaseOrder(UUID targetPoId, OffsetDateTime predictionCutoff) {
        String targetPoSql = """
            SELECT
                po.id,
                po.supplier_id,
                po.material_id,
                po.ordered_quantity,
                po.agreed_unit_price,
                po.agreed_freight_amount,
                po.freight_basis,
                po.promised_delivery_date,
                po.created_at,
                rfq.required_procurement_delivery_at
            FROM purchase_orders po
            JOIN rfqs rfq ON rfq.id = po.rfq_id
            WHERE po.id = ?
        """;

        return db.query(targetPoSql, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }

            UUID poId = UUID.fromString(rs.getString("id"));
            UUID supplierId = UUID.fromString(rs.getString("supplier_id"));
            UUID materialId = UUID.fromString(rs.getString("material_id"));
            BigDecimal orderedQty = rs.getBigDecimal("ordered_quantity");
            BigDecimal unitPrice = rs.getBigDecimal("agreed_unit_price");
            BigDecimal freightAmount = rs.getBigDecimal("agreed_freight_amount");
            String freightBasis = rs.getString("freight_basis");
            OffsetDateTime promisedDate = rs.getObject("promised_delivery_date", OffsetDateTime.class);
            OffsetDateTime poCreatedAt = rs.getObject("created_at", OffsetDateTime.class);
            OffsetDateTime requiredDate = rs.getObject("required_procurement_delivery_at", OffsetDateTime.class);

            OffsetDateTime cutoff = predictionCutoff != null ? predictionCutoff : poCreatedAt;
            Timestamp cutoffTimestamp = Timestamp.from(cutoff.toInstant());

            int promisedLeadDays = (int) ChronoUnit.DAYS.between(cutoff.toLocalDate(), promisedDate.toLocalDate());
            Integer factoryLeadDays = requiredDate != null ?
                    (int) ChronoUnit.DAYS.between(cutoff.toLocalDate(), requiredDate.toLocalDate()) : null;
            Integer leadTimeSlackDays = factoryLeadDays != null ? factoryLeadDays - promisedLeadDays : null;

            // -------------------------------------------------------------
            // Point-in-Time Historical PO Outcomes Query (Strictly Event < Cutoff T)
            // -------------------------------------------------------------
            String histSql = """
                WITH po_lifecycle AS (
                    SELECT
                        prior_po.id,
                        prior_po.material_id,
                        prior_po.promised_delivery_date,
                        prior_po.first_completed_at,
                        (
                            SELECT ae.action
                            FROM audit_events ae
                            WHERE ae.entity_type = 'PURCHASE_ORDER'
                              AND ae.entity_id = prior_po.id
                              AND ae.action IN ('PURCHASE_ORDER_ISSUED', 'PURCHASE_ORDER_CLOSED', 'PURCHASE_ORDER_REOPENED')
                              AND ae.occurred_at < :cutoff
                            ORDER BY ae.occurred_at DESC, COALESCE(ae.entity_version, 0) DESC, ae.id DESC
                            LIMIT 1
                        ) AS latest_action
                    FROM purchase_orders prior_po
                    WHERE prior_po.supplier_id = :supplierId
                      AND prior_po.id <> :targetPoId
                      AND prior_po.created_at < :cutoff
                      AND prior_po.status <> 'CANCELLED'
                ),
                classified_pos AS (
                    SELECT
                        pl.id,
                        pl.material_id,
                        pl.promised_delivery_date,
                        pl.first_completed_at,
                        pl.latest_action,
                        CASE
                            -- Outcome 1: Short-closed strictly before cutoff T
                            WHEN pl.latest_action = 'PURCHASE_ORDER_CLOSED'
                            THEN 'SHORT_CLOSED'

                            -- Outcome 2: Completed strictly before cutoff T
                            WHEN pl.latest_action IN ('PURCHASE_ORDER_ISSUED', 'PURCHASE_ORDER_REOPENED')
                             AND pl.first_completed_at IS NOT NULL
                             AND pl.first_completed_at < :cutoff
                            THEN 'COMPLETED'

                            -- Outcome 3: In-flight and overdue at cutoff T (promised date expired before T)
                            WHEN pl.latest_action IN ('PURCHASE_ORDER_ISSUED', 'PURCHASE_ORDER_REOPENED')
                             AND (pl.first_completed_at IS NULL OR pl.first_completed_at >= :cutoff)
                             AND CAST(pl.promised_delivery_date AS date) < CAST(:cutoff AS date)
                            THEN 'OVERDUE'

                            -- In-flight not yet due at T (or missing/ambiguous audit history)
                            ELSE 'EXCLUDED'
                        END AS historical_state
                    FROM po_lifecycle pl
                ),
                eligible_pos AS (
                    SELECT
                        cp.id,
                        cp.material_id,
                        cp.historical_state,
                        CASE
                            WHEN cp.historical_state = 'COMPLETED'
                             AND CAST(cp.first_completed_at AS date) <= CAST(cp.promised_delivery_date AS date)
                            THEN true
                            ELSE false
                        END AS is_ontime,
                        CASE
                            -- Completed late before cutoff T
                            WHEN cp.historical_state = 'COMPLETED'
                             AND CAST(cp.first_completed_at AS date) > CAST(cp.promised_delivery_date AS date)
                            THEN GREATEST(1, EXTRACT(DAY FROM (cp.first_completed_at - cp.promised_delivery_date)))

                            -- In-flight at T and overdue at T
                            WHEN cp.historical_state = 'OVERDUE'
                            THEN GREATEST(1, EXTRACT(DAY FROM (:cutoff - cp.promised_delivery_date)))

                            ELSE NULL
                        END AS delay_days
                    FROM classified_pos cp
                    WHERE cp.historical_state IN ('SHORT_CLOSED', 'COMPLETED', 'OVERDUE')
                )
                SELECT
                    COUNT(*) AS hist_eligible_count,
                    COUNT(*) FILTER (WHERE is_ontime = true) AS hist_ontime_count,
                    COUNT(*) FILTER (WHERE material_id = :materialId) AS hist_material_count,
                    COALESCE(AVG(delay_days), NULL) AS hist_avg_delay
                FROM eligible_pos
            """;

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("supplierId", supplierId)
                    .addValue("targetPoId", poId)
                    .addValue("cutoff", cutoffTimestamp)
                    .addValue("materialId", materialId);

            record PoMetrics(int histEligible, int histOntime, int histMaterialCount, BigDecimal avgDelay) {}

            PoMetrics poMetrics = namedDb.query(histSql, params, hRs -> {
                if (!hRs.next()) {
                    return new PoMetrics(0, 0, 0, null);
                }
                int eligible = hRs.getInt("hist_eligible_count");
                int ontime = hRs.getInt("hist_ontime_count");
                int materialCount = hRs.getInt("hist_material_count");
                BigDecimal avg = hRs.getBigDecimal("hist_avg_delay");
                return new PoMetrics(eligible, ontime, materialCount, avg);
            });

            BigDecimal otdRate = null;
            if (poMetrics != null && poMetrics.histEligible() > 0) {
                otdRate = BigDecimal.valueOf(poMetrics.histOntime())
                        .multiply(new BigDecimal("100.00"))
                        .divide(BigDecimal.valueOf(poMetrics.histEligible()), 2, RoundingMode.HALF_UP);
            }

            BigDecimal avgDelayDays = (poMetrics != null && poMetrics.avgDelay() != null)
                    ? poMetrics.avgDelay().setScale(1, RoundingMode.HALF_UP)
                    : null;

            // -------------------------------------------------------------
            // Point-in-Time Verified Receipts Query (Quality Conformance & Logistics)
            // -------------------------------------------------------------
            String receiptSql = """
                SELECT
                    COALESCE(SUM(CASE WHEN r.record_type = 'ORIGINAL' AND r.stage_quality = 'INSPECTED' AND r.inspected_at IS NOT NULL AND r.inspected_at < :cutoff THEN r.inspected_quantity ELSE 0 END), 0) AS total_inspected,
                    COALESCE(SUM(CASE WHEN r.record_type = 'ORIGINAL' AND r.stage_quality = 'INSPECTED' AND r.inspected_at IS NOT NULL AND r.inspected_at < :cutoff THEN r.accepted_quantity ELSE 0 END), 0) AS total_accepted,
                    COALESCE(SUM(CASE WHEN r.record_type = 'CORRECTION_ADJUSTMENT' THEN r.delta_accepted_quantity ELSE 0 END), 0) AS delta_accepted,
                    COUNT(DISTINCT CASE WHEN r.record_type = 'ORIGINAL' AND r.stage_delivery = 'RECORDED' THEN r.purchase_order_id END) AS pos_with_receipts,
                    COUNT(DISTINCT CASE WHEN r.record_type = 'ORIGINAL' AND r.stage_delivery = 'RECORDED' AND r.receipt_sequence > 1 THEN r.purchase_order_id END) AS multi_consign_pos
                FROM purchase_receipts r
                WHERE r.supplier_id = :supplierId
                  AND r.purchase_order_id <> :targetPoId
                  AND r.verification_status = 'VERIFIED'
                  AND r.verified_at IS NOT NULL
                  AND r.verified_at < :cutoff
                  AND r.created_at < :cutoff
            """;

            record ReceiptMetrics(BigDecimal netUsableYield, BigDecimal partialDeliveryRate) {}

            ReceiptMetrics receiptMetrics = namedDb.query(receiptSql, params, rRs -> {
                if (!rRs.next()) {
                    return new ReceiptMetrics(null, null);
                }
                BigDecimal totalInspected = rRs.getBigDecimal("total_inspected");
                BigDecimal totalAccepted = rRs.getBigDecimal("total_accepted");
                BigDecimal deltaAccepted = rRs.getBigDecimal("delta_accepted");
                int posWithReceipts = rRs.getInt("pos_with_receipts");
                int multiConsignPos = rRs.getInt("multi_consign_pos");

                BigDecimal yield = null;
                if (totalInspected != null && totalInspected.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal netAccepted = totalAccepted.add(deltaAccepted != null ? deltaAccepted : BigDecimal.ZERO).max(BigDecimal.ZERO);
                    yield = netAccepted.multiply(new BigDecimal("100.00")).divide(totalInspected, 2, RoundingMode.HALF_UP);
                }

                BigDecimal partialRate = null;
                if (posWithReceipts > 0) {
                    partialRate = BigDecimal.valueOf(multiConsignPos)
                            .multiply(new BigDecimal("100.00"))
                            .divide(BigDecimal.valueOf(posWithReceipts), 2, RoundingMode.HALF_UP);
                }

                return new ReceiptMetrics(yield, partialRate);
            });

            return Optional.of(new SupplierDeliveryFeatureVector(
                    "1.0.0",
                    OffsetDateTime.now(ZoneOffset.UTC),
                    poId,
                    supplierId,
                    materialId,
                    orderedQty,
                    unitPrice,
                    freightAmount,
                    freightBasis,
                    promisedLeadDays,
                    factoryLeadDays,
                    leadTimeSlackDays,
                    poMetrics != null ? poMetrics.histEligible() : 0,
                    poMetrics != null ? poMetrics.histOntime() : 0,
                    otdRate,
                    avgDelayDays,
                    receiptMetrics != null ? receiptMetrics.partialDeliveryRate() : null,
                    receiptMetrics != null ? receiptMetrics.netUsableYield() : null,
                    poMetrics != null ? poMetrics.histMaterialCount() : 0,
                    poMetrics != null && poMetrics.histMaterialCount() > 0
            ));
        }, targetPoId);
    }
}
