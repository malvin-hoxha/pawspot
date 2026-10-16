package com.pawspot.post;

import com.pawspot.image.CloudinaryService;
import com.pawspot.post.mapper.PetPostMapper;
import com.pawspot.user.User;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;

class PetPostServiceTest {

    @Mock
    private PetPostRepository petPostRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private PetPostMapper petPostMapper;

    @InjectMocks
    private PetPostService petPostService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
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
}