package com.app.greensuitetest.security;

import com.app.greensuitetest.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String errorMessage = "Authentication required. " +
                (authException != null ? authException.getMessage() : "Invalid credentials");

        ApiResponse apiResponse = ApiResponse.error(
                "Unauthorized access",
                Map.of(
                        "path", request.getRequestURI(),
                        "error", errorMessage,
                        "solution", "Include valid JWT token in Authorization header",
                        "documentation", "/api-docs#section/Authentication"
                )
        );

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}