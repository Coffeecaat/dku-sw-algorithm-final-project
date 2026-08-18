package com.example.cafemap.cafe.application;

import java.util.Locale;

public enum MarkerStrategyType {
    UNSORTED_LIMIT,
    HOT_RANK_LIMIT,
    BATCH_METRIC_HOT_RANK,
    DB_HOT_RANK_LIMIT;

    public static MarkerStrategyType from(String value) {
        return MarkerStrategyType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
