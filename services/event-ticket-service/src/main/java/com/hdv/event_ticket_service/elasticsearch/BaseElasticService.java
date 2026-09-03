package com.hdv.event_ticket_service.elasticsearch;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import java.util.Optional;

public abstract class BaseElasticService<T extends BaseElasticDocument, ID> {

    protected abstract BaseElasticRepository<T, ID> getRepository();

    protected abstract ElasticsearchOperations getOperations();

    public T save(T document) {
        return getRepository().save(document);
    }

    public Optional<T> findById(ID id) {
        return getRepository().findById(id);
    }

    public void deleteById(ID id) {
        getRepository().deleteById(id);
    }

    public SearchHits<T> search(Query query, Class<T> clazz) {
        return getOperations().search(query, clazz);
    }
}
