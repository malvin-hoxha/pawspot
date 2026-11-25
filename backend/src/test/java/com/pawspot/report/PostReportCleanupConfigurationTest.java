package com.pawspot.report;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.pawspot.config.SchedulingConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.scheduling.config.TaskManagementConfigUtils
    .SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME;

class PostReportCleanupConfigurationTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                SchedulingConfig.class,
                PostReportCleanupJob.class
            )
            .withBean(
                PostReportService.class,
                () -> mock(PostReportService.class)
            )
            .withPropertyValues(
                "app.scheduling.enabled=true",
                // Disable the trigger while testing bean configuration.
                "app.reports.cleanup.cron=-"
            );

    @Test
    void shouldNotCreateCleanupJobByDefault() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());

            assertEquals(
                0,
                context.getBeanNamesForType(PostReportCleanupJob.class).length
            );
        });
    }

    @Test
    void shouldCreateCleanupJobWhenEnabled() {
        contextRunner
            .withPropertyValues("app.reports.cleanup.enabled=true")
            .run(context -> {
                assertNull(context.getStartupFailure());

                assertEquals(
                    1,
                    context.getBeanNamesForType(PostReportCleanupJob.class).length
                );

                assertTrue(context.containsBean(
                    SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME
                ));

                verifyNoInteractions(context.getBean(PostReportService.class));
            });
    }

    @Test
    void shouldDisableSchedulingWhenTheGlobalSwitchIsOff() {
        contextRunner
            .withPropertyValues(
                "app.scheduling.enabled=false",
                "app.reports.cleanup.enabled=true"
            )
            .run(context -> {
                assertNull(context.getStartupFailure());

                // The job exists, but scheduled execution is not enabled.
                assertEquals(
                    1,
                    context.getBeanNamesForType(PostReportCleanupJob.class).length
                );

                assertFalse(context.containsBean(
                    SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME
                ));

                verifyNoInteractions(context.getBean(PostReportService.class));
            });
    }
}