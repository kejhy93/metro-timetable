package org.hejnaluk.metrotimetable.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class ResponseBodyLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_LOG_LENGTH = 2000;

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
        byte[] bodyBytes = response.getContentAsByteArray();
        if (bodyBytes.length == 0) {
            log.info("Response: {} {} - status={}, body=<empty>",
                    request.getMethod(), request.getRequestURI(), response.getStatus());
            return;
        }
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        if (body.length() > MAX_BODY_LOG_LENGTH) {
            body = body.substring(0, MAX_BODY_LOG_LENGTH) + "...[truncated]";
        }
        log.info("Response: {} {} - status={}, body={}",
                request.getMethod(), request.getRequestURI(), response.getStatus(), body);
    }
}
