package com.example.cafemap.cafe.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record MarkerProjectionRow(
        Long cafeId,
        double latitude,
        double longitude,
        String thumbnailUrl,
        BigDecimal hotScore,
        BigDecimal ratingAverage,
        Long ratingCount,
        Instant cafeActivatedAt,
        Instant menuCreatedAt,
        LocalDate menuReleasedAt) {
}
