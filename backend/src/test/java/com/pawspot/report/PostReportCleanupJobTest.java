package com.pawspot.report;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostReportCleanupJobTest {

    @Test
    void shouldCalculateCutoffUsingTheConfiguredRetention() {
        PostReportService service = mock(PostReportService.class);

        // Use a non-default value to verify that retention is configurable.
        PostReportCleanupJob job = new PostReportCleanupJob(service, 7);

        Instant earliestCutoff = Instant.now().minus(Duration.ofDays(7));

        job.cleanup();

        Instant latestCutoff = Instant.now().minus(Duration.ofDays(7));

        ArgumentCaptor<Instant> cutoff =
            ArgumentCaptor.forClass(Instant.class);

        verify(service).cleanupResolvedReports(cutoff.capture());

        Instant actualCutoff = cutoff.getValue();

        assertFalse(actualCutoff.isBefore(earliestCutoff));
        assertFalse(actualCutoff.isAfter(latestCutoff));
    }

    @Test
    void shouldRejectNonPositiveRetention() {
        PostReportService service = mock(PostReportService.class);

        assertThrows(
            IllegalArgumentException.class,
            () -> new PostReportCleanupJob(service, 0)
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new PostReportCleanupJob(service, -1)
        );
    }
}