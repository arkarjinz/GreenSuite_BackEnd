package com.app.greensuitetest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceMonitoringService {

    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;

    // Performance metrics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong redisOperations = new AtomicLong(0);
    private final Map<String, AtomicLong> endpointRequests = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> endpointResponseTimes = new ConcurrentHashMap<>();

    /**
     * Record a cache hit
     */
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
    }

    /**
     * Record a cache miss
     */
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
    }

    /**
     * Record a Redis operation
     */
    public void recordRedisOperation() {
        redisOperations.incrementAndGet();
    }

    /**
     * Record an endpoint request
     */
    public void recordEndpointRequest(String endpoint) {
        endpointRequests.computeIfAbsent(endpoint, k -> new AtomicLong(0)).incrementAndGet();
        totalRequests.incrementAndGet();
    }

    /**
     * Record endpoint response time
     */
    public void recordResponseTime(String endpoint, long responseTimeMs) {
        endpointResponseTimes.computeIfAbsent(endpoint, k -> new AtomicLong(0)).addAndGet(responseTimeMs);
    }

    /**
     * Get performance metrics
     */
    public Map<String, Object> getPerformanceMetrics() {
        long total = totalRequests.get();
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        double hitRate = total > 0 ? (double) hits / (hits + misses) * 100 : 0;

        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("total_requests", total);
        metrics.put("cache_hits", hits);
        metrics.put("cache_misses", misses);
        metrics.put("cache_hit_rate_percent", Math.round(hitRate * 100.0) / 100.0);
        metrics.put("redis_operations", redisOperations.get());
        metrics.put("endpoint_requests", new ConcurrentHashMap<>(endpointRequests));
        metrics.put("endpoint_response_times", new ConcurrentHashMap<>(endpointResponseTimes));

        return metrics;
    }

    /**
     * Check Redis connectivity and performance
     */
    public Map<String, Object> checkRedisHealth() {
        Map<String, Object> health = new ConcurrentHashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            redisTemplate.opsForValue().get("health_check");
            long responseTime = System.currentTimeMillis() - startTime;
            
            health.put("status", "healthy");
            health.put("response_time_ms", responseTime);
            health.put("connected", true);
            
            // Test Redis performance
            long opsStart = System.currentTimeMillis();
            for (int i = 0; i < 10; i++) {
                redisTemplate.opsForValue().set("perf_test_" + i, "test_value");
                redisTemplate.opsForValue().get("perf_test_" + i);
                redisTemplate.delete("perf_test_" + i);
            }
            long opsTime = System.currentTimeMillis() - opsStart;
            
            health.put("performance_test_10_ops_ms", opsTime);
            health.put("avg_op_time_ms", opsTime / 30.0); // 30 operations total
            
        } catch (Exception e) {
            health.put("status", "unhealthy");
            health.put("error", e.getMessage());
            health.put("connected", false);
            log.error("Redis health check failed", e);
        }
        
        return health;
    }

    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        try {
            // Get cache names and their statistics
            cacheManager.getCacheNames().forEach(cacheName -> {
                var cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    Map<String, Object> cacheStats = new ConcurrentHashMap<>();
                    cacheStats.put("name", cacheName);
                    cacheStats.put("native_cache", cache.getNativeCache().getClass().getSimpleName());
                    stats.put(cacheName, cacheStats);
                }
            });
        } catch (Exception e) {
            log.error("Error getting cache statistics", e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }

    /**
     * Scheduled performance monitoring (every 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void logPerformanceMetrics() {
        Map<String, Object> metrics = getPerformanceMetrics();
        Map<String, Object> redisHealth = checkRedisHealth();
        
        log.info("=== PERFORMANCE METRICS ===");
        log.info("Total Requests: {}", metrics.get("total_requests"));
        log.info("Cache Hit Rate: {}%", metrics.get("cache_hit_rate_percent"));
        log.info("Redis Operations: {}", metrics.get("redis_operations"));
        log.info("Redis Status: {}", redisHealth.get("status"));
        log.info("Redis Response Time: {}ms", redisHealth.get("response_time_ms"));
        
        // Log endpoint performance
        @SuppressWarnings("unchecked")
        Map<String, AtomicLong> requests = (Map<String, AtomicLong>) metrics.get("endpoint_requests");
        @SuppressWarnings("unchecked")
        Map<String, AtomicLong> responseTimes = (Map<String, AtomicLong>) metrics.get("endpoint_response_times");
        
        if (requests != null && !requests.isEmpty()) {
            log.info("=== ENDPOINT PERFORMANCE ===");
            requests.forEach((endpoint, count) -> {
                AtomicLong totalTime = responseTimes.get(endpoint);
                if (totalTime != null && count.get() > 0) {
                    double avgTime = (double) totalTime.get() / count.get();
                    log.info("{}: {} requests, avg {}ms", endpoint, count.get(), Math.round(avgTime));
                }
            });
        }
    }

    /**
     * Reset performance metrics (useful for testing)
     */
    public void resetMetrics() {
        totalRequests.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        redisOperations.set(0);
        endpointRequests.clear();
        endpointResponseTimes.clear();
        log.info("Performance metrics reset");
    }
} 