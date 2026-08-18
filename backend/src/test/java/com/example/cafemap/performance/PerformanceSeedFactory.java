package com.example.cafemap.performance;

import com.example.cafemap.cafe.domain.Cafe;
import com.example.cafemap.cafe.infrastructure.CafeRepository;
import com.example.cafemap.menu.domain.MenuItem;
import com.example.cafemap.menu.infrastructure.MenuItemRepository;
import com.example.cafemap.metric.domain.CafeMetric;
import com.example.cafemap.metric.infrastructure.CafeMetricRepository;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PerformanceSeedFactory {

    private final CafeRepository cafeRepository;
    private final MenuItemRepository menuItemRepository;
    private final CafeMetricRepository cafeMetricRepository;

    public PerformanceSeedFactory(
            CafeRepository cafeRepository,
            MenuItemRepository menuItemRepository,
            CafeMetricRepository cafeMetricRepository) {
        this.cafeRepository = cafeRepository;
        this.menuItemRepository = menuItemRepository;
        this.cafeMetricRepository = cafeMetricRepository;
    }

    public void seed(String prefix, int cafeCount) {
        for (int i = 0; i < cafeCount; i++) {
            double latitude = 37.45d + (i % 100) * 0.002d;
            double longitude = 126.90d + (i / 100) * 0.01d;
            Cafe cafe = cafeRepository.save(new Cafe(
                    prefix + " Cafe " + i,
                    "Performance fixture cafe",
                    "Seoul test address " + i,
                    "Seoul test road address " + i,
                    latitude,
                    longitude,
                    null,
                    "10:00-22:00"));
            cafe.approve();
            cafe = cafeRepository.save(cafe);

            int menuCount = 1 + (i % 3);
            for (int menuIndex = 0; menuIndex < menuCount; menuIndex++) {
                MenuItem menuItem = new MenuItem(
                        cafe.getId(),
                        menuName(prefix, i, menuIndex),
                        BigDecimal.valueOf(4500 + menuIndex * 500L),
                        "Performance menu " + menuIndex,
                        "https://example.com/perf-original.jpg",
                        "https://example.com/perf-thumb.jpg",
                        LocalDate.now().minusDays(i % 30));
                menuItem.setRepresentative(menuIndex == 0);
                menuItem.approve();
                menuItemRepository.save(menuItem);
            }

            CafeMetric metric = new CafeMetric(cafe.getId());
            for (int view = 0; view < i % 25; view++) {
                metric.viewed();
            }
            metric.updateRating(BigDecimal.valueOf(3.0d + (i % 20) / 10.0d), i % 80);
            cafeMetricRepository.save(metric);
        }
    }

    private String menuName(String prefix, int cafeIndex, int menuIndex) {
        if (menuIndex == 0 && cafeIndex % 2 == 0) {
            return prefix + " Latte " + cafeIndex;
        }
        if (menuIndex == 1) {
            return prefix + " Cake " + cafeIndex;
        }
        return prefix + " Menu " + cafeIndex + "-" + menuIndex;
    }
}
