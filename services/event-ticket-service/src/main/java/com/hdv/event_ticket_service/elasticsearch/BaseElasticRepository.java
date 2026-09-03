package com.hdv.event_ticket_service.elasticsearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface BaseElasticRepository<T extends BaseElasticDocument, ID> extends ElasticsearchRepository<T, ID> {
}
