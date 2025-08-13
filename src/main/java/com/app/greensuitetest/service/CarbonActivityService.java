package com.app.greensuitetest.service;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.app.greensuitetest.dto.carbon.CarbonActivityDto;
import com.app.greensuitetest.model.CarbonActivity;
import com.app.greensuitetest.repository.CarbonActivityRepository;
@Service
public class CarbonActivityService {

    @Autowired
    private CarbonActivityRepository repository;

    public List<CarbonActivityDto> getReportsByCompanyId(String companyId) {
        List<CarbonActivity> activities = repository.findByCompanyId(companyId);
        return activities.stream()
                .map(CarbonActivityDto::fromEntity) // You can use ModelMapper or manual conversion
                .collect(Collectors.toList());
    }
}

