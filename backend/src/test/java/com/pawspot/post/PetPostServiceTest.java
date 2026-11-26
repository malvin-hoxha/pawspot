package com.pawspot.post;

import com.pawspot.image.CloudinaryService;
import com.pawspot.image.ImageUploadResult;
import com.pawspot.image.cleanup.ImageDeletionQueueService;
import com.pawspot.post.mapper.PetPostMapper;
import com.pawspot.post.request.CreatePetPostRequest;
import com.pawspot.user.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.multipart.MultipartFile;

class PetPostServiceTest {

    @Mock
    private PetPostRepository petPostRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private PetPostMapper petPostMapper;

    @InjectMocks
    private PetPostService petPostService;

    @Mock
    private ImageDeletionQueueService imageDeletionQueueService;

    @Mock
    private MultipartFile image;

    @Mock
    private CreatePetPostRequest createRequest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldNotQueueOrDeleteImageWhenPostCreationSucceeds() {
        User user = new User();
        PetPost mappedPost = new PetPost();
        PetPost savedPost = new PetPost();

        when(cloudinaryService.upload(image)).thenReturn(
            new ImageUploadResult(
                "https://example.com/uploaded.jpg",
                "pawspot/posts/uploaded"
            )
        );
        when(petPostMapper.toEntity(createRequest)).thenReturn(mappedPost);
        when(petPostRepository.save(mappedPost)).thenReturn(savedPost);

        PetPost result = petPostService.createPost(
            createRequest,
            image,
            user
        );

        assertSame(savedPost, result);
        verify(imageDeletionQueueService, never())
            .enqueueAfterFailedCreation(anyString());
        verify(cloudinaryService, never()).delete(anyString());
    }

    @Test
    void shouldQueueUploadedImageWhenRepositorySaveFails() {
        User user = new User();
        PetPost mappedPost = new PetPost();
        RuntimeException creationFailure =
            new DataIntegrityViolationException("Database save failed");

        when(cloudinaryService.upload(image)).thenReturn(
            new ImageUploadResult(
                "https://example.com/failed.jpg",
                "pawspot/posts/failed-save"
            )
        );
        when(petPostMapper.toEntity(createRequest)).thenReturn(mappedPost);
        when(petPostRepository.save(mappedPost)).thenThrow(creationFailure);

        RuntimeException thrown = assertThrows(
            RuntimeException.class,
            () -> petPostService.createPost(createRequest, image, user)
        );

        assertSame(creationFailure, thrown);
        verify(imageDeletionQueueService)
            .enqueueAfterFailedCreation("pawspot/posts/failed-save");
        verify(cloudinaryService, never()).delete(anyString());
    }

    @Test
    void shouldQueueUploadedImageWhenMappingFails() {
        User user = new User();
        RuntimeException mappingFailure =
            new IllegalStateException("Mapping failed");

        when(cloudinaryService.upload(image)).thenReturn(
            new ImageUploadResult(
                "https://example.com/failed.jpg",
                "pawspot/posts/failed-mapping"
            )
        );
        when(petPostMapper.toEntity(createRequest)).thenThrow(mappingFailure);

        RuntimeException thrown = assertThrows(
            RuntimeException.class,
            () -> petPostService.createPost(createRequest, image, user)
        );

        assertSame(mappingFailure, thrown);
        verify(imageDeletionQueueService)
            .enqueueAfterFailedCreation("pawspot/posts/failed-mapping");
        verify(petPostRepository, never()).save(any(PetPost.class));
        verify(cloudinaryService, never()).delete(anyString());
    }

    @Test
    void shouldSuppressQueueFailureAndRethrowCreationFailure() {
        User user = new User();
        RuntimeException creationFailure =
            new IllegalStateException("Mapping failed");
        RuntimeException queueFailure =
            new IllegalStateException("Queueing failed");

        when(cloudinaryService.upload(image)).thenReturn(
            new ImageUploadResult(
                "https://example.com/failed.jpg",
                "pawspot/posts/queue-failed"
            )
        );
        when(petPostMapper.toEntity(createRequest)).thenThrow(creationFailure);
        doThrow(queueFailure)
            .when(imageDeletionQueueService)
            .enqueueAfterFailedCreation("pawspot/posts/queue-failed");

        RuntimeException thrown = assertThrows(
            RuntimeException.class,
            () -> petPostService.createPost(createRequest, image, user)
        );

        assertSame(creationFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(queueFailure, thrown.getSuppressed()[0]);
        verify(cloudinaryService, never()).delete(anyString());
    }

    @Test
    void shouldNotDeleteImageWhenDatabaseDeleteFails() {
        // Arrange: Δημιουργία ιδιοκτήτη
        User user = new User();
        user.setId(1L);

        // Δημιουργία αγγελίας
        PetPost post = new PetPost();
        post.setId(10L);
        post.setUser(user);
        post.setImagePublicId("pawspot/posts/dog123");

        // Το repository επιστρέφει την αγγελία
        when(petPostRepository.findById(10L))
            .thenReturn(Optional.of(post));

        // Προσομοιώνουμε αποτυχία κατά τη διαγραφή
        doThrow(new DataIntegrityViolationException("Database delete failed"))
            .when(petPostRepository)
            .delete(post);

        // Act & Assert
        assertThrows(
            DataIntegrityViolationException.class,
            () -> petPostService.deletePost(10L, user)
        );

        // Δεν πρέπει να διαγραφεί η εικόνα
        verify(cloudinaryService, never()).delete(anyString());
    }

    @Test
    void shouldUseIdAsTieBreakerWhenSortingPosts() {

        // Act
        petPostService.getPosts(
            null, null, null, null, null,
            0, 10, "createdAt", "desc"
        );

        // Capture του Pageable που πέρασε στο repository
        ArgumentCaptor<Pageable> pageableCaptor =
            ArgumentCaptor.forClass(Pageable.class);

        verify(petPostRepository).findAll(
            org.mockito.ArgumentMatchers.<Specification<PetPost>>any(),
            pageableCaptor.capture()
        );

        Pageable pageable = pageableCaptor.getValue();

        // Έλεγχος του sorting
        assertEquals(
            Sort.Direction.DESC,
            pageable.getSort().getOrderFor("createdAt").getDirection()
        );

        assertEquals(
            Sort.Direction.DESC,
            pageable.getSort().getOrderFor("id").getDirection()
        );
    }

    @Test
    void shouldPaginateMyPostsWithStableSorting() {

        // Act
        petPostService.getMyPosts(42L, 1, 10);

        // Capture του Pageable
        ArgumentCaptor<Pageable> pageableCaptor =
            ArgumentCaptor.forClass(Pageable.class);

        verify(petPostRepository).findByUserId(
            eq(42L),
            pageableCaptor.capture()
        );

        Pageable pageable = pageableCaptor.getValue();

        // Pagination
        assertEquals(1, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());

        // Sorting
        assertEquals(
            Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "id")),
            pageable.getSort()
        );
    }

}
