package com.pawspot.auth.user;

import com.pawspot.image.CloudinaryService;
import com.pawspot.post.PetPost;
import com.pawspot.post.PetPostRepository;
import com.pawspot.post.enums.PetSpecies;
import com.pawspot.post.enums.PostType;
import com.pawspot.security.JwtService;
import com.pawspot.user.User;
import com.pawspot.user.UserRepository;
import com.pawspot.user.UserService;
import com.pawspot.auth.refreshtoken.repository.RefreshTokenRepository;
import com.pawspot.auth.refreshtoken.service.RefreshTokenService;
import com.pawspot.exception.InvalidCredentialsException;
import com.pawspot.auth.refreshtoken.repository.RefreshSessionRepository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.util.List;

@AutoConfigureMockMvc
@SpringBootTest
@Testcontainers
class UserDeletionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PetPostRepository petPostRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldDeleteAccountAndRelatedData() {

        // Arrange: Δημιουργία χρήστη
        User user = new User();
        user.setName("Delete Account User");
        user.setEmail("delete-account@test.com");
        user.setPassword(passwordEncoder.encode("password123"));

        User savedUser = userRepository.save(user);

        // Arrange: Δημιουργία αγγελίας
        PetPost post = new PetPost();
        post.setTitle("Lost dog");
        post.setDescription("Small brown dog");
        post.setCity("Thessaloniki");
        post.setType(PostType.LOST);
        post.setSpecies(PetSpecies.DOG);
        post.setImagePublicId("pawspot/posts/delete-account-dog");
        post.setUser(savedUser);

        PetPost savedPost = petPostRepository.save(post);

        // Δημιουργεί refresh session και το πρώτο refresh token
        refreshTokenService.createSession(savedUser);

        Long userId = savedUser.getId();
        Long postId = savedPost.getId();

        // Act: Διαγραφή λογαριασμού
        userService.deleteAccount(userId, "password123");

        // Assert: Ο χρήστης διαγράφηκε
        assertFalse(userRepository.existsById(userId));

        // Assert: Η αγγελία διαγράφηκε
        assertFalse(petPostRepository.existsById(postId));

        // Assert: Δεν υπάρχουν πλέον refresh tokens και sessions
        assertFalse(refreshTokenRepository.existsByUserId(userId));
        assertFalse(refreshSessionRepository.existsByUserId(userId));

