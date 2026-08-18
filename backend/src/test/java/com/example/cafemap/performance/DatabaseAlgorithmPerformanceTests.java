package com.example.cafemap.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.example.cafemap.cafe.infrastructure.CafeRepository;
import com.example.cafemap.menu.infrastructure.MenuItemRepository;
import com.example.cafemap.metric.infrastructure.CafeMetricRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("db-performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF"
})
class DatabaseAlgorithmPerformanceTests {

    private static final int CAFE_COUNT = Integer.getInteger("perfCafeCount", 1000);
    private static final int REPEAT_COUNT = 7;
    private static final int WARMUP_COUNT = 2;
    private static final boolean INCLUDE_EXPENSIVE_STRATEGIES = Boolean.parseBoolean(System.getProperty("perfIncludeExpensiveStrategies", "true"));
    private static final Bounds NARROW_BOUNDS = new Bounds("narrow", 37.49d, 126.94d, 37.53d, 127.00d);
    private static final Bounds MEDIUM_BOUNDS = new Bounds("medium", 37.45d, 126.90d, 37.57d, 127.05d);
    private static final Bounds WIDE_BOUNDS = new Bounds("wide", 37.40d, 126.80d, 37.80d, 127.20d);
    private static final List<String> SEARCH_STRATEGIES = List.of("APP_CONTAINS", "DB_LIKE", "NORMALIZED");
    private static final List<String> CORE_MARKER_STRATEGIES = List.of("UNSORTED_LIMIT", "BATCH_METRIC_HOT_RANK", "DB_HOT_RANK_LIMIT");
    private static final List<String> ALL_MARKER_STRATEGIES = List.of("UNSORTED_LIMIT", "HOT_RANK_LIMIT", "BATCH_METRIC_HOT_RANK", "DB_HOT_RANK_LIMIT");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    CafeRepository cafeRepository;

    @Autowired
    MenuItemRepository menuItemRepository;

    @Autowired
    CafeMetricRepository cafeMetricRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private String dbLabel;
    private String prefix;

    @BeforeAll
    void seed() {
        dbLabel = System.getProperty("db.profile.label", "unknown");
        prefix = "DbPerf" + dbLabel.substring(0, 1).toUpperCase() + dbLabel.substring(1);
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        deleteExistingRows();
        new PerformanceSeedFactory(cafeRepository, menuItemRepository, cafeMetricRepository).seed(prefix, CAFE_COUNT);
        System.out.printf("[db-seed] db=%s cafeCount=%d prefix=%s%n", dbLabel, CAFE_COUNT, prefix);
    }

    @Test
    void compareSearchStrategiesOnSelectedDatabase() throws Exception {
        for (String strategy : SEARCH_STRATEGIES) {
            statistics.clear();
            long startedAt = System.nanoTime();
            String body = performSearch(strategy, WIDE_BOUNDS).body();
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            int resultCount = objectMapper.readTree(body).get("items").size();
            System.out.printf(
                    "[db-perf] db=%s scenario=search strategy=%s elapsedMs=%d resultCount=%d queryCount=%d%n",
                    dbLabel,
                    strategy,
                    elapsedMs,
                    resultCount,
                    statistics.getPrepareStatementCount());
            assertThat(resultCount).isGreaterThan(0);
        }
    }

    @Test
    void compareMarkerStrategiesOnSelectedDatabase() throws Exception {
        for (String strategy : markerStrategies()) {
            statistics.clear();
            long startedAt = System.nanoTime();
            String body = performMarkers(strategy, WIDE_BOUNDS).body();
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            int resultCount = objectMapper.readTree(body).get("markers").size();
            System.out.printf(
                    "[db-perf] db=%s scenario=markers strategy=%s elapsedMs=%d resultCount=%d queryCount=%d%n",
                    dbLabel,
                    strategy,
                    elapsedMs,
                    resultCount,
                    statistics.getPrepareStatementCount());
            assertThat(resultCount).isGreaterThan(0);
        }
    }

