package com.example.cafemap.search.infrastructure;

import com.example.cafemap.cafe.domain.Cafe;
import com.example.cafemap.menu.domain.MenuItem;
import com.example.cafemap.metric.domain.CafeMetric;
import com.example.cafemap.search.api.SearchController.CafeSearchItem;
import com.example.cafemap.search.application.CafeSearchCommand;
import com.example.cafemap.search.application.MatchedField;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

abstract class AbstractCafeSearchStrategy {

    protected static final double EXACT_CAFE_NAME_WEIGHT = 100.0d;
    protected static final double CAFE_NAME_CONTAINS_WEIGHT = 60.0d;
    protected static final double MENU_NAME_CONTAINS_WEIGHT = 40.0d;
    protected static final double ADDRESS_CONTAINS_WEIGHT = 20.0d;
    protected static final double HOT_SCORE_WEIGHT = 0.10d;
    protected static final double RATING_AVERAGE_WEIGHT = 2.0d;
    protected static final double RATING_COUNT_WEIGHT = 0.05d;

    protected final SearchCafeRepository searchCafeRepository;

    protected AbstractCafeSearchStrategy(SearchCafeRepository searchCafeRepository) {
        this.searchCafeRepository = searchCafeRepository;
    }

    protected List<CafeSearchRow> appCandidates(CafeSearchCommand command) {
        return searchCafeRepository.findActiveCafeRows().stream()
                .filter(row -> withinBounds(row.cafe(), command))
                .filter(row -> withinRadius(row.cafe(), command))
                .toList();
    }

    protected List<CafeSearchRow> dbLikeCandidates(CafeSearchCommand command) {
        return searchCafeRepository.findActiveCafeRowsByLike(command.normalizedQuery(), command.hasBounds(),
                        command.hasBounds() ? command.minLatitude() : 0.0d,
                        command.hasBounds() ? command.maxLatitude() : 0.0d,
                        command.hasBounds() ? command.minLongitude() : 0.0d,
                        command.hasBounds() ? command.maxLongitude() : 0.0d)
                .stream()
                .filter(row -> withinRadius(row.cafe(), command))
                .toList();
    }

    protected CafeSearchItem item(CafeSearchRow row, MatchedField matchedField, String matchedText, double score) {
        Cafe cafe = row.cafe();
        MenuItem menu = row.representativeMenu();
        CafeMetric metric = metric(row);
        return new CafeSearchItem(
                cafe.getId(),
                cafe.getName(),
                cafe.getAddress(),
                cafe.getShortDescription(),
                menu.getName(),
                cafe.getRoadAddress(),
                cafe.getLatitude(),
                cafe.getLongitude(),
                menu.getImageThumbnailUrl(),
                matchedField.name(),
                matchedText,
                score,
                metric.getRatingAverage(),
                metric.getRatingCount(),
                metric.getHotScore(),
                badge(cafe, menu, metric));
    }

    protected MatchedField matchedField(Cafe cafe, MenuItem menu, String query) {
        if (contains(cafe.getName(), query)) {
            return MatchedField.CAFE_NAME;
        }
        if (contains(menu.getName(), query)) {
            return MatchedField.MENU_NAME;
        }
        if (contains(cafe.getRoadAddress(), query)) {
            return MatchedField.ROAD_ADDRESS;
        }
        if (contains(cafe.getAddress(), query)) {
            return MatchedField.ADDRESS;
        }
        return MatchedField.MENU_DESCRIPTION;
    }

    protected String matchedText(Cafe cafe, MenuItem menu, MatchedField matchedField) {
        return switch (matchedField) {
            case CAFE_NAME -> cafe.getName();
            case MENU_NAME -> menu.getName();
            case ROAD_ADDRESS -> cafe.getRoadAddress();
            case ADDRESS -> cafe.getAddress();
            case MENU_DESCRIPTION -> menu.getDescription();
        };
    }

    protected boolean exact(String value, String query) {
        return normalize(value).equals(query);
    }

    protected boolean contains(String value, String query) {
        return normalize(value).contains(query);
    }

    protected boolean compactContains(String value, String query) {
        return compactNormalize(value).contains(query);
    }

