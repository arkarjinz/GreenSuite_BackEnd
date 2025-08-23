package com.app.greensuitetest.controller;

import com.app.greensuitetest.dto.UserProfileDto;
import com.app.greensuitetest.model.Company;
import com.app.greensuitetest.repository.CompanyRepository;
import com.app.greensuitetest.repository.UserRepository;
import com.app.greensuitetest.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class CompanyController {
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;

    @GetMapping("/companies")
    public ResponseEntity<?> searchCompanies(@RequestParam(required = false) String query) {
        try {
            if (query != null && !query.trim().isEmpty()) {
                // Search by query
                List<Company> companies = companyRepository.findByNameContainingIgnoreCase(query.trim());
                return ResponseEntity.ok(companies);
            } else {
                // Return all companies (or empty list if you want to restrict this)
                List<Company> companies = companyRepository.findAll();
                return ResponseEntity.ok(companies);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to search companies"));
        }
    }

    @GetMapping("/companies/{companyId}")
    public ResponseEntity<?> getCompanyById(@PathVariable String companyId) {
        try {
            Optional<Company> company = companyRepository.findById(companyId);
            return company.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get company"));
        }
    }

    /**
     * Get all users from the current user's company (for regular users)
     */
    @GetMapping("/company/users")
    @PreAuthorize("hasRole('OWNER') or hasRole('MANAGER') or hasRole('EMPLOYEE')")
    public ResponseEntity<List<UserProfileDto>> getCompanyUsers() {
        try {
            String companyId = securityUtil.getCurrentUser().getCompanyId();
            if (companyId == null) {
                return ResponseEntity.badRequest().build();
            }

            List<UserProfileDto> users = userRepository.findByCompanyId(companyId)
                .stream()
                .map(UserProfileDto::new)
                .collect(Collectors.toList());

            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(List.of());
        }
    }
}