    @Test
    void repeatedSearchStrategies_PrintPercentilesAndQuality() throws Exception {
        for (String strategy : SEARCH_STRATEGIES) {
            for (int i = 0; i < WARMUP_COUNT; i++) {
                performSearch(strategy, WIDE_BOUNDS);
            }
            List<Long> elapsedTimes = new ArrayList<>();
            int resultCount = 0;
            double averageSearchScore = 0.0d;
            long queryCount = 0;
            for (int i = 0; i < REPEAT_COUNT; i++) {
                statistics.clear();
                MeasuredResponse response = performSearch(strategy, WIDE_BOUNDS);
                elapsedTimes.add(response.elapsedMs());
                JsonNode items = objectMapper.readTree(response.body()).get("items");
                resultCount = items.size();
                averageSearchScore = average(items, "searchScore");
                queryCount = statistics.getPrepareStatementCount();
            }
            System.out.printf(
                    "[db-perf-summary] db=%s scenario=search strategy=%s p50Ms=%d p95Ms=%d p99Ms=%d resultCount=%d queryCount=%d avgSearchScore=%.2f%n",
                    dbLabel,
                    strategy,
                    percentile(elapsedTimes, 50),
                    percentile(elapsedTimes, 95),
                    percentile(elapsedTimes, 99),
                    resultCount,
                    queryCount,
                    averageSearchScore);
        }
    }

    @Test
    void repeatedMarkerStrategies_PrintPercentilesAndQuality() throws Exception {
        Set<Long> topHotCafeIds = topHotCafeIds(WIDE_BOUNDS, 300);
        for (String strategy : markerStrategies()) {
            for (int i = 0; i < WARMUP_COUNT; i++) {
                performMarkers(strategy, WIDE_BOUNDS);
            }
            List<Long> elapsedTimes = new ArrayList<>();
            int resultCount = 0;
            double averageHotScore = 0.0d;
            double topHotCoverage = 0.0d;
            long queryCount = 0;
            for (int i = 0; i < REPEAT_COUNT; i++) {
                statistics.clear();
                MeasuredResponse response = performMarkers(strategy, WIDE_BOUNDS);
                elapsedTimes.add(response.elapsedMs());
                JsonNode markers = objectMapper.readTree(response.body()).get("markers");
                resultCount = markers.size();
                averageHotScore = average(markers, "hotScore");
                topHotCoverage = topHotCoverage(markers, topHotCafeIds);
                queryCount = statistics.getPrepareStatementCount();
            }
            System.out.printf(
                    "[db-perf-summary] db=%s scenario=markers strategy=%s p50Ms=%d p95Ms=%d p99Ms=%d resultCount=%d queryCount=%d avgHotScore=%.2f topHotCoverage=%.3f%n",
                    dbLabel,
                    strategy,
                    percentile(elapsedTimes, 50),
                    percentile(elapsedTimes, 95),
                    percentile(elapsedTimes, 99),
                    resultCount,
                    queryCount,
                    averageHotScore,
                    topHotCoverage);
        }
    }

    @Test
    void boundsSelectivity_PrintLatencyAndQualityByBoundsSize() throws Exception {
        for (Bounds bounds : new Bounds[]{NARROW_BOUNDS, MEDIUM_BOUNDS, WIDE_BOUNDS}) {
            int candidateCount = candidateCount(bounds);
            for (String strategy : CORE_MARKER_STRATEGIES) {
                Set<Long> topHotCafeIds = topHotCafeIds(bounds, 300);
                List<Long> elapsedTimes = new ArrayList<>();
                int resultCount = 0;
                double averageHotScore = 0.0d;
                double topHotCoverage = 0.0d;
                long queryCount = 0;
                for (int i = 0; i < WARMUP_COUNT; i++) {
                    performMarkers(strategy, bounds);
                }
                for (int i = 0; i < REPEAT_COUNT; i++) {
                    statistics.clear();
                    MeasuredResponse response = performMarkers(strategy, bounds);
                    elapsedTimes.add(response.elapsedMs());
                    JsonNode markers = objectMapper.readTree(response.body()).get("markers");
                    resultCount = markers.size();
                    averageHotScore = average(markers, "hotScore");
                    topHotCoverage = topHotCoverage(markers, topHotCafeIds);
                    queryCount = statistics.getPrepareStatementCount();
                }
                System.out.printf(
                        "[db-bounds-summary] db=%s cafeCount=%d scenario=markers bounds=%s candidates=%d strategy=%s p50Ms=%d p95Ms=%d p99Ms=%d resultCount=%d queryCount=%d avgHotScore=%.2f topHotCoverage=%.3f%n",
                        dbLabel,
                        CAFE_COUNT,
                        bounds.name(),
                        candidateCount,
                        strategy,
                        percentile(elapsedTimes, 50),
                        percentile(elapsedTimes, 95),
                        percentile(elapsedTimes, 99),
                        resultCount,
                        queryCount,
                        averageHotScore,
                        topHotCoverage);
            }
        }
    }

