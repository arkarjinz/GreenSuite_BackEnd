package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.ApiResponse;
import com.app.greensuitetest.service.PaymentSystemMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemHealthController {
    
    private final PaymentSystemMonitoringService monitoringService;
    
    /**
     * Get comprehensive system health check (Admin only)
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse> getSystemHealth() {
        try {
            Map<String, Object> healthCheck = monitoringService.performSystemHealthCheck();
            
            return ResponseEntity.ok(ApiResponse.success("System health check completed", healthCheck));
        } catch (Exception e) {
            log.error("Error performing system health check", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed to perform system health check: " + e.getMessage()));
        }
    }
    
    /**
     * Get basic system status (for load balancers)
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getBasicStatus() {
        try {
            Map<String, Object> healthCheck = monitoringService.performSystemHealthCheck();
            String status = (String) healthCheck.get("overallStatus");
            
            Map<String, Object> basicStatus = Map.of(
                "status", status,
                "timestamp", healthCheck.get("lastChecked"),
                "healthy", "HEALTHY".equals(status)
            );
            
            if ("HEALTHY".equals(status)) {
                return ResponseEntity.ok(basicStatus);
            } else {
                return ResponseEntity.status(503).body(basicStatus); // Service Unavailable
            }
        } catch (Exception e) {
            log.error("Error getting basic system status", e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "ERROR",
                "healthy", false,
                "error", e.getMessage()
            ));
        }
    }
    
    /**
     * Trigger manual maintenance (Admin only)
     */
    @PostMapping("/maintenance")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse> triggerMaintenance() {
        try {
            monitoringService.performAutomatedMaintenance();
            
            return ResponseEntity.ok(ApiResponse.success("Manual maintenance completed successfully", null));
        } catch (Exception e) {
            log.error("Error during manual maintenance", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Manual maintenance failed: " + e.getMessage()));
        }
    }
} 