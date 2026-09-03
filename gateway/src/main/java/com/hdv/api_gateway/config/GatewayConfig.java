package com.hdv.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class GatewayConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchange -> exchange
                        // Cho phép tất cả OPTIONS preflight request đi qua không cần xác thực
                        .pathMatchers(org.springframework.http.HttpMethod.OPTIONS).permitAll()

                        // 1. Public API xem sự kiện (không cần đăng nhập)
                        .pathMatchers("/api/events/public/**").permitAll()
                        .pathMatchers(org.springframework.http.HttpMethod.GET, "/api/events", "/api/events/**").permitAll()

                        // 2. Public API VNPay (create-url, IPN callback, return)
                        .pathMatchers("/api/v1/payments/vnpay/**").permitAll()

                        // 3. Public Healthcheck & Monitoring Actuator
                        .pathMatchers("/actuator/prometheus", "/actuator/health/**", "/actuator/info").permitAll()

                        // 4. Các API còn lại (Đặt vé, Đơn hàng, Support Tickets)
                        .anyExchange().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }

    @Bean
    public org.springframework.web.cors.reactive.CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(List.of("*"));
        corsConfig.setMaxAge(3600L);
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        corsConfig.setAllowedHeaders(List.of("*"));
        corsConfig.setExposedHeaders(List.of("*"));
        corsConfig.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }
}