    @Test
    void searchQuality_PrintTop10AndMatchedFieldDistribution() throws Exception {
        for (String strategy : SEARCH_STRATEGIES) {
            statistics.clear();
            MeasuredResponse response = performSearch(strategy, WIDE_BOUNDS);
            JsonNode items = objectMapper.readTree(response.body()).get("items");
            double averageSearchScore = average(items, "searchScore");
            double top10SearchScore = averageFirst(items, "searchScore", 10);
            Map<String, Integer> matchedFields = matchedFieldCounts(items);
            System.out.printf(
                    "[db-search-quality] db=%s cafeCount=%d strategy=%s elapsedMs=%d resultCount=%d queryCount=%d avgSearchScore=%.2f top10AvgSearchScore=%.2f matchedFields=%s%n",
                    dbLabel,
                    CAFE_COUNT,
                    strategy,
                    response.elapsedMs(),
                    items.size(),
                    statistics.getPrepareStatementCount(),
                    averageSearchScore,
                    top10SearchScore,
                    matchedFields);
        }
    }

    @Test
    void explainDatabaseQueryPlans_PrintPlans() {
        printExplain("search_db_like", """
                select c.id, c.name, m.name, cm.hot_score
                from cafes c
                join menu_items m on m.cafe_id = c.id
                left join cafe_metrics cm on cm.cafe_id = c.id
                where c.status = 'ACTIVE'
                  and m.status = 'ACTIVE'
                  and m.representative = true
                  and c.latitude between 37.40 and 37.80
                  and c.longitude between 126.80 and 127.20
                  and (
                    lower(c.name) like '%latte%'
                    or lower(c.address) like '%latte%'
                    or lower(c.road_address) like '%latte%'
                    or lower(m.name) like '%latte%'
                  )
                """);
        printExplain("marker_bounds", """
                select c.id, c.latitude, c.longitude
                from cafes c
                where c.status = 'ACTIVE'
                  and c.latitude between 37.40 and 37.80
                  and c.longitude between 126.80 and 127.20
                """);
        printExplain("batch_metric_lookup", """
                select cm.*
                from cafe_metrics cm
                where cm.cafe_id in (
                    select c.id
                    from cafes c
                    where c.status = 'ACTIVE'
                      and c.latitude between 37.40 and 37.80
                      and c.longitude between 126.80 and 127.20
                )
                """);
        printExplain("db_hot_rank_projection", """
                select c.id, c.latitude, c.longitude, m.image_thumbnail_url, cm.hot_score, cm.rating_average, cm.rating_count
                from cafes c
                join menu_items m on m.cafe_id = c.id
                left join cafe_metrics cm on cm.cafe_id = c.id
                where c.status = 'ACTIVE'
                  and m.status = 'ACTIVE'
                  and m.representative = true
                  and c.latitude between 37.40 and 37.80
                  and c.longitude between 126.80 and 127.20
                order by cm.hot_score desc, cm.rating_average desc, cm.rating_count desc, c.id asc
                limit 300
                """);
    }

    private List<String> markerStrategies() {
        return INCLUDE_EXPENSIVE_STRATEGIES ? ALL_MARKER_STRATEGIES : CORE_MARKER_STRATEGIES;
    }

    private MeasuredResponse performSearch(String strategy, Bounds bounds) throws Exception {
        long startedAt = System.nanoTime();
        String body = mockMvc.perform(get("/api/v1/search/cafes")
                        .param("query", prefix + " Latte")
                        .param("swLat", Double.toString(bounds.swLat()))
                        .param("swLng", Double.toString(bounds.swLng()))
                        .param("neLat", Double.toString(bounds.neLat()))
                        .param("neLng", Double.toString(bounds.neLng()))
                        .param("limit", "100")
                        .param("strategy", strategy))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new MeasuredResponse((System.nanoTime() - startedAt) / 1_000_000, body);
    }

