package com.example.cafemap.cafe.application;

import com.example.cafemap.cafe.api.CafeController.MarkerItem;
import com.example.cafemap.cafe.api.CafeController.MarkerResponse;
import com.example.cafemap.cafe.domain.Cafe;
import com.example.cafemap.cafe.domain.CafeStatus;
import com.example.cafemap.cafe.infrastructure.CafeMarkerProjectionRepository;
import com.example.cafemap.cafe.infrastructure.CafeRepository;
import com.example.cafemap.cafe.infrastructure.MarkerProjectionRow;
import com.example.cafemap.common.error.ApiException;
import com.example.cafemap.menu.domain.MenuItem;
import com.example.cafemap.menu.domain.MenuStatus;
import com.example.cafemap.menu.infrastructure.MenuItemRepository;
import com.example.cafemap.metric.domain.CafeMetric;
import com.example.cafemap.metric.infrastructure.CafeMetricRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CafeService {

    private final CafeRepository cafeRepository;
    private final CafeMarkerProjectionRepository cafeMarkerProjectionRepository;
    private final MenuItemRepository menuItemRepository;
    private final CafeMetricRepository cafeMetricRepository;

    public CafeService(
            CafeRepository cafeRepository,
            CafeMarkerProjectionRepository cafeMarkerProjectionRepository,
            MenuItemRepository menuItemRepository,
            CafeMetricRepository cafeMetricRepository) {
        this.cafeRepository = cafeRepository;
        this.cafeMarkerProjectionRepository = cafeMarkerProjectionRepository;
        this.menuItemRepository = menuItemRepository;
        this.cafeMetricRepository = cafeMetricRepository;
    }

    @Transactional(readOnly = true)
    public MarkerResponse markers(double swLat, double swLng, double neLat, double neLng, int zoom, String strategy) {
        long startedAt = System.nanoTime();
        MarkerStrategyType strategyType = markerStrategy(strategy);
        double minLat = Math.min(swLat, neLat);
        double maxLat = Math.max(swLat, neLat);
        double minLng = Math.min(swLng, neLng);
        double maxLng = Math.max(swLng, neLng);
        int limit = markerLimit(zoom);
        if (strategyType == MarkerStrategyType.DB_HOT_RANK_LIMIT) {
            List<MarkerItem> markers = dbHotRankLimitMarkers(minLat, maxLat, minLng, maxLng, limit);
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            return new MarkerResponse(strategyType.name(), elapsedMs, markers);
        }
        List<Cafe> cafes = cafeRepository.findByStatusAndLatitudeBetweenAndLongitudeBetween(
                CafeStatus.ACTIVE, minLat, maxLat, minLng, maxLng);
        Map<Long, MenuItem> representativeMenus = menuItemRepository
                .findByCafeIdInAndRepresentativeTrueAndStatus(cafes.stream().map(Cafe::getId).toList(), MenuStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(MenuItem::getCafeId, Function.identity(), (left, right) -> left));
        List<MarkerItem> markers = switch (strategyType) {
            case UNSORTED_LIMIT -> unsortedLimitMarkers(cafes, representativeMenus, limit);
            case HOT_RANK_LIMIT -> hotRankLimitMarkers(cafes, representativeMenus, limit);
            case BATCH_METRIC_HOT_RANK -> batchMetricHotRankMarkers(cafes, representativeMenus, limit);
            case DB_HOT_RANK_LIMIT -> throw new IllegalStateException("DB projection strategy should return early");
        };
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        return new MarkerResponse(strategyType.name(), elapsedMs, markers);
    }

    @Transactional(readOnly = true)
    public MarkerResponse markers(double swLat, double swLng, double neLat, double neLng, int zoom) {
        return markers(swLat, swLng, neLat, neLng, zoom, MarkerStrategyType.BATCH_METRIC_HOT_RANK.name());
    }

    private List<MarkerItem> unsortedLimitMarkers(List<Cafe> cafes, Map<Long, MenuItem> representativeMenus, int limit) {
        return cafes.stream()
                .filter(cafe -> representativeMenus.containsKey(cafe.getId()))
                .limit(limit)
                .map(cafe -> marker(cafe, representativeMenus.get(cafe.getId()), metric(cafe.getId())))
                .toList();
    }

    private List<MarkerItem> hotRankLimitMarkers(List<Cafe> cafes, Map<Long, MenuItem> representativeMenus, int limit) {
        return cafes.stream()
                .filter(cafe -> representativeMenus.containsKey(cafe.getId()))
                .map(cafe -> marker(cafe, representativeMenus.get(cafe.getId()), metric(cafe.getId())))
                .sorted(markerRanking())
                .limit(limit)
                .toList();
    }

    private List<MarkerItem> batchMetricHotRankMarkers(List<Cafe> cafes, Map<Long, MenuItem> representativeMenus, int limit) {
        Map<Long, CafeMetric> metrics = cafeMetricRepository.findByCafeIdIn(cafes.stream().map(Cafe::getId).toList())
                .stream()
                .collect(Collectors.toMap(CafeMetric::getCafeId, Function.identity()));
        return cafes.stream()
                .filter(cafe -> representativeMenus.containsKey(cafe.getId()))
                .map(cafe -> marker(cafe, representativeMenus.get(cafe.getId()), metrics.getOrDefault(cafe.getId(), new CafeMetric(cafe.getId()))))
                .sorted(markerRanking())
                .limit(limit)
                .toList();
    }

    private CafeMetric metric(Long cafeId) {
        return cafeMetricRepository.findById(cafeId).orElseGet(() -> new CafeMetric(cafeId));
    }

    private List<MarkerItem> dbHotRankLimitMarkers(
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude,
            int limit) {
        return cafeMarkerProjectionRepository
                .findHotRankedMarkers(minLatitude, maxLatitude, minLongitude, maxLongitude, limit)
                .stream()
                .map(this::marker)
                .toList();
    }

    private Comparator<MarkerItem> markerRanking() {
        return Comparator.comparing(MarkerItem::hotScore, Comparator.reverseOrder())
                .thenComparing(MarkerItem::ratingAverage, Comparator.reverseOrder())
                .thenComparing(MarkerItem::ratingCount, Comparator.reverseOrder())
                .thenComparing(MarkerItem::cafeId);
    }

    private int markerLimit(int zoom) {
        return zoom >= 11 ? 300 : 80;
    }

    private MarkerStrategyType markerStrategy(String strategy) {
        try {
            return MarkerStrategyType.from(strategy);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported marker strategy");
        }
    }

    private MarkerItem marker(Cafe cafe, MenuItem menuItem, CafeMetric metric) {
        boolean isNew = cafe.getActivatedAt() != null && cafe.getActivatedAt().isAfter(Instant.now().minusSeconds(60 * 60 * 24 * 7))
                || menuItem.getCreatedAt().isAfter(Instant.now().minusSeconds(60 * 60 * 24 * 7))
                || (menuItem.getReleasedAt() != null && menuItem.getReleasedAt().isAfter(LocalDate.now().minusDays(14)));
        String badge = metric.getHotScore().compareTo(BigDecimal.valueOf(20)) >= 0 ? "HOT" : isNew ? "NEW" : null;
        return new MarkerItem(
                cafe.getId(),
                cafe.getLatitude(),
                cafe.getLongitude(),
                menuItem.getImageThumbnailUrl(),
                badge,
                metric.getHotScore(),
                metric.getRatingAverage(),
                metric.getRatingCount(),
                isNew);
    }

    private MarkerItem marker(MarkerProjectionRow row) {
        BigDecimal hotScore = row.hotScore() == null ? BigDecimal.ZERO : row.hotScore();
        BigDecimal ratingAverage = row.ratingAverage() == null ? BigDecimal.ZERO : row.ratingAverage();
        long ratingCount = row.ratingCount() == null ? 0L : row.ratingCount();
        boolean isNew = row.cafeActivatedAt() != null && row.cafeActivatedAt().isAfter(Instant.now().minusSeconds(60 * 60 * 24 * 7))
                || row.menuCreatedAt().isAfter(Instant.now().minusSeconds(60 * 60 * 24 * 7))
                || (row.menuReleasedAt() != null && row.menuReleasedAt().isAfter(LocalDate.now().minusDays(14)));
        String badge = hotScore.compareTo(BigDecimal.valueOf(20)) >= 0 ? "HOT" : isNew ? "NEW" : null;
        return new MarkerItem(
                row.cafeId(),
                row.latitude(),
                row.longitude(),
                row.thumbnailUrl(),
                badge,
                hotScore,
                ratingAverage,
                ratingCount,
                isNew);
    }
}
