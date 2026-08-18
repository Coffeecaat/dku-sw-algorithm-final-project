package com.example.cafemap.search.infrastructure;

import com.example.cafemap.cafe.domain.Cafe;
import com.example.cafemap.menu.domain.MenuItem;
import com.example.cafemap.search.api.SearchController.CafeSearchItem;
import com.example.cafemap.search.application.CafeSearchCommand;
import com.example.cafemap.search.application.CafeSearchStrategy;
import com.example.cafemap.search.application.MatchedField;
import com.example.cafemap.search.application.SearchStrategyType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NormalizedCafeSearchStrategy extends AbstractCafeSearchStrategy implements CafeSearchStrategy {

    public NormalizedCafeSearchStrategy(SearchCafeRepository searchCafeRepository) {
        super(searchCafeRepository);
    }

    @Override
    public SearchStrategyType type() {
        return SearchStrategyType.NORMALIZED;
    }

    @Override
    public List<CafeSearchItem> search(CafeSearchCommand command) {
        return appCandidates(command).stream()
                .filter(row -> normalizedMatchesAny(row, command.compactNormalizedQuery()))
                .map(row -> scoredItem(row, command))
                .sorted(commonRanking())
                .limit(command.limit())
                .toList();
    }

    private CafeSearchItem scoredItem(CafeSearchRow row, CafeSearchCommand command) {
        Cafe cafe = row.cafe();
        MenuItem menu = row.representativeMenu();
        MatchedField field = matchedField(cafe, menu, command.normalizedQuery());
        return item(row, field, matchedText(cafe, menu, field), normalizedScore(row, command.compactNormalizedQuery()));
    }
}
