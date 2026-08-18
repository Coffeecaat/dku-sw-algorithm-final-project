package com.example.cafemap.search.application;

import com.example.cafemap.search.api.SearchController.CafeSearchItem;

import java.util.List;

public interface CafeSearchStrategy {

    SearchStrategyType type();

    List<CafeSearchItem> search(CafeSearchCommand command);
}
