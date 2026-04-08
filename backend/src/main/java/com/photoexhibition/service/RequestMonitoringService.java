package com.photoexhibition.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RequestMonitoringService {

    private static final long ONE_MINUTE_MILLIS = 60_000L;
    private static final long DEFAULT_SLOW_REQUEST_THRESHOLD_MS = 1_000L;
    private static final int MAX_SLOW_REQUEST_HISTORY = 30;

    private final AtomicInteger activeRequestCount = new AtomicInteger(0);
    private final ConcurrentHashMap<String, AtomicInteger> activeActorCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> activeEndpointCounts = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<Long> recentRequestTimestamps = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<SlowRequestSnapshot> slowRequests = new ConcurrentLinkedDeque<>();

    public RequestTracker startRequest(String method,
                                       String path,
                                       String queryString,
                                       String actorKey,
                                       String actorLabel,
                                       String ipAddress) {
        long startNanos = System.nanoTime();
        String normalizedPath = normalizePath(path);
        String endpointKey = buildEndpointKey(method, normalizedPath);
        activeRequestCount.incrementAndGet();
        recentRequestTimestamps.addLast(System.currentTimeMillis());
        activeEndpointCounts.computeIfAbsent(endpointKey, ignored -> new AtomicInteger(0)).incrementAndGet();
        if (actorKey != null && !actorKey.isBlank()) {
            activeActorCounts.computeIfAbsent(actorKey, ignored -> new AtomicInteger(0)).incrementAndGet();
        }
        trimRecentRequests();
        return new RequestTracker(startNanos, method, normalizedPath, queryString, endpointKey, actorKey, actorLabel, ipAddress);
    }

    public void finishRequest(RequestTracker tracker, int statusCode, Throwable error) {
        if (tracker == null) {
            return;
        }
        long durationMs = Math.max(0L, Math.round((System.nanoTime() - tracker.startNanos) / 1_000_000.0));
        activeRequestCount.updateAndGet(value -> Math.max(0, value - 1));
        decrementCounter(activeEndpointCounts, tracker.endpointKey);
        decrementCounter(activeActorCounts, tracker.actorKey);
        if (durationMs >= DEFAULT_SLOW_REQUEST_THRESHOLD_MS) {
            slowRequests.addFirst(new SlowRequestSnapshot(
                tracker.method,
                tracker.path,
                tracker.queryString,
                tracker.actorLabel,
                tracker.ipAddress,
                durationMs,
                statusCode,
                error == null ? null : sanitizeMessage(error.getMessage()),
                LocalDateTime.now()
            ));
            trimSlowRequests();
        }
    }

    public Map<String, Object> getOverview() {
        trimRecentRequests();
        trimSlowRequests();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("threadType", "HTTP_REQUEST");
        result.put("label", "实时请求监控");
        result.put("running", activeRequestCount.get() > 0);
        result.put("activeRequestCount", activeRequestCount.get());
        result.put("activeUserCount", activeActorCounts.size());
        result.put("recentMinuteRequestCount", recentRequestTimestamps.size());
        result.put("slowRequestThresholdMs", DEFAULT_SLOW_REQUEST_THRESHOLD_MS);
        result.put("topActiveEndpoints", activeEndpointCounts.entrySet().stream()
            .filter(entry -> entry.getValue().get() > 0)
            .sorted((left, right) -> Integer.compare(right.getValue().get(), left.getValue().get()))
            .limit(5)
            .map(entry -> Map.of(
                "endpoint", entry.getKey(),
                "activeCount", entry.getValue().get()
            ))
            .collect(java.util.stream.Collectors.toList()));
        result.put("recentSlowRequests", slowRequests.stream()
            .limit(10)
            .map(SlowRequestSnapshot::toMap)
            .collect(java.util.stream.Collectors.toList()));
        result.put("summary", activeRequestCount.get() > 0
            ? "当前有请求正在后端处理中"
            : "当前没有正在执行的 HTTP 请求");
        return result;
    }

    private void decrementCounter(ConcurrentHashMap<String, AtomicInteger> counters, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        counters.computeIfPresent(key, (ignored, counter) -> counter.decrementAndGet() <= 0 ? null : counter);
    }

    private void trimRecentRequests() {
        long cutoff = System.currentTimeMillis() - ONE_MINUTE_MILLIS;
        while (true) {
            Long head = recentRequestTimestamps.peekFirst();
            if (head == null || head >= cutoff) {
                return;
            }
            recentRequestTimestamps.pollFirst();
        }
    }

    private void trimSlowRequests() {
        while (slowRequests.size() > MAX_SLOW_REQUEST_HISTORY) {
            slowRequests.pollLast();
        }
    }

    private String buildEndpointKey(String method, String path) {
        return (method == null ? "GET" : method.toUpperCase()) + " " + normalizePath(path);
    }

    private String normalizePath(String path) {
        return path == null || path.isBlank() ? "/" : path;
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }

    public static final class RequestTracker {
        private final long startNanos;
        private final String method;
        private final String path;
        private final String queryString;
        private final String endpointKey;
        private final String actorKey;
        private final String actorLabel;
        private final String ipAddress;

        private RequestTracker(long startNanos,
                               String method,
                               String path,
                               String queryString,
                               String endpointKey,
                               String actorKey,
                               String actorLabel,
                               String ipAddress) {
            this.startNanos = startNanos;
            this.method = method;
            this.path = path;
            this.queryString = queryString;
            this.endpointKey = endpointKey;
            this.actorKey = actorKey;
            this.actorLabel = actorLabel;
            this.ipAddress = ipAddress;
        }
    }

    private static final class SlowRequestSnapshot {
        private final String method;
        private final String path;
        private final String queryString;
        private final String actorLabel;
        private final String ipAddress;
        private final long durationMs;
        private final int statusCode;
        private final String error;
        private final LocalDateTime finishedAt;

        private SlowRequestSnapshot(String method,
                                    String path,
                                    String queryString,
                                    String actorLabel,
                                    String ipAddress,
                                    long durationMs,
                                    int statusCode,
                                    String error,
                                    LocalDateTime finishedAt) {
            this.method = method;
            this.path = path;
            this.queryString = queryString;
            this.actorLabel = actorLabel;
            this.ipAddress = ipAddress;
            this.durationMs = durationMs;
            this.statusCode = statusCode;
            this.error = error;
            this.finishedAt = finishedAt;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("method", method);
            item.put("path", path);
            item.put("queryString", queryString);
            item.put("actorLabel", actorLabel);
            item.put("ipAddress", ipAddress);
            item.put("durationMs", durationMs);
            item.put("statusCode", statusCode);
            item.put("error", error);
            item.put("finishedAt", finishedAt);
            return item;
        }
    }
}
