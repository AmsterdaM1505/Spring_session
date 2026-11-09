package ru.mephi.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
import ru.mephi.shared.ApiError;

@Component
@Order(-2)
public class GatewayErrors implements ErrorWebExceptionHandler {
    private final ObjectMapper mapper;
    public GatewayErrors(ObjectMapper mapper) {
        this.mapper = mapper;
    }
    @Override public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted()) return Mono.error(error);
        int status = error instanceof ResponseStatusException response ? response.getStatusCode().value()
        : error instanceof java.util.concurrent.TimeoutException ? 504 : 500;
        String traceId = exchange.getAttribute("traceId");
        LoggerFactory.getLogger(getClass()).atWarn().addKeyValue("event", "gateway_error")
        .addKeyValue("traceId", traceId).addKeyValue("status", status).setCause(error).log("Gateway request failed");
        exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = mapper.writeValueAsBytes(ApiError.of(status, "GATEWAY_ERROR", "Шлюз не смог обработать запрос", traceId));
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch
        (Exception encodingError) {
            return exchange.getResponse().setComplete();
        }
    }
}
