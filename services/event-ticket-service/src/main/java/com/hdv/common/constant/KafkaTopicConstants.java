package com.hdv.common.constant;

public final class KafkaTopicConstants {
    private KafkaTopicConstants() {}

    // TOPICS
    public static final String TICKET_RESERVED = "ticket.reserved";
    public static final String ORDER_CREATED = "order.created";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String ORDER_CONFIRMED = "order.confirmed";
    public static final String TICKET_RELEASE = "ticket.release";

    // CONSUMER GROUPS
    public static final String GROUP_EVENT_TICKET = "event-ticket-group";
    public static final String GROUP_ORDER = "order-service-group";
    public static final String GROUP_PAYMENT = "payment-service-group";
    public static final String GROUP_NOTIFICATION = "notification-service-group";
}
