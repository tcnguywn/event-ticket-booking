package com.hdv.order_service.config;

import org.redisson.Redisson;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        // Cấu hình Single Node cho môi trường Dev
        config.useSingleServer().setAddress("redis://" + redisHost + ":" + redisPort);
        return Redisson.create(config);
    }

    @Bean
    public RBlockingQueue<String> orderExpiryQueue(RedissonClient client) {
        // Hàng đợi chứa các đơn hàng đã hết hạn
        return client.getBlockingQueue("order.expiry");
    }

    @Bean
    public RDelayedQueue<String> orderDelayedQueue(
            RedissonClient client,
            RBlockingQueue<String> orderExpiryQueue) {
        // Hàng đợi trễ đếm ngược thời gian, trỏ tới orderExpiryQueue
        return client.getDelayedQueue(orderExpiryQueue);
    }
}