package com.example.cafemap.search.infrastructure;

import com.example.cafemap.cafe.domain.Cafe;
import com.example.cafemap.menu.domain.MenuItem;
import com.example.cafemap.metric.domain.CafeMetric;

public record CafeSearchRow(Cafe cafe, MenuItem representativeMenu, CafeMetric metric) {
}
