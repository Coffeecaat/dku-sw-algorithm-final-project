package com.example.cafemap.search.api;

import com.example.cafemap.search.application.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/cafes")
    public CafeSearchResponse cafes(
            @RequestParam String query,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radius,
            @RequestParam(required = false) Double swLat,
            @RequestParam(required = false) Double swLng,
            @RequestParam(required = false) Double neLat,
            @RequestParam(required = false) Double neLng,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "DB_LIKE") String strategy) {
        return searchService.searchCafes(query, lat, lng, radius, swLat, swLng, neLat, neLng, limit, strategy);
    }

    public record CafeSearchResponse(
            String strategy,
            long elapsedMs,
            List<CafeSearchItem> items) {
    }

    public record CafeSearchItem(
            Long cafeId,
            String name,
            String address,
            String shortDescription,
            String representativeMenuName,
            String roadAddress,
            double latitude,
            double longitude,
            String thumbnailUrl,
            String matchedField,
            String matchedText,
            double searchScore,
            BigDecimal ratingAverage,
            long ratingCount,
            BigDecimal hotScore,
            String badge) {
    }
}
