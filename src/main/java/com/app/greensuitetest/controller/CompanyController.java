package com.app.greensuitetest.controller;

import com.app.greensuitetest.model.Company;
import com.app.greensuitetest.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class CompanyController {
    private final CompanyRepository companyRepository;

    @GetMapping("/companies")
    public List<Company> searchCompanies(@RequestParam String query) {
        return companyRepository.findByNameContainingIgnoreCase(query);
    }

    @GetMapping("/companies/{companyId}")
    public ResponseEntity<Company> getCompanyById(@PathVariable String companyId) {
        Optional<Company> company = companyRepository.findById(companyId);
        return company.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}