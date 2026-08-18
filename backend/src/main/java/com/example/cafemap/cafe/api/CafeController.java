package com.example.cafemap.cafe.api;

import com.example.cafemap.cafe.application.CafeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CafeController {

    private final CafeService cafeService;

    public CafeController(CafeService cafeService) {
        this.cafeService = cafeService;
    }

    @GetMapping("/cafes/markers")
    public MarkerResponse markers(
            @RequestParam double swLat,
            @RequestParam double swLng,
            @RequestParam double neLat,
            @RequestParam double neLng,
            @RequestParam int zoom,
            @RequestParam(defaultValue = "BATCH_METRIC_HOT_RANK") String strategy) {
        return cafeService.markers(swLat, swLng, neLat, neLng, zoom, strategy);
    }

    public record MarkerResponse(String strategy, long elapsedMs, List<MarkerItem> markers) {
    }

    public record MarkerItem(
            Long cafeId,
            double lat,
            double lng,
            String thumbnailUrl,
            String badge,
            BigDecimal hotScore,
            BigDecimal ratingAverage,
            long ratingCount,
            boolean isNew) {
    }
}
