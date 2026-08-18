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
public class DbLikeCafeSearchStrategy extends AbstractCafeSearchStrategy implements CafeSearchStrategy {

    public DbLikeCafeSearchStrategy(SearchCafeRepository searchCafeRepository) {
        super(searchCafeRepository);
    }

    @Override
    public SearchStrategyType type() {
        return SearchStrategyType.DB_LIKE;
    }

    @Override
    public List<CafeSearchItem> search(CafeSearchCommand command) {
        return dbLikeCandidates(command).stream()
                .map(row -> scoredItem(row, command.normalizedQuery()))
                .sorted(commonRanking())
                .limit(command.limit())
                .toList();
    }

    private CafeSearchItem scoredItem(CafeSearchRow row, String query) {
        Cafe cafe = row.cafe();
        MenuItem menu = row.representativeMenu();
        MatchedField field = matchedField(cafe, menu, query);
        return item(row, field, matchedText(cafe, menu, field), score(row, query));
    }
}
