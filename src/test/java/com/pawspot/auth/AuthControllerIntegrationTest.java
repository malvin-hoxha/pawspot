package com.pawspot.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.pawspot.auth.refreshtoken.repository.RefreshSessionRepository;
import com.pawspot.auth.refreshtoken.repository.RefreshTokenRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import org.hamcrest.Matchers;
import org.springframework.http.HttpHeaders;

import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshSessionRepository sessionRepository;

    @Autowired
    private RefreshTokenRepository tokenRepository;

    @Test
    void shouldRegisterUser() throws Exception {
        String requestBody = """
            {
                "name": "Test User",
                "email": "integration-test@pawspot.com",
                "password": "Password123!"
            }
            """;

        mockMvc.perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("integration-test@pawspot.com"))
        .andExpect(jsonPath("$.name").value("Test User"));
    }

    @Test
    void shouldLoginAndSetAuthCookies() throws Exception {
        String email = "login-test@pawspot.com";
        String password = "Password123!";

        // Create a real user in the test database
        mockMvc.perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Login Test",
                        "email": "login-test@pawspot.com",
                        "password": "Password123!"
                    }
                    """)
        ).andExpect(status().isCreated());

        // Login using that user's credentials
        mockMvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "login-test@pawspot.com",
                        "password": "Password123!"
                    }
                    """)
        )
        .andExpect(status().isOk())
        .andExpect(header().stringValues(
            HttpHeaders.SET_COOKIE,
            Matchers.contains(
                Matchers.allOf(
                    Matchers.containsString("accessToken="),
                    Matchers.containsString("HttpOnly"),
                    Matchers.containsString("Path=/")
                ),
                Matchers.allOf(
                    Matchers.containsString("refreshToken="),
                    Matchers.containsString("HttpOnly"),
                    Matchers.containsString("Path=/api/auth")
                )
            )
        ));
        assertEquals(1, sessionRepository.count());
        assertEquals(1, tokenRepository.count());
    }
}
