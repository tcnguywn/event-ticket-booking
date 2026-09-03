package com.hdv.event_ticket_service.ticket.service;

import com.hdv.event_ticket_service.config.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

import static com.hdv.event_ticket_service.config.Constants.PREFIX_REDIS_STOCK;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> inventoryLuaScript;


    /**
     * Trừ kho nguyên tử trên Redis qua Lua script.
     * @param ticketTypeId ID loại vé
     * @param quantity Số lượng mua
     * @param initialStockFromDb Số lượng tồn kho hiện tại trong DB (làm fallback khi cold cache)
     * @return Số vé còn lại (>= 0), hoặc -1 nếu hết vé
     */

    public long decrementStock(String ticketTypeId, int quantity, int initialStockFromDb) {
        String key = PREFIX_REDIS_STOCK + ticketTypeId;
        Long result = redisTemplate.execute(inventoryLuaScript, Collections.singletonList(key), String.valueOf(quantity), String.valueOf(initialStockFromDb));
        if (result == null) {
            log.error("Lua script execution failed for ticketTypeId: {}", ticketTypeId);
            return -1;
        }
        log.info("Decremented stock for {} by {}, remaining: {}", ticketTypeId, quantity, result);
        return result;
    }


    public void incrementStock(String ticketTypeId, int quantity) {
        String key = PREFIX_REDIS_STOCK + ticketTypeId;
        redisTemplate.opsForValue().increment(key, quantity);
        log.info("Incremented stock for {} by {}", ticketTypeId, quantity);
    }
}