        // Assert: Ζητήθηκε διαγραφή της εικόνας
        verify(cloudinaryService).delete("pawspot/posts/delete-account-dog");
    }

    @Test
    void shouldRejectAccountDeletionWithWrongPassword() {
        User user = new User();
        user.setName("Wrong Password User");
        user.setEmail("wrong-password@test.com");
        user.setPassword(passwordEncoder.encode("correctPassword"));

        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        assertThrows(
            InvalidCredentialsException.class,
            () -> userService.deleteAccount(userId, "wrongPassword")
        );

        assertTrue(userRepository.existsById(userId));

        verify(cloudinaryService, never()).delete(anyString());
    }

    @Test
    void shouldRollbackAccountDeletionWhenDatabaseFails() {
        User user = new User();
        user.setName("Rollback User");
        user.setEmail("rollback@test.com");
        user.setPassword(passwordEncoder.encode("password123"));

        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        PetPost post = new PetPost();
        post.setTitle("Lost dog");
        post.setDescription("Test description");
        post.setCity("Thessaloniki");
        post.setType(PostType.LOST);
        post.setSpecies(PetSpecies.DOG);
        post.setImagePublicId("pawspot/posts/rollback-image");
        post.setUser(savedUser);

        Long postId = petPostRepository.save(post).getId();

        refreshTokenService.createSession(savedUser);

        // Trigger που αναγκάζει τη διαγραφή χρήστη να αποτύχει
        jdbcTemplate.execute("""
            CREATE OR REPLACE FUNCTION reject_user_delete()
            RETURNS trigger AS $$
            BEGIN
                RAISE EXCEPTION 'Simulated database failure';
            END;
            $$ LANGUAGE plpgsql;
            """);

        jdbcTemplate.execute("""
            CREATE TRIGGER reject_user_delete_trigger
            BEFORE DELETE ON users
            FOR EACH ROW
            EXECUTE FUNCTION reject_user_delete();
            """);

        try {
            assertThrows(
                RuntimeException.class,
                () -> userService.deleteAccount(userId, "password123")
            );

            // Οι προηγούμενες διαγραφές πρέπει να έχουν γίνει rollback
            assertTrue(userRepository.existsById(userId));
            assertTrue(petPostRepository.existsById(postId));

            assertFalse(refreshTokenRepository.findAll().isEmpty());
            assertFalse(refreshSessionRepository.findAll().isEmpty());

            // Το Cloudinary δεν πρέπει να κληθεί
            verify(cloudinaryService, never()).delete(anyString());

        } finally {
            jdbcTemplate.execute(
                "DROP TRIGGER IF EXISTS reject_user_delete_trigger ON users"
            );
            jdbcTemplate.execute(
                "DROP FUNCTION IF EXISTS reject_user_delete()"
            );
        }
    }

    @Test
    void shouldDeleteAccountEvenWhenCloudinaryFails() {
        // Arrange: Δημιουργία χρήστη
        User user = new User();
        user.setName("Cloudinary Failure User");
        user.setEmail("cloudinary-delete@test.com");
        user.setPassword(passwordEncoder.encode("password123"));

        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        // Δημιουργία αγγελίας με εικόνα
        PetPost post = new PetPost();
        post.setTitle("Lost dog");
        post.setDescription("Small brown dog");
        post.setCity("Thessaloniki");
        post.setType(PostType.LOST);
        post.setSpecies(PetSpecies.DOG);
        post.setImagePublicId("pawspot/posts/cloudinary-failure");
        post.setUser(savedUser);

        Long postId = petPostRepository.save(post).getId();

        // Προσομοίωση αποτυχίας Cloudinary
        doThrow(new RuntimeException("Cloudinary unavailable"))
            .when(cloudinaryService)
            .delete("pawspot/posts/cloudinary-failure");

        // Act: Η διαγραφή λογαριασμού δεν πρέπει να αποτύχει
        assertDoesNotThrow(
            () -> userService.deleteAccount(userId, "password123")
        );

        // Assert: Ο χρήστης διαγράφηκε από την PostgreSQL
        assertFalse(userRepository.existsById(userId));

        // Assert: Η αγγελία διαγράφηκε
        assertFalse(petPostRepository.existsById(postId));

        // Assert: Το Cloudinary cleanup επιχειρήθηκε
        verify(cloudinaryService)
            .delete("pawspot/posts/cloudinary-failure");
    }

    @Test
    void shouldDeleteAccountThroughHttp() throws Exception {
        // Arrange: Πραγματικός χρήστης στην PostgreSQL
        User user = new User();
        user.setName("HTTP Delete User");
        user.setEmail("http-delete@test.com");
        user.setPassword(passwordEncoder.encode("password123"));

        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        // Δημιουργούμε πραγματικό access JWT
        String accessToken = jwtService.generateAccessToken(savedUser);

        // Act & Assert: Καλούμε το πραγματικό HTTP endpoint
        mockMvc.perform(
            MockMvcRequestBuilders.delete("/api/users/me")
                .cookie(new MockCookie("accessToken", accessToken))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"password":"password123"}
                    """)
        )
        .andExpect(status().isNoContent())
        .andExpect(header().exists(HttpHeaders.SET_COOKIE))
        .andExpect(result -> {
            List<String> cookies = result.getResponse()
                .getHeaders(HttpHeaders.SET_COOKIE);

            assertTrue(cookies.stream()
                .anyMatch(cookie -> cookie.startsWith("accessToken=")
                    && cookie.contains("Max-Age=0")));

            assertTrue(cookies.stream()
                .anyMatch(cookie -> cookie.startsWith("refreshToken=")
                    && cookie.contains("Max-Age=0")));
        });

        // Επιβεβαιώνουμε ότι η διαγραφή έγινε commit
        assertFalse(userRepository.existsById(userId));
    }

    @Test
    void shouldRejectRegistrationWithOversizedPassword() throws Exception {
        String password = "a".repeat(73);

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Test User",
                        "email": "oversized@test.com",
                        "password": "%s"
                    }
                    """.formatted(password))
        )
        .andExpect(status().isBadRequest());

        assertFalse(userRepository.existsByEmail("oversized@test.com"));
    }

    @Test
    void shouldRejectOversizedLoginPasswordRegardlessOfEmail()
        throws Exception {

        User user = new User();
        user.setName("Password Limit User");
        user.setEmail("password-limit@test.com");
        user.setPassword(passwordEncoder.encode("password123"));

        userRepository.save(user);

        String password = "a".repeat(73);

        for (String email : List.of(
            "password-limit@test.com",
            "unknown-password-limit@test.com"
        )) {
            mockMvc.perform(
                MockMvcRequestBuilders.post("/api/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "email": "%s",
                            "password": "%s"
                        }
                        """.formatted(email, password))
            )
            .andExpect(status().isBadRequest());
        }
    }

    @Test
    void shouldValidatePasswordUsingUtf8Bytes() throws Exception {
        String password = "α".repeat(37);

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Greek Password User",
                        "email": "greek-password@test.com",
                        "password": "%s"
                    }
                    """.formatted(password))
        )
        .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnConflictForDuplicateRegistration() throws Exception {

        User user = new User();
        user.setName("Existing User");
        user.setEmail("duplicate@test.com");
        user.setPassword(passwordEncoder.encode("password123"));

        userRepository.save(user);

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Second User",
                        "email": "duplicate@test.com",
                        "password": "password123"
                    }
                    """)
        )
        .andExpect(status().isConflict());

        assertEquals(1, userRepository.findAll().stream()
            .filter(u -> u.getEmail().equals("duplicate@test.com"))
            .count());
    }

    @Test
    void shouldNormalizeEmailOnRegistration() throws Exception {

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Normalized User",
                        "email": "MixedCase@Test.com",
                        "password": "password123"
                    }
                    """)
        )
        .andExpect(status().isCreated());

        assertTrue(userRepository.existsByEmail("mixedcase@test.com"));
    }

    @Test
    void shouldRejectDuplicateEmailRegardlessOfCase() throws Exception {

        User user = new User();
        user.setName("First User");
        user.setEmail("case-test@test.com");
        user.setPassword(passwordEncoder.encode("password123"));
        userRepository.save(user);

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Second User",
                        "email": "CASE-TEST@TEST.COM",
                        "password": "password123"
                    }
                    """)
        )
        .andExpect(status().isConflict());
    }





}