package org.munim.api;

import org.munim.intelligence.SupplierPerformanceDtos.SupplierScorecardList;
import org.munim.intelligence.SupplierPerformanceDtos.SupplierScorecardView;
import org.munim.intelligence.SupplierPerformanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1", "/api"})
public class SupplierPerformanceController {

    private final SupplierPerformanceService service;

    public SupplierPerformanceController(SupplierPerformanceService service) {
        this.service = service;
    }

    @GetMapping("/suppliers/{id}/scorecard")
    public SupplierScorecardView getSupplierScorecard(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID materialId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toDate
    ) {
        return service.getSupplierScorecard(id, materialId, fromDate, toDate);
    }

    @GetMapping("/suppliers/scorecards")
    public SupplierScorecardList listSupplierScorecards(
            @RequestParam(required = false) UUID materialId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime toDate
    ) {
        return service.getAllSupplierScorecards(materialId, fromDate, toDate);
    }
}
