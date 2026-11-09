package ru.mephi.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class ServletTraceFilter extends OncePerRequestFilter implements org.springframework.core.Ordered {
    @Override public int getOrder() {
        return org.springframework.core.Ordered.HIGHEST_PRECEDENCE;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
    throws ServletException, IOException {
        String traceId = TraceIds.normalize(request.getHeader(TraceIds.HEADER));
        MDC.put("traceId", traceId);
        response.setHeader(TraceIds.HEADER, traceId);
        try {
            chain.doFilter(request, response);
        }
        finally {
            LoggerFactory.getLogger(getClass()).atInfo().addKeyValue("event", "http_request")
            .addKeyValue("method", request.getMethod()).addKeyValue("path", request.getRequestURI())
            .addKeyValue("status", response.getStatus()).log("HTTP request completed");
            MDC.remove("traceId");
        }
    }
}
