package ru.mephi.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.mephi.shared.*;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurity {
    @Bean ReactiveJwtDecoder decoder(@Value("${app.user-public-key}") Resource key) throws Exception {
        var decoder = NimbusReactiveJwtDecoder.withPublicKey(JwtSupport.publicKey(key)).build();
        decoder.setJwtValidator(JwtSupport.validator("hotel-booking", "hotel-api"));
        return decoder;
    }
    @Bean SecurityWebFilterChain security(ServerHttpSecurity http, ReactiveJwtDecoder decoder, ObjectMapper mapper) {
        var unauthorized = (org.springframework.security.web.server.ServerAuthenticationEntryPoint) (exchange, ex) -> error(exchange, mapper, 401);
        var forbidden = (org.springframework.security.web.server.authorization.ServerAccessDeniedHandler) (exchange, ex) -> error(exchange, mapper, 403);
        return http.csrf(c -> c.disable()).httpBasic(c -> c.disable()).formLogin(c -> c.disable())
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .authorizeExchange(a -> a.pathMatchers("/api/rooms/*/release", "/api/rooms/*/confirm-availability").denyAll()
        .pathMatchers(HttpMethod.POST, "/api/users/register", "/api/users/auth").permitAll()
        .pathMatchers("/actuator/health", "/docs/**").permitAll()
        .pathMatchers("/api/**").hasAnyRole("USER", "ADMIN").anyExchange().denyAll())
        .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtDecoder(decoder)
        .jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(JwtSupport.authenticationConverter())))
        .authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden)).build();
    }
    static Mono<Void> error(ServerWebExchange exchange, ObjectMapper mapper, int status) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (status == 401) response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        try {
            byte[] bytes = mapper.writeValueAsBytes(ApiError.of(status, status == 401 ? "UNAUTHORIZED" : "FORBIDDEN",
            status == 401 ? "Требуется действительный JWT" : "Недостаточно прав", exchange.getAttribute("traceId")));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch
        (Exception ex) {
            return response.setComplete();
        }
    }
}
