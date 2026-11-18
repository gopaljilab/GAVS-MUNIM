package org.munim.profitability;

import tools.jackson.databind.ObjectMapper;
import org.munim.profitability.CommercialDtos.*;
import org.munim.profitability.ProfitabilityDtos.SourcingBasis;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class CommercialRepository {

    private final JdbcTemplate db;
    private final ObjectMapper json;

    public CommercialRepository(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json != null ? json : new ObjectMapper();
    }

    // ==========================================
    // 1. Customer Enquiries
    // ==========================================

    private final RowMapper<CustomerEnquiryView> enquiryMapper = (rs, rowNum) -> new CustomerEnquiryView(
            rs.getObject("id", UUID.class),
            rs.getString("customer_name"),
            rs.getString("customer_reference"),
            rs.getString("contact_email"),
            rs.getString("contact_phone"),
            rs.getObject("required_delivery_date", OffsetDateTime.class),
            rs.getString("status"),
            rs.getString("notes"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getInt("version")
    );

    public UUID insertEnquiry(EnquiryCreate in) {
        return db.queryForObject(
                "INSERT INTO customer_enquiries(customer_name, customer_reference, contact_email, contact_phone, required_delivery_date, status, notes) " +
                        "VALUES (?, ?, ?, ?, ?, 'OPEN', ?) RETURNING id",
                UUID.class,
                in.customerName().trim(),
                in.customerReference() != null ? in.customerReference().trim() : null,
                in.contactEmail() != null ? in.contactEmail().trim() : null,
                in.contactPhone() != null ? in.contactPhone().trim() : null,
                in.requiredDeliveryDate(),
                in.notes() != null ? in.notes().trim() : null
        );
    }

    public Optional<CustomerEnquiryView> findEnquiryById(UUID id) {
        var list = db.query("SELECT * FROM customer_enquiries WHERE id = ?", enquiryMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public List<CustomerEnquiryView> listEnquiries(String statusFilter) {
        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            return db.query("SELECT * FROM customer_enquiries WHERE status = ? ORDER BY created_at DESC",
                    enquiryMapper, statusFilter.trim().toUpperCase(Locale.ROOT));
        }
        return db.query("SELECT * FROM customer_enquiries ORDER BY created_at DESC", enquiryMapper);
    }

    public boolean updateEnquiry(UUID id, EnquiryUpdate in) {
        return db.update(
                "UPDATE customer_enquiries SET " +
                        "customer_reference = COALESCE(?, customer_reference), " +
                        "contact_email = COALESCE(?, contact_email), " +
                        "contact_phone = COALESCE(?, contact_phone), " +
                        "required_delivery_date = COALESCE(?, required_delivery_date), " +
                        "notes = COALESCE(?, notes), " +
                        "status = COALESCE(?, status), " +
                        "updated_at = now(), version = version + 1 " +
                        "WHERE id = ?",
                in.customerReference() != null ? in.customerReference().trim() : null,
                in.contactEmail() != null ? in.contactEmail().trim() : null,
                in.contactPhone() != null ? in.contactPhone().trim() : null,
                in.requiredDeliveryDate(),
                in.notes() != null ? in.notes().trim() : null,
                in.status() != null ? in.status().trim().toUpperCase(Locale.ROOT) : null,
                id
        ) > 0;
    }

    public boolean updateEnquiryStatus(UUID id, String status) {
        return db.update(
                "UPDATE customer_enquiries SET status = ?, updated_at = now(), version = version + 1 WHERE id = ?",
                status, id
        ) > 0;
    }

    // ==========================================
    // 2. Bills of Materials (BOMs)
    // ==========================================

    public int getNextBomVersion(UUID enquiryId) {
        Integer maxVersion = db.queryForObject(
                "SELECT COALESCE(MAX(bom_version), 0) FROM boms WHERE enquiry_id = ?",
                Integer.class,
                enquiryId
        );
        return (maxVersion != null ? maxVersion : 0) + 1;
    }

    public UUID insertBom(UUID enquiryId, int version, BigDecimal batchQuantity) {
        return db.queryForObject(
                "INSERT INTO boms(enquiry_id, bom_version, batch_quantity, status) " +
                        "VALUES (?, ?, ?, 'DRAFT') RETURNING id",
                UUID.class,
                enquiryId, version, batchQuantity
        );
    }

    public Optional<BomView> findBomById(UUID id) {
        var list = db.query(
                "SELECT * FROM boms WHERE id = ?",
                (rs, rowNum) -> mapBom(rs),
                id
        );
        if (list.isEmpty()) {
            return Optional.empty();
        }
        BomView bom = list.getFirst();
        List<BomItemView> items = listBomItemsByBomId(bom.id());
        return Optional.of(new BomView(
                bom.id(), bom.enquiryId(), bom.bomVersion(), bom.batchQuantity(),
                bom.status(), bom.createdAt(), bom.updatedAt(), items
        ));
    }

    public List<BomView> listBomsByEnquiryId(UUID enquiryId) {
        var boms = db.query(
                "SELECT * FROM boms WHERE enquiry_id = ? ORDER BY bom_version DESC",
                (rs, rowNum) -> mapBom(rs),
                enquiryId
        );
        List<BomView> result = new ArrayList<>();
        for (BomView b : boms) {
            List<BomItemView> items = listBomItemsByBomId(b.id());
            result.add(new BomView(
                    b.id(), b.enquiryId(), b.bomVersion(), b.batchQuantity(),
                    b.status(), b.createdAt(), b.updatedAt(), items
            ));
        }
        return result;
    }

    private BomView mapBom(ResultSet rs) throws SQLException {
        return new BomView(
                rs.getObject("id", UUID.class),
                rs.getObject("enquiry_id", UUID.class),
                rs.getInt("bom_version"),
                rs.getBigDecimal("batch_quantity"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                List.of()
        );
    }

    // ==========================================
    // 3. BOM Items
    // ==========================================

    private final RowMapper<BomItemView> bomItemMapper = (rs, rowNum) -> new BomItemView(
            rs.getObject("id", UUID.class),
            rs.getObject("bom_id", UUID.class),
            rs.getObject("material_id", UUID.class),
            rs.getString("part_name"),
            rs.getBigDecimal("net_quantity"),
            rs.getString("unit"),
            rs.getBigDecimal("scrap_percentage"),
            rs.getBoolean("scrap_recovery_supported"),
            rs.getBigDecimal("scrap_recovery_price"),
            SourcingBasis.valueOf(rs.getString("sourcing_basis")),
            rs.getObject("awarded_quote_revision_id", UUID.class),
            rs.getBigDecimal("estimated_unit_cost"),
            rs.getString("estimate_rationale"),
            rs.getObject("created_at", OffsetDateTime.class)
    );

    public UUID insertBomItem(UUID bomId, BomItemCreate in) {
        BigDecimal scrapPercent = in.scrapPercentage() != null ? in.scrapPercentage() : BigDecimal.ZERO;
        boolean scrapRecovery = Boolean.TRUE.equals(in.scrapRecoverySupported());
        BigDecimal scrapPrice = scrapRecovery ? in.scrapRecoveryPrice() : null;

        UUID awardedRevId = in.sourcingBasis() == SourcingBasis.AWARDED_QUOTE ? in.awardedQuoteRevisionId() : null;
        BigDecimal estCost = in.sourcingBasis() == SourcingBasis.ESTIMATE ? in.estimatedUnitCost() : null;
        String estRationale = in.sourcingBasis() == SourcingBasis.ESTIMATE ? (in.estimateRationale() != null ? in.estimateRationale().trim() : null) : null;

        return db.queryForObject(
                "INSERT INTO bom_items(bom_id, material_id, part_name, net_quantity, unit, scrap_percentage, " +
                        "scrap_recovery_supported, scrap_recovery_price, sourcing_basis, awarded_quote_revision_id, estimated_unit_cost, estimate_rationale) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                UUID.class,
                bomId,
                in.materialId(),
                in.partName().trim(),
                in.netQuantity(),
                in.unit().trim().toLowerCase(Locale.ROOT),
                scrapPercent,
                scrapRecovery,
                scrapPrice,
                in.sourcingBasis().name(),
                awardedRevId,
                estCost,
                estRationale
        );
    }

    public Optional<BomItemView> findBomItemById(UUID id) {
        var list = db.query("SELECT * FROM bom_items WHERE id = ?", bomItemMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public List<BomItemView> listBomItemsByBomId(UUID bomId) {
        return db.query("SELECT * FROM bom_items WHERE bom_id = ? ORDER BY created_at ASC", bomItemMapper, bomId);
    }

    public boolean updateBomItem(UUID itemId, BomItemUpdate in) {
        var existingOpt = findBomItemById(itemId);
        if (existingOpt.isEmpty()) return false;
        var ex = existingOpt.get();

        String partName = in.partName() != null ? in.partName().trim() : ex.partName();
        BigDecimal netQty = in.netQuantity() != null ? in.netQuantity() : ex.netQuantity();
        String unit = in.unit() != null ? in.unit().trim().toLowerCase(Locale.ROOT) : ex.unit();
        BigDecimal scrapPct = in.scrapPercentage() != null ? in.scrapPercentage() : ex.scrapPercentage();
        boolean scrapRecovery = in.scrapRecoverySupported() != null ? in.scrapRecoverySupported() : ex.scrapRecoverySupported();
        BigDecimal scrapPrice = scrapRecovery ? (in.scrapRecoveryPrice() != null ? in.scrapRecoveryPrice() : ex.scrapRecoveryPrice()) : null;

        SourcingBasis basis = in.sourcingBasis() != null ? in.sourcingBasis() : ex.sourcingBasis();
        UUID awardedRevId = basis == SourcingBasis.AWARDED_QUOTE
                ? (in.awardedQuoteRevisionId() != null ? in.awardedQuoteRevisionId() : ex.awardedQuoteRevisionId())
                : null;
        BigDecimal estCost = basis == SourcingBasis.ESTIMATE
                ? (in.estimatedUnitCost() != null ? in.estimatedUnitCost() : ex.estimatedUnitCost())
                : null;
        String estRationale = basis == SourcingBasis.ESTIMATE
                ? (in.estimateRationale() != null ? in.estimateRationale().trim() : ex.estimateRationale())
                : null;

        return db.update(
                "UPDATE bom_items SET part_name = ?, net_quantity = ?, unit = ?, scrap_percentage = ?, " +
                        "scrap_recovery_supported = ?, scrap_recovery_price = ?, sourcing_basis = ?, " +
                        "awarded_quote_revision_id = ?, estimated_unit_cost = ?, estimate_rationale = ? " +
                        "WHERE id = ?",
                partName, netQty, unit, scrapPct, scrapRecovery, scrapPrice, basis.name(), awardedRevId, estCost, estRationale, itemId
        ) > 0;
    }

    public boolean deleteBomItem(UUID itemId) {
        return db.update("DELETE FROM bom_items WHERE id = ?", itemId) > 0;
    }

    public boolean isAwardedQuoteRevisionEligible(UUID quoteRevisionId) {
        if (quoteRevisionId == null) return false;
        Boolean exists = db.queryForObject(
                "SELECT EXISTS (" +
                        "SELECT 1 FROM supplier_quote_revisions sqr " +
                        "JOIN supplier_quotes sq ON sq.id = sqr.supplier_quote_id " +
                        "LEFT JOIN rfqs r ON r.id = sq.rfq_id " +
                        "WHERE sqr.id = ? AND (" +
                        "sq.awarded_revision_id = sqr.id " +
                        "OR r.awarded_quote_id = sq.id " +
                        "OR EXISTS (SELECT 1 FROM commercial_proposals cp WHERE cp.quote_revision_id = sqr.id AND cp.status = 'APPROVED')" +
                        ")" +
                        ")",
                Boolean.class,
                quoteRevisionId
        );
        return Boolean.TRUE.equals(exists);
    }

    public Map<String, Object> getSupplierQuoteRevisionFields(UUID quoteRevisionId) {
        if (quoteRevisionId == null) return Map.of();
        var rows = db.query(
                "SELECT sqr.fields::text FROM supplier_quote_revisions sqr WHERE sqr.id = ?",
                (rs, n) -> rs.getString(1),
                quoteRevisionId
        );
        if (rows.isEmpty()) return Map.of();
        try {
            return json.readValue(rows.getFirst(), Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ==========================================
    // 4. Costing Sheets
    // ==========================================

    public UUID insertCostingSheet(
            UUID enquiryId,
            UUID bomId,
            BigDecimal batchQuantity,
            BigDecimal materialCost,
            BigDecimal conversionCost,
            BigDecimal overheadCost,
            BigDecimal packagingCost,
            BigDecimal freightCost,
            BigDecimal totalCost,
            BigDecimal targetMargin,
            BigDecimal targetPrice,
            BigDecimal quotedPrice,
            BigDecimal realizedMargin,
            String marginClassification,
            String calculationVersion,
            Map<String, Object> snapshot,
            String snapshotSha256
    ) {
        String snapshotJson;
        try {
            snapshotJson = json.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize costing snapshot", e);
        }

        return db.queryForObject(
                "INSERT INTO costing_sheets(" +
                        "enquiry_id, bom_id, batch_quantity, material_cost, conversion_cost, overhead_cost, " +
                        "packaging_cost, freight_cost, total_cost, target_margin, target_price, quoted_price, " +
                        "realized_margin, margin_classification, calculation_version, calculation_snapshot, snapshot_sha256) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?) RETURNING id",
                UUID.class,
                enquiryId, bomId, batchQuantity, materialCost, conversionCost, overheadCost,
                packagingCost, freightCost, totalCost, targetMargin, targetPrice, quotedPrice,
                realizedMargin, marginClassification, calculationVersion, snapshotJson, snapshotSha256
        );
    }

    public Optional<CostingSheetView> findCostingSheetById(UUID id) {
        var list = db.query("SELECT * FROM costing_sheets WHERE id = ?", (rs, rowNum) -> mapCostingSheet(rs), id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public List<CostingSheetView> listCostingSheetsByBomId(UUID bomId) {
        return db.query("SELECT * FROM costing_sheets WHERE bom_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> mapCostingSheet(rs), bomId);
    }

    private CostingSheetView mapCostingSheet(ResultSet rs) throws SQLException {
        Map<String, Object> snapshotMap;
        try {
            snapshotMap = json.readValue(rs.getString("calculation_snapshot"), Map.class);
        } catch (Exception e) {
            snapshotMap = Map.of();
        }

        return new CostingSheetView(
                rs.getObject("id", UUID.class),
                rs.getObject("enquiry_id", UUID.class),
                rs.getObject("bom_id", UUID.class),
                rs.getBigDecimal("batch_quantity"),
                rs.getBigDecimal("material_cost"),
                rs.getBigDecimal("conversion_cost"),
                rs.getBigDecimal("overhead_cost"),
                rs.getBigDecimal("packaging_cost"),
                rs.getBigDecimal("freight_cost"),
                rs.getBigDecimal("total_cost"),
                rs.getBigDecimal("target_margin"),
                rs.getBigDecimal("target_price"),
                rs.getBigDecimal("quoted_price"),
                rs.getBigDecimal("realized_margin"),
                rs.getString("margin_classification"),
                rs.getString("calculation_version"),
                snapshotMap,
                rs.getString("snapshot_sha256"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    // ==========================================
    // 5. Customer Quotations
    // ==========================================

    private final RowMapper<CustomerQuotationView> quotationMapper = (rs, rowNum) -> new CustomerQuotationView(
            rs.getObject("id", UUID.class),
            rs.getObject("enquiry_id", UUID.class),
            rs.getObject("costing_sheet_id", UUID.class),
            rs.getBigDecimal("selling_price"),
            rs.getBigDecimal("output_gst_rate"),
            rs.getBigDecimal("customer_payable"),
            rs.getObject("valid_from", OffsetDateTime.class),
            rs.getObject("valid_until", OffsetDateTime.class),
            rs.getString("payment_terms"),
            rs.getString("margin_classification"),
            rs.getString("approval_status"),
            rs.getObject("commercial_proposal_id", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getInt("version")
    );

    public UUID insertCustomerQuotation(
            UUID enquiryId,
            UUID costingSheetId,
            BigDecimal sellingPrice,
            BigDecimal outputGstRate,
            BigDecimal customerPayable,
            OffsetDateTime validFrom,
            OffsetDateTime validUntil,
            String paymentTerms,
            String marginClassification
    ) {
        return db.queryForObject(
                "INSERT INTO customer_quotations(" +
                        "enquiry_id, costing_sheet_id, selling_price, output_gst_rate, customer_payable, " +
                        "valid_from, valid_until, payment_terms, margin_classification, approval_status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT') RETURNING id",
                UUID.class,
                enquiryId, costingSheetId, sellingPrice, outputGstRate, customerPayable,
                validFrom, validUntil, paymentTerms.trim(), marginClassification
        );
    }

    public Optional<CustomerQuotationView> findCustomerQuotationById(UUID id) {
        var list = db.query("SELECT * FROM customer_quotations WHERE id = ?", quotationMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public List<CustomerQuotationView> listCustomerQuotationsByEnquiryId(UUID enquiryId) {
        return db.query("SELECT * FROM customer_quotations WHERE enquiry_id = ? ORDER BY created_at DESC",
                quotationMapper, enquiryId);
    }

    public List<CustomerQuotationView> listAllCustomerQuotations() {
        return db.query("SELECT * FROM customer_quotations ORDER BY created_at DESC", quotationMapper);
    }
}
