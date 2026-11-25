package com.pawspot.image;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.pawspot.exception.image.ImageUploadException;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    private static final String PUBLIC_ID = "pawspot/posts/test-image";

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private CloudinaryService service;

    @BeforeEach
    void setUp() {
        when(cloudinary.uploader()).thenReturn(uploader);
        service = new CloudinaryService(cloudinary);
    }

    @Test
    void shouldCompleteWhenDestroyReturnsOk() throws IOException {
        when(uploader.destroy(eq(PUBLIC_ID), anyMap()))
            .thenReturn(Map.of("result", "ok"));

        assertDoesNotThrow(() -> service.delete(PUBLIC_ID));
    }

    @Test
    void shouldCompleteWhenDestroyReturnsNotFound() throws IOException {
        when(uploader.destroy(eq(PUBLIC_ID), anyMap()))
            .thenReturn(Map.of("result", "not found"));

        assertDoesNotThrow(() -> service.delete(PUBLIC_ID));
    }

    @Test
    void shouldRejectUnexpectedDestroyResult() throws IOException {
        when(uploader.destroy(eq(PUBLIC_ID), anyMap()))
            .thenReturn(Map.of("result", "pending"));

        assertThrows(
            ImageUploadException.class,
            () -> service.delete(PUBLIC_ID)
        );
    }

    @Test
    void shouldRejectEmptyDestroyResponse() throws IOException {
        when(uploader.destroy(eq(PUBLIC_ID), anyMap()))
            .thenReturn(Map.of());

        assertThrows(
            ImageUploadException.class,
            () -> service.delete(PUBLIC_ID)
        );
    }

    @Test
    void shouldRejectNullDestroyResponse() throws IOException {
        when(uploader.destroy(eq(PUBLIC_ID), anyMap()))
            .thenReturn(null);

        assertThrows(
            ImageUploadException.class,
            () -> service.delete(PUBLIC_ID)
        );
    }

    @Test
    void shouldWrapIOExceptionAndPreserveCause() throws IOException {
        IOException cause = new IOException("Cloudinary unavailable");

        when(uploader.destroy(eq(PUBLIC_ID), anyMap()))
            .thenThrow(cause);

        ImageUploadException exception = assertThrows(
            ImageUploadException.class,
            () -> service.delete(PUBLIC_ID)
        );

        assertSame(cause, exception.getCause());
    }

    @Test
    @SuppressWarnings("rawtypes")
    void shouldDestroyOriginalPublicIdWithRequiredOptions() throws IOException {
        when(uploader.destroy(eq(PUBLIC_ID), anyMap()))
            .thenReturn(Map.of("result", "ok"));

        service.delete(PUBLIC_ID);

        ArgumentCaptor<Map> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader).destroy(eq(PUBLIC_ID), options.capture());

        assertEquals(
            Map.of(
                "resource_type", "image",
                "type", "upload",
                "invalidate", Boolean.TRUE
            ),
            options.getValue()
        );
    }
}
