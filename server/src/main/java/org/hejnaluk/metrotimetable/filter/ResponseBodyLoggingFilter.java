package org.hejnaluk.metrotimetable.filter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResponseBodyLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_LOG_LENGTH = 2000;
    private static final String UNKNOWN_VERSION = "unknown";

    private final MeterRegistry meterRegistry;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator/health")
                || uri.startsWith("/actuator/prometheus")
                || uri.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrappedResponse);
        } finally {
            logResponse(request, wrappedResponse);
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void logResponse(HttpServletRequest request, ContentCachingResponseWrapper response) {
        String clientVersion = request.getHeader("X-Client-Version");
        String effectiveVersion = clientVersion != null ? clientVersion : UNKNOWN_VERSION;

        Counter.builder("client.requests")
                .description("Number of requests per client version")
                .tag("version", effectiveVersion)
                .register(meterRegistry)
                .increment();

        byte[] bodyBytes = response.getContentAsByteArray();
        if (bodyBytes.length == 0) {
            log.info("Response: {} {} - status={}, clientVersion={}, body=<empty>",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), clientVersion);
            return;
        }
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        if (body.length() > MAX_BODY_LOG_LENGTH) {
            body = body.substring(0, MAX_BODY_LOG_LENGTH) + "...[truncated]";
        }
        log.info("Response: {} {} - status={}, clientVersion={}, body={}",
                request.getMethod(), request.getRequestURI(), response.getStatus(), clientVersion, body);
    }
}
