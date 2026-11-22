package org.munim.ml;

import org.munim.ml.SupplierMlDtos.MlStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal")
public class SupplierMlController {

    private final SupplierMlService mlService;

    public SupplierMlController(SupplierMlService mlService) {
        this.mlService = mlService;
    }

    @GetMapping("/ml-status")
    public ResponseEntity<MlStatusResponse> getStatus() {
        MlStatusResponse status = mlService.getMlEngineStatus();
        return ResponseEntity.ok(status);
    }
}
