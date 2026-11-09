package ru.mephi.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;

public final class ServletSecurity {
    private ServletSecurity() {
    }
    public static void resourceServer(HttpSecurity http, JwtDecoder decoder, ObjectMapper mapper) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        var unauthorized = (org.springframework.security.web.AuthenticationEntryPoint) (req, res, ex) -> {
            res.setStatus(401);
            res.setContentType("application/json;charset=UTF-8");
            res.setHeader("WWW-Authenticate", "Bearer");
            mapper.writeValue(res.getOutputStream(), ApiError.of(401, "UNAUTHORIZED", "Требуется действительный JWT", MDC.get("traceId")));
        };
        var forbidden = (org.springframework.security.web.access.AccessDeniedHandler) (req, res, ex) -> {
            res.setStatus(403);
            res.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(res.getOutputStream(), ApiError.of(403, "FORBIDDEN", "Недостаточно прав", MDC.get("traceId")));
        };
        http.exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden));
        http.oauth2ResourceServer(o -> o.jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(JwtSupport.authenticationConverter()))
        .authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden));
    }
}
