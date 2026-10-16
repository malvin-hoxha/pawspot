package com.pawspot.post;

import com.pawspot.exception.ForbiddenOperationException;
import com.pawspot.image.CloudinaryService;
import com.pawspot.image.ImageUploadResult;
import com.pawspot.post.enums.PetSpecies;
import com.pawspot.post.enums.PostStatus;
import com.pawspot.post.enums.PostType;
import com.pawspot.post.request.CreatePetPostRequest;
import com.pawspot.post.request.UpdatePetPostRequest;
import com.pawspot.user.User;
import com.pawspot.user.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
public class PetPostIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private PetPostService petPostService;

    @Autowired
    private PetPostRepository petPostRepository;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @Autowired
    private UserRepository userRepository;

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
}
