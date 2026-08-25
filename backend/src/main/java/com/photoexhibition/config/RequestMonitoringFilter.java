package com.photoexhibition.config;

import com.photoexhibition.service.RequestMonitoringService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Order(1)
public class RequestMonitoringFilter extends OncePerRequestFilter {

    private final RequestMonitoringService requestMonitoringService;
    private final JwtConfig jwtConfig;

    public RequestMonitoringFilter(RequestMonitoringService requestMonitoringService, JwtConfig jwtConfig) {
        this.requestMonitoringService = requestMonitoringService;
        this.jwtConfig = jwtConfig;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        RequestActor actor = resolveActor(request);
        RequestMonitoringService.RequestTracker tracker = requestMonitoringService.startRequest(
            request.getMethod(),
            request.getRequestURI(),
            request.getQueryString(),
            actor.actorKey,
            actor.actorLabel,
            request.getRemoteAddr()
        );

        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException throwable) {
            failure = throwable;
            throw throwable;
        } catch (Error throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            requestMonitoringService.finishRequest(tracker, response.getStatus(), failure);
        }
    }

    private RequestActor resolveActor(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            try {
                Long userId = jwtConfig.extractUserId(token);
                String username = jwtConfig.extractUsername(token);
                String slug = jwtConfig.extractSlug(token);
                String label = firstNonBlank(
                    userId != null ? "用户#" + userId : null,
                    username,
                    slug
                );
                String key = userId != null
                    ? "USER:" + userId
                    : "TOKEN:" + firstNonBlank(username, slug, request.getRemoteAddr());
                return new RequestActor(key, label);
            } catch (Exception ignored) {
            }
        }
        String ipAddress = request.getRemoteAddr();
        return new RequestActor("GUEST:" + ipAddress, "游客@" + ipAddress);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static final class RequestActor {
        private final String actorKey;
        private final String actorLabel;

        private RequestActor(String actorKey, String actorLabel) {
            this.actorKey = actorKey;
            this.actorLabel = actorLabel;
        }
    }
}
