package com.pawspot.auth;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.pawspot.auth.refreshtoken.repository.RefreshSessionRepository;
import com.pawspot.auth.refreshtoken.repository.RefreshTokenRepository;
import com.pawspot.security.JwtService;
import com.pawspot.support.PostgresIntegrationTest;
import com.pawspot.user.User;
import com.pawspot.user.UserRepository;

import io.jsonwebtoken.Claims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshSessionRepository sessionRepository;

    @Autowired
    private RefreshTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EntityManager entityManager;

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
        MvcResult loginResult = mockMvc.perform(
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
        )).andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
        assertNotNull(refreshCookie);

        Claims refreshClaims = jwtService.validateRefreshToken(refreshCookie.getValue());
        String sessionId = refreshClaims.get("family_id", String.class);

        assertEquals(
            email,
            sessionRepository.findById(sessionId).orElseThrow().getUser().getEmail()
        );
        assertEquals(
            email,
            tokenRepository.findByTokenId(refreshClaims.getId()).orElseThrow().getUser().getEmail()
        );
    }

    @Test
    void shouldRejectLoginWithIncorrectPassword() throws Exception {
        registerUser(
            "Incorrect Password Test",
            "incorrect-password@pawspot.com",
            "Password123!"
        );

        mockMvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "incorrect-password@pawspot.com",
                        "password": "WrongPassword123!"
                    }
                    """)
        )
        .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectMeWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnCurrentUserWithValidAccessToken() throws Exception {
        String name = "Current User Test";
        String email = "current-user@pawspot.com";
        String password = "Password123!";

        registerUser(name, email, password);

        MvcResult loginResult = login(email, password);
        Cookie accessCookie = loginResult.getResponse().getCookie("accessToken");
        assertNotNull(accessCookie);

        User user = userRepository.findByEmail(email).orElseThrow();

        mockMvc.perform(
            get("/api/auth/me")
                .cookie(accessCookie)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(user.getId()))
        .andExpect(jsonPath("$.name").value(name))
        .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void shouldRejectLoginWithoutCsrfToken() throws Exception {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "csrf-test@pawspot.com",
                        "password": "Password123!"
                    }
                    """)
        )
        .andExpect(status().isForbidden());
    }

    @Test
    void shouldClearAuthCookiesWhenRefreshCookieIsMissing() throws Exception {
        mockMvc.perform(
            post("/api/auth/refresh")
                .with(csrf())
        )
        .andExpect(status().isUnauthorized())
        .andExpect(header().stringValues(
            HttpHeaders.SET_COOKIE,
            Matchers.contains(
                Matchers.allOf(
                    Matchers.containsString("accessToken=;"),
                    Matchers.containsString("Path=/"),
                    Matchers.containsString("Max-Age=0"),
                    Matchers.containsString("HttpOnly")
                ),
                Matchers.allOf(
                    Matchers.containsString("refreshToken=;"),
                    Matchers.containsString("Path=/api/auth"),
                    Matchers.containsString("Max-Age=0"),
                    Matchers.containsString("HttpOnly")
                )
            )
        ));
    }

    @Test
    void shouldNotCreateRefreshArtifactsForIncorrectCredentials() throws Exception {
        String email = "no-refresh-artifacts@pawspot.com";
        registerUser("No Refresh Artifacts Test", email, "Password123!");

        User user = userRepository.findByEmail(email).orElseThrow();

        mockMvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "no-refresh-artifacts@pawspot.com",
                        "password": "WrongPassword123!"
                    }
                    """)
        )
        .andExpect(status().isUnauthorized());

        assertEquals(0L, refreshSessionCountFor(user.getId()));
        assertEquals(0L, refreshTokenCountFor(user.getId()));
    }

    private void registerUser(String name, String email, String password) throws Exception {
        mockMvc.perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "%s",
                        "email": "%s",
                        "password": "%s"
                    }
                    """.formatted(name, email, password))
        )
        .andExpect(status().isCreated());
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "%s",
                        "password": "%s"
                    }
                    """.formatted(email, password))
        )
        .andExpect(status().isOk())
        .andReturn();
    }

    private long refreshSessionCountFor(Long userId) {
        return entityManager.createQuery(
                "select count(session) from RefreshSession session where session.user.id = :userId",
                Long.class
            )
            .setParameter("userId", userId)
            .getSingleResult();
    }

    private long refreshTokenCountFor(Long userId) {
        return entityManager.createQuery(
                "select count(token) from RefreshToken token where token.user.id = :userId",
                Long.class
            )
            .setParameter("userId", userId)
            .getSingleResult();
    }

    @Test
    void shouldLimitAttemptsForTheSameEmailAcrossDifferentIps()
            throws Exception {

        String requestBody = """
            {
                "email": "rate-limit-unknown@pawspot.com",
                "password": "WrongPassword123!"
            }
            """;

        for (int attempt = 1; attempt <= 5; attempt++) {
            String ip = "192.0.2." + attempt;

            mockMvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .with(request -> {
                        request.setRemoteAddr(ip);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isUnauthorized());
        }

        // Changing the IP must not bypass the email limit.
        MvcResult result = mockMvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr("192.0.2.6");
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
        .andReturn();

        long retryAfter = Long.parseLong(
            result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)
        );

        assertTrue(retryAfter > 0 && retryAfter <= 600);
    }

    @Test
    void shouldLimitAttemptsFromTheSameIpAcrossDifferentEmails()
            throws Exception {

        String ip = "192.0.2.100";

        String requestBody = """
            {
                "email": "ip-limit-%d@pawspot.com",
                "password": "WrongPassword123!"
            }
            """;

        for (int attempt = 1; attempt <= 20; attempt++) {
            mockMvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .with(request -> {
                        request.setRemoteAddr(ip);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody.formatted(attempt))
            )
            .andExpect(status().isUnauthorized());
        }

        // A new email must not bypass the exhausted IP limit.
        MvcResult result = mockMvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody.formatted(21))
        )
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
        .andReturn();

        long retryAfter = Long.parseLong(
            result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)
        );

        assertTrue(retryAfter > 0 && retryAfter <= 60);

        // Another IP still has its own attempt budget.
        mockMvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr("192.0.2.101");
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody.formatted(22))
        )
        .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldResetEmailAttemptsAfterSuccessfulLogin() throws Exception {
        String email = "reset-limit@pawspot.com";
        String password = "Password123!";
        String ip = "192.0.2.110";

        registerUser("Reset Limit Test", email, password);

        String requestBody = """
            {
                "email": "reset-limit@pawspot.com",
                "password": "%s"
            }
            """;

        // Each successful fifth attempt must reset the email counter.
        for (int cycle = 0; cycle < 2; cycle++) {
            for (int attempt = 0; attempt < 4; attempt++) {
                mockMvc.perform(
                    post("/api/auth/login")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.formatted("WrongPassword123!"))
                )
                .andExpect(status().isUnauthorized());
            }

            mockMvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .with(request -> {
                        request.setRemoteAddr(ip);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody.formatted(password))
            )
            .andExpect(status().isOk());
        }
    }
}
