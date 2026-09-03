package com.hdv.api_gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Component
public class VirtualWaitingRoomFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(VirtualWaitingRoomFilter.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String BOOKING_PATH = "/api/tickets/book";
    private static final String QUEUE_TOKEN_HEADER = "X-Queue-Pass-Token";
    private static final int MAX_CONCURRENT_USERS_PER_SECOND = 100;

    public VirtualWaitingRoomFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Chỉ áp dụng Virtual Waiting Room cho luồng đặt vé
        if (!BOOKING_PATH.equals(path)) {
            return chain.filter(exchange);
        }

        String passToken = exchange.getRequest().getHeaders().getFirst(QUEUE_TOKEN_HEADER);

        return exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(jwtAuth -> {
                    String userId = jwtAuth.getToken().getSubject();
                    String queueTokenKey = "queue_token:" + userId;

                    // 1. Nếu client đã có Queue-Pass-Token hợp lệ -> Cho qua thẳng
                    if (passToken != null && !passToken.isBlank()) {
                        return redisTemplate.hasKey(queueTokenKey)
                                .flatMap(hasKey -> {
                                    if (Boolean.TRUE.equals(hasKey)) {
                                        return chain.filter(exchange);
                                    }
                                    return processWaitingRoom(exchange, chain, userId);
                                });
                    }

                    return processWaitingRoom(exchange, chain, userId);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> processWaitingRoom(ServerWebExchange exchange, GatewayFilterChain chain, String userId) {
        String waitingRoomKey = "waiting_room:active_event";
        long now = System.currentTimeMillis();

        return redisTemplate.opsForZSet().score(waitingRoomKey, userId)
                .switchIfEmpty(
                        // Nếu user chưa vào hàng đợi, add vào ZSET
                        redisTemplate.opsForZSet().add(waitingRoomKey, userId, now)
                                .then(Mono.just((double) now))
                )
                .flatMap(score -> redisTemplate.opsForZSet().rank(waitingRoomKey, userId))
                .flatMap(rank -> {
                    // Nếu rank nằm trong số lượng được phép vào mua (VD: top 100)
                    if (rank != null && rank < MAX_CONCURRENT_USERS_PER_SECOND) {
                        String generatedToken = UUID.randomUUID().toString();
                        String queueTokenKey = "queue_token:" + userId;

                        // Cấp Queue-Pass-Token có hiệu lực 5 phút và xóa khỏi hàng đợi
                        return redisTemplate.opsForValue().set(queueTokenKey, generatedToken, Duration.ofMinutes(5))
                                .then(redisTemplate.opsForZSet().remove(waitingRoomKey, userId))
                                .then(Mono.defer(() -> {
                                    exchange.getResponse().getHeaders().add(QUEUE_TOKEN_HEADER, generatedToken);
                                    return exchange.getSession().flatMap(s -> {
                                        s.getAttributes().put(QUEUE_TOKEN_HEADER, generatedToken);
                                        return Mono.empty();
                                    });
                                }))
                                .then(Mono.defer(() -> {
                                    // Cho phép request hiện tại đi tiếp vào backend
                                    return chain.filter(exchange);
                                }));
                    }

                    // Nếu vị trí hàng đợi còn cao, trả về 429 Too Many Requests kèm vị trí xếp hàng
                    long position = (rank != null) ? rank + 1 : 1;
                    long waitSeconds = (position / MAX_CONCURRENT_USERS_PER_SECOND) * 2;

                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

                    String json = String.format(
                            "{\"status\":\"WAITING_ROOM\",\"message\":\"Bạn đang trong phòng chờ. Vui lòng không tải lại trang.\",\"queuePosition\":%d,\"estimatedWaitSeconds\":%d}",
                            position, Math.max(waitSeconds, 1)
                    );

                    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
                    return exchange.getResponse().writeWith(Mono.just(buffer));
                });
    }

    @Override
    public int getOrder() {
        return 0; // Chạy sau AuthenticationFilter (-1)
    }
}
