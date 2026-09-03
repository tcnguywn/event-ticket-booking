package com.hdv.payment_service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class Producer {
    private final KafkaTemplate<String, String> kafkaTemplate;

    public void send(String topic, String aggregateId, String payload) {
        kafkaTemplate.send(topic, aggregateId, payload)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send Kafka message to topic={}", topic, ex);
                    throw new RuntimeException(ex);
                }
                log.info("Message sent to topic={}, offset={}",
                    topic, result.getRecordMetadata().offset());
            });
    }
}
