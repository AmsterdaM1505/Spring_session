package ru.mephi.gateway;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

@SpringBootTest(properties = {
    "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"
})
class GatewaySecurityTest {
    @Autowired ApplicationContext context;
    @Autowired RouteLocator routes;
    WebTestClient client() {
        return WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient().build();
    }
    @Test void anonymousBookingRequestIs401() {
        client().get().uri("/api/bookings").exchange().expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }
    @Test void malformedTokenIs401() {
        client().get().uri("/api/hotels").header("Authorization", "Bearer malformed").exchange().expectStatus().isUnauthorized();
    }
    @Test void internalRoutesAreNeverExposed() {
        client().mutateWith(mockJwt().authorities(() -> "ROLE_ADMIN")).post().uri("/api/rooms/1/release").exchange().expectStatus().isForbidden();
        client().mutateWith(mockJwt().authorities(() -> "ROLE_USER")).post().uri("/api/rooms/1/confirm-availability").exchange().expectStatus().isForbidden();
    }
    @Test void traceIdIsPreservedEvenForUnauthorizedRequest() {
        String id = UUID.randomUUID().toString();
        client().get().uri("/api/bookings").header("X-Request-Id", id).exchange().expectStatus().isUnauthorized().expectHeader().valueEquals("X-Request-Id", id);
    }
    @Test void invalidTraceIdIsReplaced() {
        client().get().uri("/api/bookings").header("X-Request-Id", "invalid").exchange().expectHeader().valueMatches("X-Request-Id", "[0-9a-f-]{36}");
    }
    @Test void routesUseDiscoveryRatherThanFixedPorts() {
        var uris = routes.getRoutes().map(r -> r.getUri().toString()).collectList().block(Duration.ofSeconds(5));
        assertThat(uris).contains("lb://hotel-service", "lb://booking-service");
    }
}
