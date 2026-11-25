package com.pawspot.report;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "app.reports.cleanup",
    name = "enabled",
    havingValue = "true"
)
public class PostReportCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(PostReportCleanupJob.class);
    private final PostReportService postReportService;
    private final Duration retention;

    public PostReportCleanupJob(
        PostReportService postReportService,
        @Value("${app.reports.cleanup.retention-days:30}") int retentionDays
    ) {
        if (retentionDays <= 0) {
            throw new IllegalArgumentException(
                "Report retention days must be positive"
            );
        }

        this.postReportService = postReportService;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(
        cron = "${app.reports.cleanup.cron:0 0 3 * * *}", // Clean up at 3:00 UTC
        zone = "UTC"
    )
    public void cleanup() {
        Instant cutoff = Instant.now().minus(retention);
        int deleted = postReportService.cleanupResolvedReports(cutoff);
        // Record the outcome without logging report contents.
        log.info("Deleted {} expired resolved post reports", deleted);
    }
}
