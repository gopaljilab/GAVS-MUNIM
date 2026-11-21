package org.munim.api;

import org.munim.intelligence.SupplierRecommendationDtos.SupplierRecommendationList;
import org.munim.intelligence.SupplierRecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1", "/api"})
public class SupplierRecommendationController {

    private final SupplierRecommendationService recommendationService;

    public SupplierRecommendationController(SupplierRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/rfqs/{id}/supplier-recommendations")
    public SupplierRecommendationList getSupplierRecommendations(@PathVariable UUID id) {
        return recommendationService.getRecommendationsForRfq(id);
    }
}
