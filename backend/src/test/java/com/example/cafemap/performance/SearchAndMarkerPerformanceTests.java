package com.example.cafemap.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF"
})
class SearchAndMarkerPerformanceTests {

    private static final String PREFIX = "PerfFixture";
    private static final int CAFE_COUNT = 1000;

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
    EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeAll
    void seed() {
        new PerformanceSeedFactory(cafeRepository, menuItemRepository, cafeMetricRepository).seed(PREFIX, CAFE_COUNT);
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @Test
    void markerStrategies_PrintElapsedTimeAndQueryCount() throws Exception {
        for (String strategy : new String[]{"UNSORTED_LIMIT", "HOT_RANK_LIMIT", "BATCH_METRIC_HOT_RANK"}) {
            statistics.clear();
            long startedAt = System.nanoTime();
            String body = mockMvc.perform(get("/api/v1/cafes/markers")
                            .param("swLat", "37.40")
                            .param("swLng", "126.80")
                            .param("neLat", "37.80")
                            .param("neLng", "127.20")
                            .param("zoom", "13")
                            .param("strategy", strategy))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            int resultCount = objectMapper.readTree(body).get("markers").size();
            System.out.printf(
                    "[perf] marker strategy=%s elapsedMs=%d resultCount=%d queryCount=%d%n",
                    strategy,
                    elapsedMs,
                    resultCount,
                    statistics.getPrepareStatementCount());
            assertThat(body).contains("\"markers\"");
        }
    }

    @Test
    void searchStrategies_PrintElapsedTimeAndResultCount() throws Exception {
        for (String strategy : new String[]{"APP_CONTAINS", "DB_LIKE", "NORMALIZED"}) {
            statistics.clear();
            long startedAt = System.nanoTime();
            String body = mockMvc.perform(get("/api/v1/search/cafes")
                            .param("query", PREFIX + " Latte")
                            .param("swLat", "37.40")
                            .param("swLng", "126.80")
                            .param("neLat", "37.80")
                            .param("neLng", "127.20")
                            .param("limit", "100")
                            .param("strategy", strategy))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            int resultCount = objectMapper.readTree(body).get("items").size();
            System.out.printf(
                    "[perf] search strategy=%s elapsedMs=%d resultCount=%d queryCount=%d%n",
                    strategy,
                    elapsedMs,
                    resultCount,
                    statistics.getPrepareStatementCount());
            assertThat(body).contains("\"items\"");
        }
    }
}
