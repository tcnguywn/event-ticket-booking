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
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .authorizeExchange(exchange -> exchange
                        // 1. Public API xem sự kiện (không cần đăng nhập)
                        .pathMatchers("/api/events/public/**").permitAll()
                        .pathMatchers(org.springframework.http.HttpMethod.GET, "/api/events").permitAll()
                        .pathMatchers(org.springframework.http.HttpMethod.GET, "/api/events/*").permitAll()

                        // 2. Public API VNPay (create-url, IPN callback, return)
                        .pathMatchers("/api/v1/payments/vnpay/**").permitAll()

                        // 3. Public Healthcheck & Monitoring Actuator
                        .pathMatchers("/actuator/prometheus", "/actuator/health/**", "/actuator/info").permitAll()

                        // 4. Các API còn lại (Đặt vé, Đơn hàng, Support Tickets) bắt buộc phải có JWT hợp lệ
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(List.of("*"));
        corsConfig.setMaxAge(3600L);
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        corsConfig.setAllowedHeaders(List.of("*"));
        corsConfig.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
