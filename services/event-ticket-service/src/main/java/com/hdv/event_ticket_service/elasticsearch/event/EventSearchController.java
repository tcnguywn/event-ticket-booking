package com.hdv.event_ticket_service.elasticsearch.event;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events/search")
@RequiredArgsConstructor
public class EventSearchController {

    private final EventSearchService eventSearchService;

    @GetMapping
    public ResponseEntity<List<EventElasticDocument>> searchEvents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<EventElasticDocument> results = eventSearchService.searchEvents(
                keyword, location, category, minPrice, maxPrice, page, size
        );
        return ResponseEntity.ok(results);
    }
}