    private MeasuredResponse performMarkers(String strategy, Bounds bounds) throws Exception {
        long startedAt = System.nanoTime();
        String body = mockMvc.perform(get("/api/v1/cafes/markers")
                        .param("swLat", Double.toString(bounds.swLat()))
                        .param("swLng", Double.toString(bounds.swLng()))
                        .param("neLat", Double.toString(bounds.neLat()))
                        .param("neLng", Double.toString(bounds.neLng()))
                        .param("zoom", "13")
                        .param("strategy", strategy))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new MeasuredResponse((System.nanoTime() - startedAt) / 1_000_000, body);
    }

    private long percentile(List<Long> values, int percentile) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0d * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private double average(JsonNode nodes, String fieldName) {
        if (nodes.isEmpty()) {
            return 0.0d;
        }
        double total = 0.0d;
        for (JsonNode node : nodes) {
            total += node.get(fieldName).asDouble();
        }
        return total / nodes.size();
    }

    private double averageFirst(JsonNode nodes, String fieldName, int limit) {
        if (nodes.isEmpty()) {
            return 0.0d;
        }
        double total = 0.0d;
        int count = Math.min(nodes.size(), limit);
        for (int i = 0; i < count; i++) {
            total += nodes.get(i).get(fieldName).asDouble();
        }
        return total / count;
    }

    private Map<String, Integer> matchedFieldCounts(JsonNode items) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (JsonNode item : items) {
            String field = item.get("matchedField").asText();
            counts.merge(field, 1, Integer::sum);
        }
        return counts;
    }

    private int candidateCount(Bounds bounds) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from cafes c
                join menu_items m on m.cafe_id = c.id
                where c.status = 'ACTIVE'
                  and m.status = 'ACTIVE'
                  and m.representative = true
                  and c.latitude between ? and ?
                  and c.longitude between ? and ?
                """, Integer.class, bounds.swLat(), bounds.neLat(), bounds.swLng(), bounds.neLng());
        return count == null ? 0 : count;
    }

    private Set<Long> topHotCafeIds(Bounds bounds, int limit) {
        List<Long> ids = jdbcTemplate.queryForList("""
                select c.id
                from cafes c
                join menu_items m on m.cafe_id = c.id
                left join cafe_metrics cm on cm.cafe_id = c.id
                where c.status = 'ACTIVE'
                  and m.status = 'ACTIVE'
                  and m.representative = true
                  and c.latitude between ? and ?
                  and c.longitude between ? and ?
                order by cm.hot_score desc, cm.rating_average desc, cm.rating_count desc, c.id asc
                limit ?
                """, Long.class, bounds.swLat(), bounds.neLat(), bounds.swLng(), bounds.neLng(), limit);
        return new HashSet<>(ids);
    }

    private double topHotCoverage(JsonNode markers, Set<Long> topHotCafeIds) {
        if (markers.isEmpty()) {
            return 0.0d;
        }
        int included = 0;
        for (JsonNode marker : markers) {
            if (topHotCafeIds.contains(marker.get("cafeId").asLong())) {
                included++;
            }
        }
        return included / (double) Math.min(markers.size(), topHotCafeIds.size());
    }

    private void printExplain(String name, String sql) {
        String explainSql = dbLabel.equals("mysql") ? "explain " + sql : "explain " + sql;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(explainSql);
        for (Map<String, Object> row : rows) {
            System.out.printf("[db-explain] db=%s query=%s row=%s%n", dbLabel, name, row);
        }
    }

    private void deleteExistingRows() {
        jdbcTemplate.update("delete from cafe_metrics");
        jdbcTemplate.update("delete from menu_items");
        jdbcTemplate.update("delete from cafes");
    }

    private record MeasuredResponse(long elapsedMs, String body) {
    }

    private record Bounds(String name, double swLat, double swLng, double neLat, double neLng) {
    }
}
