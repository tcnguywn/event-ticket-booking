package com.hdv.notification_service.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class ProcessedEventCache {

    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "notif:processed:";
    private static final long TTL_SECONDS = 86400; // 1 day

    /**
     * Checks if the event has already been processed based on the idempotency key.
     * @param idempotencyKey The unique key for the event
     * @return true if already processed, false otherwise.
     */
    public boolean isProcessed(String idempotencyKey) {
        String key = PREFIX + idempotencyKey;
        // setIfAbsent returns true if the key didn't exist and was set
        Boolean isSet = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL_SECONDS, TimeUnit.SECONDS);
        // If it was NOT set (isSet == false), it means it ALREADY existed. So it IS processed.
        return Boolean.FALSE.equals(isSet);
    }
}
