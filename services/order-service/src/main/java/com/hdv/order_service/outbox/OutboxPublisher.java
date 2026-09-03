package com.hdv.order_service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(String topic, String payload) {
        log.info("Publishing outbox message to topic [{}]: {}", topic, payload);
        kafkaTemplate.send(topic, payload); // Bắn message lên Kafka [cite: 151, 438]
    }
}