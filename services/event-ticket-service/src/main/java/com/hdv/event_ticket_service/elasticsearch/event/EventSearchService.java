package com.hdv.event_ticket_service.elasticsearch.event;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.hdv.event_ticket_service.elasticsearch.BaseElasticRepository;
import com.hdv.event_ticket_service.elasticsearch.BaseElasticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventSearchService extends BaseElasticService<EventElasticDocument, String> {

    private final EventElasticRepository eventElasticRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    protected BaseElasticRepository<EventElasticDocument, String> getRepository() {
        return eventElasticRepository;
    }

    @Override
    protected ElasticsearchOperations getOperations() {
        return elasticsearchOperations;
    }

    /**
     * Tìm kiếm và lọc sự kiện đa tiêu chí qua Elasticsearch Native Query
     */
    public List<EventElasticDocument> searchEvents(String keyword, String location, String category,
                                                  Double minPrice, Double maxPrice, int page, int size) {
        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withPageable(PageRequest.of(page, size));

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // 1. Full-text search (fuzzy matching trên title & description)
        if (keyword != null && !keyword.isBlank()) {
            boolQuery.must(m -> m.multiMatch(mm -> mm
                    .fields("title^3", "description")
                    .query(keyword)
                    .fuzziness("AUTO")
            ));
        }

        // 2. Lọc theo địa điểm
        if (location != null && !location.isBlank()) {
            boolQuery.filter(f -> f.term(t -> t.field("location").value(location)));
        }

        // 3. Lọc theo danh mục
        if (category != null && !category.isBlank()) {
            boolQuery.filter(f -> f.term(t -> t.field("category").value(category)));
        }

        // 4. Lọc theo khoảng giá
        if (minPrice != null || maxPrice != null) {
            boolQuery.filter(f -> f.range(r -> r.number(n -> {
                n.field("minPrice");
                if (minPrice != null) n.gte(minPrice);
                if (maxPrice != null) n.lte(maxPrice);
                return n;
            })));
        }

        queryBuilder.withQuery(boolQuery.build()._toQuery());
        SearchHits<EventElasticDocument> hits = search(queryBuilder.build(), EventElasticDocument.class);

        return hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();
    }
}
