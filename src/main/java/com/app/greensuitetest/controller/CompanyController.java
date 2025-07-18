package com.app.greensuitetest.controller;

import com.app.greensuitetest.model.Company;
import com.app.greensuitetest.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class CompanyController {
    private final CompanyRepository companyRepository;

    @GetMapping("/companies")
    public List<Company> searchCompanies(@RequestParam String query) {
        return companyRepository.findByNameContainingIgnoreCase(query);
    }
}