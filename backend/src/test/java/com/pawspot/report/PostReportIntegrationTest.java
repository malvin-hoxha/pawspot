package com.pawspot.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import com.pawspot.post.PetPost;
import com.pawspot.post.PetPostRepository;
import com.pawspot.post.enums.PetSpecies;
import com.pawspot.post.enums.PostType;
import com.pawspot.report.enums.ReportReason;
import com.pawspot.report.enums.ReportStatus;
import com.pawspot.support.PostgresIntegrationTest;
import com.pawspot.user.User;
import com.pawspot.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class PostReportIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PetPostRepository posts;

    @Autowired
    private PostReportRepository reports;

    private User reporter;
    private Long postId;

    @BeforeEach
    void createPostAndReporter() {
        User owner = createUser("Owner", "owner@test.com");
        reporter = createUser("Reporter", "reporter@test.com");

        PetPost petPost = new PetPost();
        petPost.setUser(owner);
        petPost.setTitle("Lost dog");
        petPost.setDescription("Small brown dog");
        petPost.setCity("Thessaloniki");
        petPost.setType(PostType.LOST);
        petPost.setSpecies(PetSpecies.DOG);
        postId = posts.save(petPost).getId();
    }

    @Test
    void shouldSavePendingReport() throws Exception {
        var result = mockMvc.perform(
            post("/api/posts/{postId}/reports", postId)
                .with(authentication(reporterAuthentication()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason": "SPAM", "description": "  Repeated listing  "}
                    """)
        )
        .andExpect(status().isCreated())
        .andReturn();

        assertEquals(1L, reports.count());
        PostReport saved = reports.findAll().get(0);

        jsonPath("$.id").value(saved.getId()).match(result);
        assertEquals(postId, saved.getReportedPostId());
        assertEquals(reporter.getId(), saved.getReporter().getId());
        assertEquals(ReportReason.SPAM, saved.getReason());
        assertEquals(ReportStatus.PENDING, saved.getStatus());
        assertEquals("Repeated listing", saved.getDescription());
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(
            post("/api/posts/{postId}/reports", postId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason": "SPAM"}
                    """)
        )
        .andExpect(status().isUnauthorized());

        assertEquals(0L, reports.count());
    }

    @Test
    void shouldRejectReportWithoutReason() throws Exception {
        mockMvc.perform(
            post("/api/posts/{postId}/reports", postId)
                .with(authentication(reporterAuthentication()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
        .andExpect(status().isBadRequest());

        assertEquals(0L, reports.count());
    }

    private UsernamePasswordAuthenticationToken reporterAuthentication() {
        return new UsernamePasswordAuthenticationToken(reporter, null, List.of());
    }

    private User createUser(String name, String email) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("test-password");
        return users.save(user);
    }
}