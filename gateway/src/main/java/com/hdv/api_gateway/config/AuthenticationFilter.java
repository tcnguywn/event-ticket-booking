package com.hdv.api_gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Nếu request có JWT hợp lệ, ưu tiên inject header an toàn từ Token
        return exchange.getPrincipal()
            .filter(principal -> principal instanceof JwtAuthenticationToken)
            .cast(JwtAuthenticationToken.class)
            .map(jwtAuth -> {
                Jwt jwt = jwtAuth.getToken();
                String userId = jwt.getSubject();
                String rawEmail = jwt.getClaimAsString("email");
                String email = (rawEmail != null && !rawEmail.isBlank()) ? rawEmail : "unknown@example.com";
                String role = extractRole(jwt.getClaimAsMap("realm_access"));

                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .headers(httpHeaders -> {
                        httpHeaders.set("X-User-Id", userId);
                        httpHeaders.set("X-User-Email", email);
                        httpHeaders.set("X-User-Role", role);
                    }).build();
                return exchange.mutate().request(mutatedRequest).build();
            })
            .defaultIfEmpty(exchange)
            .flatMap(chain::filter);
    }

    private String extractRole(Map<String, Object> realmAccess) {
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles != null && !roles.isEmpty()) {
                if (roles.contains("ORGANIZER") || roles.contains("organizer")) return "ORGANIZER";
                if (roles.contains("ADMIN") || roles.contains("admin")) return "ADMIN";
                if (roles.contains("USER") || roles.contains("user")) return "USER";
            }
        }
        return "USER";
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
