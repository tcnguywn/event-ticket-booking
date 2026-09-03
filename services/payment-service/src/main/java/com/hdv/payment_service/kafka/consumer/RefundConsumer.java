package com.hdv.payment_service.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdv.common.dto.OrderRefundRequestedEvent;
import com.hdv.payment_service.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefundConsumer {
    private final RefundService refundService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "refund.requested",
        groupId = "payment-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
        @Payload String rawMessage,
        Acknowledgment ack
    ) {
        log.info("Received refund request: {}", rawMessage);
        try {
            OrderRefundRequestedEvent event = objectMapper.readValue(rawMessage, OrderRefundRequestedEvent.class);
            refundService.processRefundRequest(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing refund: {}", rawMessage, e);
        }
    }
}
