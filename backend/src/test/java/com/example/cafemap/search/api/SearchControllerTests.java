package com.example.cafemap.search.api;

import com.example.cafemap.cafe.domain.Cafe;
import com.example.cafemap.cafe.infrastructure.CafeRepository;
import com.example.cafemap.menu.domain.MenuItem;
import com.example.cafemap.menu.infrastructure.MenuItemRepository;
import com.example.cafemap.metric.domain.CafeMetric;
import com.example.cafemap.metric.infrastructure.CafeMetricRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SearchControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CafeRepository cafeRepository;

    @Autowired
    MenuItemRepository menuItemRepository;

    @Autowired
    CafeMetricRepository cafeMetricRepository;

    @Test
    void searchCafes_AppContainsStrategy_ReturnsActiveCafeWithRepresentativeMenu() throws Exception {
        Cafe cafe = activeCafe("App Contains Search Cafe", "Seoul Seongsu", 37.5446, 127.0557);
        activeRepresentativeMenu(cafe.getId(), "AppContains Signature Latte", "Creamy coffee");

        mockMvc.perform(get("/api/v1/search/cafes")
                        .param("query", "AppContains")
                        .param("strategy", "APP_CONTAINS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("APP_CONTAINS"))
                .andExpect(jsonPath("$.elapsedMs").exists())
                .andExpect(jsonPath("$.items[0].cafeId").value(cafe.getId()))
                .andExpect(jsonPath("$.items[0].matchedField").value("MENU_NAME"))
                .andExpect(jsonPath("$.items[0].representativeMenuName").value("AppContains Signature Latte"))
                .andExpect(jsonPath("$.items[0].searchScore").exists());
    }

    @Test
    void searchCafes_DbLikeStrategy_RanksCafeNameBeforeMenuName() throws Exception {
        Cafe menuMatchedCafe = activeCafe("A DbLike Menu Match Cafe", "Seoul Mapo", 37.55, 126.91);
        activeRepresentativeMenu(menuMatchedCafe.getId(), "DbLikeBerry Cake", "Fresh berry dessert");
        Cafe cafeNameMatchedCafe = activeCafe("DbLikeBerry House", "Seoul Gangnam", 37.49, 127.03);
        activeRepresentativeMenu(cafeNameMatchedCafe.getId(), "Vanilla Cake", "House dessert");

        mockMvc.perform(get("/api/v1/search/cafes")
                        .param("query", "DbLikeBerry")
                        .param("strategy", "DB_LIKE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("DB_LIKE"))
                .andExpect(jsonPath("$.items[0].cafeId").value(cafeNameMatchedCafe.getId()))
                .andExpect(jsonPath("$.items[0].matchedField").value("CAFE_NAME"))
                .andExpect(jsonPath("$.items[0].searchScore").value(60.0));
    }

    @Test
    void searchCafes_BoundsProvided_FiltersByMapBounds() throws Exception {
        Cafe nearCafe = activeCafe("BoundsTarget Near Cafe", "Seoul", 37.5446, 127.0557);
        activeRepresentativeMenu(nearCafe.getId(), "BoundsTarget Latte", "near");
        Cafe farCafe = activeCafe("BoundsTarget Far Cafe", "Busan", 35.1796, 129.0756);
        activeRepresentativeMenu(farCafe.getId(), "BoundsTarget Latte", "far");

        mockMvc.perform(get("/api/v1/search/cafes")
                        .param("query", "BoundsTarget")
                        .param("strategy", "DB_LIKE")
                        .param("swLat", "37.0")
                        .param("swLng", "126.0")
                        .param("neLat", "38.0")
                        .param("neLng", "128.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(lessThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].cafeId").value(nearCafe.getId()));
    }

    @Test
    void searchCafes_NormalizedStrategy_MatchesWhitespaceInsensitiveQuery() throws Exception {
        Cafe cafe = activeCafe("Normalized Match Cafe", "Seoul", 37.5446, 127.0557);
        activeRepresentativeMenu(cafe.getId(), "Matcha Latte", "normalized");

        mockMvc.perform(get("/api/v1/search/cafes")
                        .param("query", "matchalatte")
                        .param("strategy", "NORMALIZED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("NORMALIZED"))
                .andExpect(jsonPath("$.items[0].cafeId").value(cafe.getId()));
    }

    @Test
    void searchCafes_BlankQuery_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/search/cafes")
                        .param("query", " "))
                .andExpect(status().isBadRequest());
    }

    private Cafe activeCafe(String name, String roadAddress, double latitude, double longitude) {
        Cafe cafe = cafeRepository.save(new Cafe(
                name,
                "Search test cafe",
                roadAddress,
                roadAddress,
                latitude,
                longitude,
                null,
                null));
        cafe.approve();
        cafeMetricRepository.save(new CafeMetric(cafe.getId()));
        return cafeRepository.save(cafe);
    }

    private MenuItem activeRepresentativeMenu(Long cafeId, String name, String description) {
        MenuItem menuItem = new MenuItem(
                cafeId,
                name,
                BigDecimal.valueOf(6500),
                description,
                "https://example.com/original.jpg",
                "https://example.com/thumb.jpg",
                LocalDate.now());
        menuItem.setRepresentative(true);
        menuItem.approve();
        return menuItemRepository.save(menuItem);
    }
}
