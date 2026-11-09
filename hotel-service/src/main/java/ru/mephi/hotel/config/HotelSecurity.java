package ru.mephi.hotel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import ru.mephi.shared.*;

@Configuration
@EnableMethodSecurity
@Import(RestErrors.class)
public class HotelSecurity {
    @Bean public ServletTraceFilter traceFilter() {
        return new ServletTraceFilter();
    }
    @Bean
    @Order(1)
    SecurityFilterChain internal(HttpSecurity http, ObjectMapper mapper,
    @Value("${app.internal-public-key}") Resource key) throws Exception {
        http.securityMatcher("/api/rooms/*/confirm-availability", "/api/rooms/*/release");
        ServletSecurity.resourceServer(http, JwtSupport.decoder(key, "booking-internal", "hotel-internal"), mapper);
        http.authorizeHttpRequests(a -> a.anyRequest().hasRole("INTERNAL"));
        return http.build();
    }
    @Bean
    @Order(2)
    SecurityFilterChain publicApi(HttpSecurity http, ObjectMapper mapper,
    @Value("${app.user-public-key}") Resource key) throws Exception {
        ServletSecurity.resourceServer(http, JwtSupport.decoder(key, "hotel-booking", "hotel-api"), mapper);
        http.authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
        .anyRequest().hasAnyRole("USER", "ADMIN"));
        return http.build();
    }
    @Bean OpenAPI openAPI() {
        return new OpenAPI().components(new Components().addSecuritySchemes("bearerAuth",
        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
