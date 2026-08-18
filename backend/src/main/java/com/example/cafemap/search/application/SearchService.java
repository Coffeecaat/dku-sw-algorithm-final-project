package com.example.cafemap.search.application;

import com.example.cafemap.common.error.ApiException;
import com.example.cafemap.search.api.SearchController.CafeSearchResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SearchService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final Map<SearchStrategyType, CafeSearchStrategy> strategies = new EnumMap<>(SearchStrategyType.class);

    public SearchService(List<CafeSearchStrategy> strategies) {
        strategies.forEach(strategy -> this.strategies.put(strategy.type(), strategy));
    }

    public CafeSearchResponse searchCafes(
            String query,
            Double lat,
            Double lng,
            Double radius,
            Double swLat,
            Double swLng,
            Double neLat,
            Double neLng,
            int limit,
            String strategy) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Search query is required");
        }
        SearchStrategyType strategyType = strategyType(strategy);
        CafeSearchStrategy searchStrategy = strategies.get(strategyType);
        if (searchStrategy == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported search strategy");
        }

        int boundedLimit = boundLimit(limit);
        CafeSearchCommand command = new CafeSearchCommand(
                query.trim(),
                normalizedQuery,
                compactNormalize(query),
                lat,
                lng,
                normalizeRadius(radius),
                swLat,
                swLng,
                neLat,
                neLng,
                boundedLimit);
        long startedAt = System.nanoTime();
        var items = searchStrategy.search(command);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        return new CafeSearchResponse(searchStrategy.type().name(), elapsedMs, items);
    }

    private String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private String compactNormalize(String query) {
        return normalize(query).replaceAll("\\s+", "");
    }

    private int boundLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Double normalizeRadius(Double radius) {
        if (radius == null || radius <= 0) {
            return null;
        }
        return radius;
    }

    private SearchStrategyType strategyType(String strategy) {
        try {
            return SearchStrategyType.from(strategy);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported search strategy");
        }
    }
}
