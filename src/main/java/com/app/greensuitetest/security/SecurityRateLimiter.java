package com.app.greensuitetest.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SecurityRateLimiter implements HandlerInterceptor {
    private final Map<String, RateLimitData> rateLimitMap = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long timeoutMillis;

    public SecurityRateLimiter(int maxRequests, long timeoutMillis) {
        this.maxRequests = maxRequests;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return true;
        }

        String clientId = getClientIdentifier(request);
        RateLimitData rateLimitData = rateLimitMap.computeIfAbsent(clientId,
                k -> new RateLimitData(maxRequests, timeoutMillis));

        if (!rateLimitData.tryAcquire()) {
            log.warn("Rate limit exceeded for client: {}", clientId);
            response.sendError(429, "Too Many Requests - Try again later");
            response.setHeader("Retry-After", String.valueOf(
                    TimeUnit.MILLISECONDS.toSeconds(timeoutMillis)));
            return false;
        }

        response.setHeader("X-Rate-Limit-Remaining",
                String.valueOf(rateLimitData.availablePermits()));
        return true;
    }

    private String getClientIdentifier(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private static class RateLimitData {
        private final Semaphore semaphore;
        private final int maxRequests;
        private final long timeoutMillis;
        private long lastAccessTime;

        public RateLimitData(int maxRequests, long timeoutMillis) {
            this.semaphore = new Semaphore(maxRequests);
            this.maxRequests = maxRequests;
            this.timeoutMillis = timeoutMillis;
            this.lastAccessTime = System.currentTimeMillis();
        }

        public boolean tryAcquire() {
            if (System.currentTimeMillis() - lastAccessTime > timeoutMillis) {
                semaphore.drainPermits();
                semaphore.release(maxRequests);
            }

            lastAccessTime = System.currentTimeMillis();
            return semaphore.tryAcquire();
        }

        public int availablePermits() {
            return semaphore.availablePermits();
        }
    }
}