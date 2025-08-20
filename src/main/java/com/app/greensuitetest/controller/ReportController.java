package com.app.greensuitetest.controller;
import com.app.greensuitetest.dto.carbon.CarbonActivityDto;
import com.app.greensuitetest.service.CarbonActivityService;
import com.app.greensuitetest.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/api/reports")
public class ReportController {


    private CarbonActivityService carbonActivityService;
    private final SecurityUtil securityUtil;
    @Autowired
    public ReportController(CarbonActivityService carbonActivityService, SecurityUtil securityUtil) {
        this.carbonActivityService = carbonActivityService;
        this.securityUtil = securityUtil;
    }
    @GetMapping
    public ResponseEntity<List<CarbonActivityDto>> getCompanyReports() {
        //String companyId = SecurityUtil.getCurrentCompanyId(); // <-- You mentioned this is already set up
        String companyId = securityUtil.getCurrentUserCompanyId();
        List<CarbonActivityDto> reports = carbonActivityService.getReportsByCompanyId(companyId);
        return ResponseEntity.ok(reports);
    }
}

