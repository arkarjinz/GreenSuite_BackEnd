package com.app.greensuitetest.config;

import com.app.greensuitetest.service.PerformanceMonitoringService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@Slf4j
public class CacheMonitoringAspect {

    @Autowired
    private ApplicationContext applicationContext;

    @Around("@annotation(org.springframework.cache.annotation.Cacheable)")
    public Object monitorCacheable(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            
            // Get PerformanceMonitoringService lazily to avoid circular dependency
            try {
                PerformanceMonitoringService performanceService = 
                    applicationContext.getBean(PerformanceMonitoringService.class);
                
                // If we got here with a result, it was likely a cache miss (method executed)
                performanceService.recordCacheMiss();
                
                Method method = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getMethod();
                long processingTime = System.currentTimeMillis() - startTime;
                
                String operation = joinPoint.getTarget().getClass().getSimpleName() + "." + method.getName();
                performanceService.recordResponseTime(operation + ".cacheable", processingTime);
                
                log.debug("Cache operation for {}.{} completed in {}ms", 
                    joinPoint.getTarget().getClass().getSimpleName(), 
                    method.getName(), processingTime);
                    
            } catch (Exception e) {
                // Silently handle if performance service is not available
                log.debug("Performance monitoring not available during cache operation");
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Error in cache monitoring aspect", e);
            throw e;
        }
    }
} 