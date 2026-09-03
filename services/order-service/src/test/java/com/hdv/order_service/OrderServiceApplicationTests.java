package com.hdv.order_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdv.common.dto.TicketReservedEvent;
import org.junit.jupiter.api.Test;

class OrderServiceApplicationTests {

	@Test
	void testDeserialize() throws Exception {
		String json = "{\"bookingGroupId\":\"c03cb8fb-cd34-4558-a659-59283b6d7f22\",\"userId\":\"33333333-3333-3333-3333-333333333333\",\"email\":\"john_doe@loadtest.com\",\"eventId\":\"11111111-1111-1111-1111-111111111111\",\"totalPrice\":2150000,\"idempotencyKey\":\"508fa56e-ba40-478b-9e5b-6cec0b86428d\",\"items\":[{\"ticketTypeId\":\"22222222-2222-2222-2222-222222222222\",\"ticketTypeName\":\"VIP Diamond\",\"quantity\":1,\"price\":1500000},{\"ticketTypeId\":\"33333333-2222-2222-2222-222222222222\",\"ticketTypeName\":\"Standard GA\",\"quantity\":1,\"price\":650000}],\"timestamp\":\"2026-09-03T16:30:53.149975700Z\"}";
		ObjectMapper mapper = new ObjectMapper();
		TicketReservedEvent event = mapper.readValue(json, TicketReservedEvent.class);
		System.out.println("EVENT: " + event);
	}
}
