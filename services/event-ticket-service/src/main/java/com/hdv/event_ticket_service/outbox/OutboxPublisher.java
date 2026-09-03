package com.hdv.event_ticket_service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public void publish(String topic, String key, String payload) {
        try {
            log.info("Publishing message to topic: {}, key: {}", topic, key);
            Object jsonPayload = objectMapper.readValue(payload, Object.class);
            kafkaTemplate.send(topic, key, jsonPayload);
        } catch (Exception e) {
            log.error("Failed to parse and publish payload to Kafka", e);
            throw new RuntimeException("Error publishing outbox msg", e);
        }
    }
}
