package org.munim.intelligence;

import org.munim.intelligence.SupplierPerformanceDtos.*;
import org.munim.intelligence.SupplierPerformanceRepository.SupplierHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SupplierPerformanceService {

    private final SupplierPerformanceRepository repository;
    private final SupplierPerformanceEngine engine;

    public SupplierPerformanceService(SupplierPerformanceRepository repository, SupplierPerformanceEngine engine) {
        this.repository = repository;
        this.engine = engine;
    }

    public SupplierScorecardView getSupplierScorecard(
            UUID supplierId,
            UUID materialId,
            OffsetDateTime fromDate,
            OffsetDateTime toDate
    ) {
        String supplierName = repository.findSupplierName(supplierId)
                .orElseThrow(() -> new NoSuchElementException("Supplier not found: " + supplierId));

        List<PoPerformanceRecord> orders = repository.findPoRecords(supplierId, materialId, fromDate, toDate);
        List<ReceiptPerformanceRecord> receipts = repository.findReceiptRecords(supplierId, materialId, fromDate, toDate);

        return engine.calculateScorecard(supplierId, supplierName, orders, receipts, OffsetDateTime.now());
    }

    public SupplierScorecardList getAllSupplierScorecards(
            UUID materialId,
            OffsetDateTime fromDate,
            OffsetDateTime toDate
    ) {
        List<SupplierHeader> suppliers = repository.findAllActiveSuppliers();
        if (suppliers.isEmpty()) {
            return new SupplierScorecardList(Collections.emptyList());
        }

        List<PoPerformanceRecord> allOrders = repository.findPoRecords(null, materialId, fromDate, toDate);
        List<ReceiptPerformanceRecord> allReceipts = repository.findReceiptRecords(null, materialId, fromDate, toDate);

        Map<UUID, List<PoPerformanceRecord>> ordersBySupplier = allOrders.stream()
                .collect(Collectors.groupingBy(PoPerformanceRecord::supplierId));

        Map<UUID, List<ReceiptPerformanceRecord>> receiptsBySupplier = allReceipts.stream()
                .collect(Collectors.groupingBy(ReceiptPerformanceRecord::supplierId));

        OffsetDateTime now = OffsetDateTime.now();
        List<SupplierScorecardView> scorecards = new ArrayList<>(suppliers.size());

        for (SupplierHeader s : suppliers) {
            List<PoPerformanceRecord> sOrders = ordersBySupplier.getOrDefault(s.id(), Collections.emptyList());
            List<ReceiptPerformanceRecord> sReceipts = receiptsBySupplier.getOrDefault(s.id(), Collections.emptyList());
            scorecards.add(engine.calculateScorecard(s.id(), s.name(), sOrders, sReceipts, now));
        }

        // Sort deterministically: Confidence Tier rank (HIGH=4, MEDIUM=3, LOW=2, UNRATED=1) desc, then OTD desc (nulls last), then Name asc
        scorecards.sort((a, b) -> {
            int rankA = tierRank(a.confidenceTier());
            int rankB = tierRank(b.confidenceTier());
            if (rankA != rankB) {
                return Integer.compare(rankB, rankA); // highest tier first
            }
            if (a.contractOtdRate() != null && b.contractOtdRate() != null) {
                int c = b.contractOtdRate().compareTo(a.contractOtdRate());
                if (c != 0) return c;
            } else if (a.contractOtdRate() != null) {
                return -1;
            } else if (b.contractOtdRate() != null) {
                return 1;
            }
            return a.supplierName().compareToIgnoreCase(b.supplierName());
        });

        return new SupplierScorecardList(scorecards);
    }

    private int tierRank(String tier) {
        if ("HIGH".equalsIgnoreCase(tier)) return 4;
        if ("MEDIUM".equalsIgnoreCase(tier)) return 3;
        if ("LOW".equalsIgnoreCase(tier)) return 2;
        return 1; // UNRATED
    }
}
