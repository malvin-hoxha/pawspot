package com.pawspot.post;

import java.util.List;
import org.springframework.data.domain.Page;

import com.pawspot.exception.ForbiddenOperationException;
import com.pawspot.exception.image.ImageUploadException;
import com.pawspot.image.CloudinaryService;
import com.pawspot.image.ImageUploadResult;
import com.pawspot.post.enums.PetSpecies;
import com.pawspot.post.enums.PostStatus;
import com.pawspot.post.enums.PostType;
import com.pawspot.post.request.CreatePetPostRequest;
import com.pawspot.post.request.UpdatePetPostRequest;
import com.pawspot.support.PostgresIntegrationTest;
import com.pawspot.user.User;
import com.pawspot.user.UserRepository;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest
public class PetPostIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private PetPostService petPostService;

    @Autowired
    private PetPostRepository petPostRepository;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Test
    void shouldCreatePost() {
        // Arrange: Δημιουργούμε έναν χρήστη στη test DB
        User user = new User();
        user.setName("Test User");
        user.setEmail("create-post@test.com");
        user.setPassword("test-password");

        user = userRepository.save(user);

        // Arrange: Δημιουργούμε το request
        CreatePetPostRequest request = new CreatePetPostRequest();
        request.setTitle("Lost dog");
        request.setDescription("Small brown dog");
        request.setCity("Thessaloniki");
        request.setType(PostType.LOST);
        request.setSpecies(PetSpecies.DOG);

        // Arrange: Mock εικόνας και Cloudinary response
        MultipartFile image = new MockMultipartFile(
            "image",
            "dog.jpg",
            "image/jpeg",
            "fake-image-content".getBytes(StandardCharsets.UTF_8)
        );

        when(cloudinaryService.upload(image))
            .thenReturn(new ImageUploadResult(
                "https://example.com/dog.jpg",
                "pawspot/posts/dog123"
            ));

        // Act: Καλούμε το πραγματικό Service
        PetPost created = petPostService.createPost(
            request,
            image,
            user
        );

        // Assert: Ελέγχουμε τη βάση
        PetPost saved = petPostRepository.findById(created.getId())
            .orElseThrow();

        assertEquals("Lost dog", saved.getTitle());
        assertEquals("Small brown dog", saved.getDescription());
        assertEquals("Thessaloniki", saved.getCity());
        assertEquals(user.getId(), saved.getUser().getId());
        assertEquals("https://example.com/dog.jpg", saved.getImageUrl());
        assertEquals("pawspot/posts/dog123", saved.getImagePublicId());
        assertEquals(PostStatus.ACTIVE, saved.getStatus());

        verify(cloudinaryService).upload(image);
    }

    @Test
    void shouldUpdatePost() {
        // Arrange: Δημιουργία χρήστη
        User user = new User();
        user.setName("Update User");
        user.setEmail("update-post@test.com");
        user.setPassword("test-password");
        user = userRepository.save(user);

        // Δημιουργία αρχικής αγγελίας
        PetPost post = new PetPost();
        post.setTitle("Lost dog");
        post.setDescription("Small brown dog");
        post.setCity("Thessaloniki");
        post.setType(PostType.LOST);
        post.setSpecies(PetSpecies.DOG);
        post.setUser(user);

        post = petPostRepository.save(post);

        // PATCH request: Αλλάζουμε μόνο τον τίτλο
        UpdatePetPostRequest request = new UpdatePetPostRequest();
        request.setTitle("Dog found");

        // Act
        petPostService.updatePost(post.getId(), request, user);

        // Assert: Διαβάζουμε από την PostgreSQL
        PetPost updated = petPostRepository.findById(post.getId())
            .orElseThrow();

        assertEquals("Dog found", updated.getTitle());
        assertEquals("Small brown dog", updated.getDescription());
        assertEquals("Thessaloniki", updated.getCity());
        assertEquals(PostType.LOST, updated.getType());
        assertEquals(PetSpecies.DOG, updated.getSpecies());
        assertEquals(user.getId(), updated.getUser().getId());
    }

    @Test
    void shouldRejectUpdateByNonOwner() {
        // Δημιουργία ιδιοκτήτη
        User owner = new User();
        owner.setName("Owner");
        owner.setEmail("owner@test.com");
        owner.setPassword("test-password");
        owner = userRepository.save(owner);

        // Δημιουργία δεύτερου χρήστη
        User otherUser = new User();
        otherUser.setName("Other User");
        otherUser.setEmail("other@test.com");
        otherUser.setPassword("test-password");
        User savedOtherUser = userRepository.save(otherUser);

        // Αγγελία που ανήκει στον owner
        PetPost post = new PetPost();
        post.setTitle("Lost dog");
        post.setDescription("Small brown dog");
        post.setCity("Thessaloniki");
        post.setType(PostType.LOST);
        post.setSpecies(PetSpecies.DOG);
        post.setUser(owner);
        post = petPostRepository.save(post);

        Long postId = post.getId();

        // Ο δεύτερος χρήστης επιχειρεί να αλλάξει τον τίτλο
        UpdatePetPostRequest request = new UpdatePetPostRequest();
        request.setTitle("Hacked title");

        assertThrows(
            ForbiddenOperationException.class,
            () -> petPostService.updatePost(postId, request, savedOtherUser)
        );

        // Επιβεβαιώνουμε ότι η αγγελία δεν τροποποιήθηκε
        PetPost saved = petPostRepository.findById(postId).orElseThrow();

        assertEquals("Lost dog", saved.getTitle());
        assertEquals(owner.getId(), saved.getUser().getId());
    }

    @Test
    void shouldDeletePost() {
        // Arrange: Δημιουργία χρήστη
        User user = new User();
        user.setName("Delete User");
        user.setEmail("delete-post@test.com");
        user.setPassword("test-password");

        user = userRepository.save(user);

        // Δημιουργία αγγελίας
        PetPost post = new PetPost();
        post.setTitle("Lost dog");
        post.setDescription("Small brown dog");
        post.setCity("Thessaloniki");
        post.setType(PostType.LOST);
        post.setSpecies(PetSpecies.DOG);
        post.setImagePublicId("pawspot/posts/dog123");
        post.setUser(user);

        post = petPostRepository.save(post);

        Long postId = post.getId();

        // Act
        petPostService.deletePost(postId, user);

        // Assert: Δεν υπάρχει πλέον στη βάση
        assertFalse(petPostRepository.existsById(postId));

        // Assert: Ζητήθηκε διαγραφή της εικόνας
        verify(cloudinaryService).delete("pawspot/posts/dog123");
    }

    @Test
    void shouldRejectDeleteByNonOwner() {
        // Arrange: Δημιουργία ιδιοκτήτη
        User owner = new User();
        owner.setName("Owner");
        owner.setEmail("delete-owner@test.com");
        owner.setPassword("test-password");
        owner = userRepository.save(owner);

        // Δημιουργία δεύτερου χρήστη
        User otherUser = new User();
        otherUser.setName("Other User");
        otherUser.setEmail("delete-other@test.com");
        otherUser.setPassword("test-password");

        User savedOtherUser = userRepository.save(otherUser);

        // Αγγελία του owner
        PetPost post = new PetPost();
        post.setTitle("Lost dog");
        post.setDescription("Small brown dog");
        post.setCity("Thessaloniki");
        post.setType(PostType.LOST);
        post.setSpecies(PetSpecies.DOG);
        post.setImagePublicId("pawspot/posts/owner-dog");
        post.setUser(owner);

        Long postId = petPostRepository.save(post).getId();

        // Act: Άλλος χρήστης επιχειρεί διαγραφή
        assertThrows(
            ForbiddenOperationException.class,
            () -> petPostService.deletePost(postId, savedOtherUser)
        );

        // Assert: Η αγγελία εξακολουθεί να υπάρχει
        assertTrue(petPostRepository.existsById(postId));

        // Assert: Δεν ζητήθηκε διαγραφή εικόνας
        verify(cloudinaryService, never()).delete(anyString());
    }

    @Test
    void shouldFilterPosts() {
        // Arrange: Δημιουργία χρήστη
        User user = new User();
        user.setName("Filter User");
        user.setEmail("filter-posts@test.com");
        user.setPassword("test-password");

        User savedUser = userRepository.save(user);

        // Αγγελία 1
        PetPost post1 = new PetPost();
        post1.setTitle("Lost dog");
        post1.setDescription("Brown dog");
        post1.setCity("Thessaloniki");
        post1.setType(PostType.LOST);
        post1.setSpecies(PetSpecies.DOG);
        post1.setUser(savedUser);

        // Αγγελία 2
        PetPost post2 = new PetPost();
        post2.setTitle("Found cat");
        post2.setDescription("White cat");
        post2.setCity("Athens");
        post2.setType(PostType.FOUND);
        post2.setSpecies(PetSpecies.CAT);
        post2.setUser(savedUser);

        // Αγγελία 3
        PetPost post3 = new PetPost();
        post3.setTitle("Lost cat");
        post3.setDescription("Black cat");
        post3.setCity("Thessaloniki");
        post3.setType(PostType.LOST);
        post3.setSpecies(PetSpecies.CAT);
        post3.setUser(savedUser);

        petPostRepository.saveAll(List.of(post1, post2, post3));

        // Act: Ζητάμε LOST αγγελίες στη Θεσσαλονίκη για DOG
        Page<PetPost> result = petPostService.getPosts(
            PostType.LOST,
            null,                 // status
            PetSpecies.DOG,
            "Thessaloniki",
            null,                 // search
            0,                    // page
            10,                   // size
            "createdAt",
            "desc"
        );

        // Assert
        assertEquals(1, result.getTotalElements());
        assertEquals("Lost dog", result.getContent().get(0).getTitle());

        Page<PetPost> activePosts = petPostService.getPosts(
            null,
            PostStatus.ACTIVE,
            null,
            null,
            null,
            0,
            10,
            "createdAt",
            "desc"
        );

        assertEquals(3, activePosts.getTotalElements());

        Page<PetPost> searchResults = petPostService.getPosts(
            null,
            null,
            null,
            null,
            "BROWN",
            0,
            10,
            "createdAt",
            "desc"
        );

        assertEquals(1, searchResults.getTotalElements());
        assertEquals(
            "Lost dog",
            searchResults.getContent().get(0).getTitle()
        );
    }

    @Test
    void shouldPaginateAndSortPosts() {
        User user = new User();
        user.setName("Pagination User");
        user.setEmail("pagination@test.com");
        user.setPassword("test-password");

        User savedUser = userRepository.save(user);

        for (String title : List.of("Alpha", "Bravo", "Charlie")) {
            PetPost post = new PetPost();
            post.setTitle(title);
            post.setDescription("Test description");
            post.setCity("Thessaloniki");
            post.setType(PostType.LOST);
            post.setSpecies(PetSpecies.DOG);
            post.setUser(savedUser);

            petPostRepository.save(post);
        }

        // Ζητάμε τη δεύτερη σελίδα, με 2 posts ανά σελίδα
        // και αλφαβητική ταξινόμηση τίτλου.
        Page<PetPost> result = petPostService.getPosts(
            null, null, null, null, null,
            1, 2, "title", "asc"
        );

        assertEquals(3, result.getTotalElements());
        assertEquals(2, result.getTotalPages());
        assertEquals(1, result.getContent().size());
        assertEquals("Charlie", result.getContent().get(0).getTitle());
    }

    @Test
    void shouldDeletePostEvenWhenCloudinaryFails() {
        // Arrange: Δημιουργία χρήστη
        User user = new User();
        user.setName("Cloudinary Failure User");
        user.setEmail("cloudinary-failure@test.com");
        user.setPassword("test-password");

        User savedUser = userRepository.save(user);

        // Δημιουργία αγγελίας
        PetPost post = new PetPost();
        post.setTitle("Lost dog");
        post.setDescription("Small brown dog");
        post.setCity("Thessaloniki");
        post.setType(PostType.LOST);
        post.setSpecies(PetSpecies.DOG);
        post.setImagePublicId("pawspot/posts/delete-failure");
        post.setUser(savedUser);

        Long postId = petPostRepository.save(post).getId();

        // Προσομοιώνουμε αποτυχία του Cloudinary
        doThrow(new ImageUploadException("Cloudinary unavailable"))
            .when(cloudinaryService)
            .delete("pawspot/posts/delete-failure");

        // Act & Assert: Δεν πρέπει να πεταχτεί exception
        assertDoesNotThrow(
            () -> petPostService.deletePost(postId, savedUser)
        );

        // Η αγγελία πρέπει να έχει διαγραφεί από τη βάση
        assertFalse(petPostRepository.existsById(postId));

        // Το Cloudinary cleanup πρέπει να έχει επιχειρηθεί
        verify(cloudinaryService).delete("pawspot/posts/delete-failure");
    }

    
    @Test
    void shouldRejectStalePetPostUpdate() {
        // 1. Δημιουργούμε χρήστη
        User user = new User();
        user.setName("Version Test User");
        user.setEmail("version-test@example.com");
        user.setPassword(passwordEncoder.encode("password123"));

        User savedUser = userRepository.save(user);

        // 2. Δημιουργούμε αγγελία
        PetPost post = new PetPost();
        post.setTitle("Lost Dog");
        post.setDescription("Small brown dog");
        post.setCity("Athens");
        post.setType(PostType.LOST);
        post.setSpecies(PetSpecies.DOG);
        post.setUser(savedUser);

        Long postId = petPostRepository.saveAndFlush(post).getId();

        // 3. Διαβάζουμε την αρχική έκδοση σε ξεχωριστό transaction
        PetPost stalePost = transactionTemplate.execute(status ->
            petPostRepository.findById(postId).orElseThrow()
        );

        Long originalVersion = stalePost.getVersion();

        // 4. Ένα άλλο transaction αλλάζει την αγγελία
        transactionTemplate.executeWithoutResult(status -> {
            PetPost currentPost = petPostRepository.findById(postId)
                .orElseThrow();

            currentPost.setTitle("Missing Dog");
        });

        // 5. Επιβεβαιώνουμε ότι αυξήθηκε το version
        PetPost updatedPost = petPostRepository.findById(postId)
            .orElseThrow();

        assertEquals(originalVersion + 1, updatedPost.getVersion());

        // 6. Προσπαθούμε να αποθηκεύσουμε την παλιά έκδοση
        stalePost.setCity("Thessaloniki");

        assertThrows(
            OptimisticLockingFailureException.class,
            () -> petPostRepository.saveAndFlush(stalePost)
        );

        // 7. Η πρώτη αλλαγή πρέπει να έχει διατηρηθεί
        PetPost finalPost = petPostRepository.findById(postId)
            .orElseThrow();

        assertEquals("Missing Dog", finalPost.getTitle());
        assertEquals("Athens", finalPost.getCity());
    }

    @Test
    void shouldRejectInvalidMyPostsPageSize() throws Exception {

        MockMvc mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()
            )
            .build();

        mockMvc.perform(
            get("/api/posts/my")
                .param("size", "101")
                .with(SecurityMockMvcRequestPostProcessors.user("test-user"))
        )
        .andExpect(status().isBadRequest());
    }
}
