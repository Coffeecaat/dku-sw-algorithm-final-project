package com.example.cafemap.search.application;

import java.util.Locale;

public enum SearchStrategyType {
    APP_CONTAINS,
    DB_LIKE,
    NORMALIZED;

    public static SearchStrategyType from(String value) {
        return SearchStrategyType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
