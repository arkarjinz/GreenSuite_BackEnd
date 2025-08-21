package com.app.greensuitetest.config;

import com.app.greensuitetest.service.PerformanceMonitoringService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceInterceptor implements HandlerInterceptor {

    private final PerformanceMonitoringService performanceMonitoringService;
    private static final String START_TIME_ATTRIBUTE = "startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Record start time for response time calculation
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        
        // Record endpoint request
        String endpoint = getEndpointName(request);
        performanceMonitoringService.recordEndpointRequest(endpoint);
        
        log.debug("Performance tracking started for endpoint: {}", endpoint);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Calculate and record response time
        Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
        if (startTime != null) {
            long responseTime = System.currentTimeMillis() - startTime;
            String endpoint = getEndpointName(request);
            
            performanceMonitoringService.recordResponseTime(endpoint, responseTime);
            
            log.debug("Performance tracking completed for endpoint: {} - {}ms", endpoint, responseTime);
        }
    }

    private String getEndpointName(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        
        // Simplify path for better grouping (remove IDs and query params)
        String simplifiedPath = path
                .replaceAll("/\\d+", "/{id}")                    // Replace numeric IDs
                .replaceAll("/[a-f0-9-]{36}", "/{uuid}")        // Replace UUIDs
                .replaceAll("/[a-zA-Z0-9_-]{20,}", "/{token}")  // Replace long tokens
                .split("\\?")[0];                               // Remove query parameters
        
        return method + " " + simplifiedPath;
    }
} 