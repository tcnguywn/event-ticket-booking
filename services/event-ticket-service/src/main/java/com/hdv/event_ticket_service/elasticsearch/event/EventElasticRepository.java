package com.hdv.event_ticket_service.elasticsearch.event;

import com.hdv.event_ticket_service.elasticsearch.BaseElasticRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventElasticRepository extends BaseElasticRepository<EventElasticDocument, String> {

    List<EventElasticDocument> findByLocation(String location);

    List<EventElasticDocument> findByCategory(String category);
}