    protected double score(CafeSearchRow row, String query) {
        Cafe cafe = row.cafe();
        MenuItem menu = row.representativeMenu();
        CafeMetric metric = metric(row);
        double score = 0.0d;
        if (exact(cafe.getName(), query)) {
            score += EXACT_CAFE_NAME_WEIGHT;
        } else if (contains(cafe.getName(), query)) {
            score += CAFE_NAME_CONTAINS_WEIGHT;
        }
        if (contains(menu.getName(), query)) {
            score += MENU_NAME_CONTAINS_WEIGHT;
        }
        if (contains(cafe.getAddress(), query) || contains(cafe.getRoadAddress(), query)) {
            score += ADDRESS_CONTAINS_WEIGHT;
        }
        return score + metricScore(metric);
    }

    protected double normalizedScore(CafeSearchRow row, String query) {
        Cafe cafe = row.cafe();
        MenuItem menu = row.representativeMenu();
        CafeMetric metric = metric(row);
        double score = 0.0d;
        if (compactNormalize(cafe.getName()).equals(query)) {
            score += EXACT_CAFE_NAME_WEIGHT;
        } else if (compactContains(cafe.getName(), query)) {
            score += CAFE_NAME_CONTAINS_WEIGHT;
        }
        if (compactContains(menu.getName(), query)) {
            score += MENU_NAME_CONTAINS_WEIGHT;
        }
        if (compactContains(cafe.getAddress(), query) || compactContains(cafe.getRoadAddress(), query)) {
            score += ADDRESS_CONTAINS_WEIGHT;
        }
        return score + metricScore(metric);
    }

    protected boolean matchesAny(CafeSearchRow row, String query) {
        Cafe cafe = row.cafe();
        MenuItem menu = row.representativeMenu();
        return contains(cafe.getName(), query)
                || contains(cafe.getAddress(), query)
                || contains(cafe.getRoadAddress(), query)
                || contains(menu.getName(), query);
    }

    protected boolean normalizedMatchesAny(CafeSearchRow row, String query) {
        Cafe cafe = row.cafe();
        MenuItem menu = row.representativeMenu();
        return compactContains(cafe.getName(), query)
                || compactContains(cafe.getAddress(), query)
                || compactContains(cafe.getRoadAddress(), query)
                || compactContains(menu.getName(), query);
    }

    protected Comparator<CafeSearchItem> commonRanking() {
        return Comparator.comparingDouble(CafeSearchItem::searchScore).reversed()
                .thenComparing(CafeSearchItem::ratingAverage, Comparator.reverseOrder())
                .thenComparing(CafeSearchItem::ratingCount, Comparator.reverseOrder())
                .thenComparing(CafeSearchItem::name);
    }

    private CafeMetric metric(CafeSearchRow row) {
        return row.metric() == null ? new CafeMetric(row.cafe().getId()) : row.metric();
    }

    private String badge(Cafe cafe, MenuItem menu, CafeMetric metric) {
        boolean isNew = cafe.getActivatedAt() != null && cafe.getActivatedAt().isAfter(Instant.now().minusSeconds(60 * 60 * 24 * 7))
                || menu.getCreatedAt().isAfter(Instant.now().minusSeconds(60 * 60 * 24 * 7))
                || (menu.getReleasedAt() != null && menu.getReleasedAt().isAfter(LocalDate.now().minusDays(14)));
        if (metric.getHotScore().compareTo(BigDecimal.valueOf(20)) >= 0) {
            return "HOT";
        }
        return isNew ? "NEW" : null;
    }

    private boolean withinRadius(Cafe cafe, CafeSearchCommand command) {
        if (command.latitude() == null || command.longitude() == null || command.radiusKm() == null) {
            return true;
        }
        return distanceKm(command.latitude(), command.longitude(), cafe.getLatitude(), cafe.getLongitude()) <= command.radiusKm();
    }

    private boolean withinBounds(Cafe cafe, CafeSearchCommand command) {
        if (!command.hasBounds()) {
            return true;
        }
        return cafe.getLatitude() >= command.minLatitude()
                && cafe.getLatitude() <= command.maxLatitude()
                && cafe.getLongitude() >= command.minLongitude()
                && cafe.getLongitude() <= command.maxLongitude();
    }

    private double metricScore(CafeMetric metric) {
        return metric.getHotScore().doubleValue() * HOT_SCORE_WEIGHT
                + metric.getRatingAverage().doubleValue() * RATING_AVERAGE_WEIGHT
                + Math.min(metric.getRatingCount(), 200L) * RATING_COUNT_WEIGHT;
    }

    private double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusKm = 6371.0088d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String compactNormalize(String value) {
        return normalize(value).replaceAll("\\s+", "");
    }
}
