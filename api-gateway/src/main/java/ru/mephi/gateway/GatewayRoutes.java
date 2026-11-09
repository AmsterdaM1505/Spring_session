package ru.mephi.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;

@Configuration
public class GatewayRoutes {
    @Bean RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
        .route("booking", r -> r.path("/api/bookings", "/api/bookings/**", "/api/users", "/api/users/**").uri("lb://booking-service"))
        .route("hotel", r -> r.path("/api/hotels", "/api/hotels/**", "/api/rooms", "/api/rooms/**").uri("lb://hotel-service"))
        .route("booking-openapi", r -> r.path("/docs/booking").filters(f -> f.setPath("/v3/api-docs")).uri("lb://booking-service"))
        .route("hotel-openapi", r -> r.path("/docs/hotel").filters(f -> f.setPath("/v3/api-docs")).uri("lb://hotel-service"))
        .build();
    }
}
