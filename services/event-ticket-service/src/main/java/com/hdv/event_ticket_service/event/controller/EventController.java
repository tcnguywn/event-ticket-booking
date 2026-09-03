package com.hdv.event_ticket_service.event.controller;

import com.hdv.event_ticket_service.event.domain.dtos.CreateEventRequest;
import com.hdv.event_ticket_service.event.domain.dtos.EventResponse;
import com.hdv.event_ticket_service.event.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse createEvent(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateEventRequest request) {
        return eventService.createEvent(request, UUID.fromString(userId));
    }

    @GetMapping
    public List<EventResponse> getAllEvents() {
        return eventService.getAllEvents();
    }

    @GetMapping("/{id}")
    public EventResponse getEvent(@PathVariable("id") UUID id) {
        return eventService.getEvent(id);
    }
}
