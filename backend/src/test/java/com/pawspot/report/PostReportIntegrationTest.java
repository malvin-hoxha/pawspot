package com.pawspot.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.pawspot.image.CloudinaryService;
import com.pawspot.image.cleanup.ImageDeletionTaskRepository;
import com.pawspot.post.PetPost;
import com.pawspot.post.PetPostRepository;
import com.pawspot.post.enums.PetSpecies;
import com.pawspot.post.enums.PostType;
import com.pawspot.report.enums.ReportReason;
import com.pawspot.report.enums.ReportStatus;
import com.pawspot.support.PostgresIntegrationTest;
import com.pawspot.user.User;
import com.pawspot.user.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.pawspot.security.JwtService;
import com.pawspot.user.enums.UserRole;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import java.time.Instant;

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

    @Autowired
    private ImageDeletionTaskRepository imageDeletionTasks;

    private User reporter;
    private Long postId;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @Autowired
    private PostReportService postReportService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Cookie accessCookie(User user) {
        return new Cookie("accessToken", jwtService.generateAccessToken(user));
    }

    private Long saveReportWithResolution(
        ReportStatus status,
        Instant resolvedAt
    ) {
        PostReport report = new PostReport(
            posts.findById(postId).orElseThrow(),
            reporter,
            ReportReason.SPAM,
            "Cleanup test"
        );

        report.setStatus(status);
        report.setResolvedAt(resolvedAt);

        return reports.saveAndFlush(report).getId();
    }

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
        assertNull(saved.getResolvedAt());
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

    @Test
    void shouldRestrictReportQueueToAdmins() throws Exception {
        mockMvc.perform(get("/api/admin/post-reports"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/post-reports")
                .cookie(accessCookie(reporter)))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnEmptyThenPopulatedReportQueueForAdmin() throws Exception {
        User admin = createUser("Admin", "admin@test.com");
        admin.setRole(UserRole.ADMIN);
        admin = users.saveAndFlush(admin);
        Cookie cookie = accessCookie(admin);

        mockMvc.perform(get("/api/admin/post-reports").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(0));

        PostReport saved = reports.save(new PostReport(
            posts.findById(postId).orElseThrow(),
            reporter,
            ReportReason.SPAM,
            "Repeated listing"
        ));

        mockMvc.perform(get("/api/admin/post-reports").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(saved.getId()))
            .andExpect(jsonPath("$.content[0].status").value("PENDING"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldDismissReportAsAdmin() throws Exception {
        User admin = createUser("Admin", "admin@test.com");
        admin.setRole(UserRole.ADMIN);
        admin = users.saveAndFlush(admin);

        PostReport report = reports.saveAndFlush(
            new PostReport(
                posts.findById(postId).orElseThrow(),
                reporter,
                ReportReason.SPAM,
                "Repeated listing"
            )
        );

        mockMvc.perform(
                post("/api/admin/post-reports/{reportId}/dismiss", report.getId())
                    .cookie(accessCookie(admin))
                    .with(csrf())
            )
            .andExpect(status().isNoContent());

        PostReport updated = reports.findById(report.getId()).orElseThrow();

        assertEquals(ReportStatus.DISMISSED, updated.getStatus());

        var resolvedAt = updated.getResolvedAt();
        assertNotNull(resolvedAt);

        // Repeating the action must not extend the retention period.
        mockMvc.perform(
            post("/api/admin/post-reports/{reportId}/dismiss", report.getId())
                .cookie(accessCookie(admin))
                .with(csrf())
        )
        .andExpect(status().isConflict());

        PostReport unchanged = reports.findById(report.getId()).orElseThrow();

        assertEquals(ReportStatus.DISMISSED, unchanged.getStatus());
        assertEquals(resolvedAt, unchanged.getResolvedAt());
    }

    @Test
    void shouldRejectDismissalByRegularUser() throws Exception {
        PostReport report = reports.saveAndFlush(
            new PostReport(
                posts.findById(postId).orElseThrow(),
                reporter,
                ReportReason.SPAM,
                "Repeated listing"
            )
        );

        mockMvc.perform(
                post("/api/admin/post-reports/{reportId}/dismiss", report.getId())
                    .cookie(accessCookie(reporter))
                    .with(csrf())
            )
            .andExpect(status().isForbidden());

        PostReport unchanged = reports.findById(report.getId()).orElseThrow();

        assertEquals(ReportStatus.PENDING, unchanged.getStatus());
    }

    @Test
    void shouldRemovePostAndResolveItsPendingReportsAsAdmin() throws Exception {
        User admin = createUser("Admin", "admin@test.com");
        admin.setRole(UserRole.ADMIN);
        admin = users.saveAndFlush(admin);

        PetPost post = posts.findById(postId).orElseThrow();
        post.setImagePublicId("pawspot/posts/reported-post");
        posts.saveAndFlush(post);

        PostReport firstReport = reports.saveAndFlush(
            new PostReport(post, reporter, ReportReason.SPAM, "Repeated listing")
        );

        PostReport secondReport = reports.saveAndFlush(
            new PostReport(post, reporter, ReportReason.SPAM, "Another report")
        );

        mockMvc.perform(
                post(
                    "/api/admin/post-reports/{reportId}/remove-post",
                    firstReport.getId()
                )
                    .cookie(accessCookie(admin))
                    .with(csrf())
            )
            .andExpect(status().isNoContent());

        assertFalse(posts.existsById(postId));

        var resolvedAt = reports.findById(firstReport.getId())
            .orElseThrow()
            .getResolvedAt();

        assertNotNull(resolvedAt);

        for (Long reportId : List.of(firstReport.getId(), secondReport.getId())) {
            PostReport updated = reports.findById(reportId).orElseThrow();

            assertEquals(ReportStatus.POST_REMOVED, updated.getStatus());
            assertEquals(postId, updated.getReportedPostId());
            assertNull(updated.getPost());
            assertEquals(resolvedAt, updated.getResolvedAt());
        }

        var queuedTasks = imageDeletionTasks.findAll();

        assertEquals(1, queuedTasks.size());
        assertEquals(
            "pawspot/posts/reported-post",
            queuedTasks.get(0).getPublicId()
        );

        verifyNoInteractions(cloudinaryService);
    }

    @Test
    void shouldRejectPostRemovalByRegularUser() throws Exception {
        PostReport report = reports.saveAndFlush(
            new PostReport(
                posts.findById(postId).orElseThrow(),
                reporter,
                ReportReason.SPAM,
                "Repeated listing"
            )
        );

        mockMvc.perform(
                post(
                    "/api/admin/post-reports/{reportId}/remove-post",
                    report.getId()
                )
                    .cookie(accessCookie(reporter))
                    .with(csrf())
            )
            .andExpect(status().isForbidden());

        assertTrue(posts.existsById(postId));

        PostReport unchanged = reports.findById(report.getId()).orElseThrow();
        assertEquals(ReportStatus.PENDING, unchanged.getStatus());
        assertEquals(0L, imageDeletionTasks.count());

        verifyNoInteractions(cloudinaryService);
    }

    @Test
    void shouldRollbackPostRemovalAndQueuedTaskTogether() {
        User admin = createUser("Admin", "admin@test.com");
        admin.setRole(UserRole.ADMIN);
        users.saveAndFlush(admin);

        PetPost post = posts.findById(postId).orElseThrow();
        post.setImagePublicId("pawspot/posts/rollback-reported-post");
        posts.saveAndFlush(post);

        PostReport report = reports.saveAndFlush(
            new PostReport(
                post,
                reporter,
                ReportReason.SPAM,
                "Repeated listing"
            )
        );

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            postReportService.removeReportedPost(report.getId(), admin);

            // Execute the database changes before deliberately rolling them back.
            reports.flush();

            assertFalse(posts.existsById(postId));
            assertEquals(1L, imageDeletionTasks.count());

            status.setRollbackOnly();
        });

        assertTrue(posts.existsById(postId));
        assertEquals(0L, imageDeletionTasks.count());

        PostReport restored = reports.findById(report.getId()).orElseThrow();
        assertEquals(ReportStatus.PENDING, restored.getStatus());
        assertNull(restored.getResolvedAt());

        verifyNoInteractions(cloudinaryService);
    }

    @Test
    void shouldPreserveStatusDuringInternalErrorDispatch() throws Exception {
        mockMvc.perform(get("/error"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(
                get("/error")
                    .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                    .requestAttr(
                        RequestDispatcher.ERROR_REQUEST_URI,
                        "/api/admin/post-reports"
                    )
                    .with(request -> {
                        request.setDispatcherType(DispatcherType.ERROR);
                        return request;
                    })
            )
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    void shouldDeleteOnlyResolvedReportsAtOrBeforeTheCutoff() {
        Instant cutoff = Instant.parse("2026-11-01T00:00:00Z");

        Long oldDismissed = saveReportWithResolution(
            ReportStatus.DISMISSED,
            cutoff.minusSeconds(1)
        );

        Long oldRemoved = saveReportWithResolution(
            ReportStatus.POST_REMOVED,
            cutoff.minusSeconds(1)
        );

        Long exactlyAtCutoff = saveReportWithResolution(
            ReportStatus.DISMISSED,
            cutoff
        );

        Long recentDismissed = saveReportWithResolution(
            ReportStatus.DISMISSED,
            cutoff.plusSeconds(1)
        );

        Long recentRemoved = saveReportWithResolution(
            ReportStatus.POST_REMOVED,
            cutoff.plusSeconds(1)
        );

        Long pending = saveReportWithResolution(
            ReportStatus.PENDING,
            null
        );

        Long missingResolutionTime = saveReportWithResolution(
            ReportStatus.DISMISSED,
            null
        );

        int deleted = postReportService.cleanupResolvedReports(cutoff);

        assertEquals(3, deleted);

        for (Long id : List.of(oldDismissed, oldRemoved, exactlyAtCutoff)) {
            assertFalse(reports.existsById(id));
        }

        for (Long id : List.of(
            recentDismissed,
            recentRemoved,
            pending,
            missingResolutionTime
        )) {
            assertTrue(reports.existsById(id));
        }

        // Deleting reports must preserve the listing itself.
        assertTrue(posts.existsById(postId));

        // Repeating cleanup with the same cutoff must not delete anything else.
        assertEquals(0, postReportService.cleanupResolvedReports(cutoff));
    }
}
