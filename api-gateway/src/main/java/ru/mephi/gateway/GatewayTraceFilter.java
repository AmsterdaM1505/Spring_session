package ru.mephi.gateway;

import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
import ru.mephi.shared.TraceIds;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayTraceFilter implements WebFilter {
    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String trace = TraceIds.normalize(exchange.getRequest().getHeaders().getFirst(TraceIds.HEADER));
        exchange.getAttributes().put("traceId", trace);
        exchange.getResponse().getHeaders().set(TraceIds.HEADER, trace);
        var forwarded = exchange.mutate().request(r -> r.headers(h -> {
            h.set(TraceIds.HEADER, trace);
            h.remove("X-Booking-Id");
        })).
        build();
        return chain.filter(forwarded).doFinally(signal -> LoggerFactory.getLogger(getClass()).atInfo()
        .addKeyValue("event", "gateway_request").addKeyValue("traceId", trace)
        .addKeyValue("method", exchange.getRequest().getMethod())
        .addKeyValue("path", exchange.getRequest().getPath().value())
        .addKeyValue("status", exchange.getResponse().getStatusCode()).log("Gateway request completed"));
    }
}
