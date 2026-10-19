package com.pawspot.support;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class PostgresIntegrationTest {

    private static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer("postgres:17")
            .withDatabaseName("pawspot_integration_test");

    static {
        POSTGRES.start();
    }

    @Autowired
    private JdbcTemplate integrationTestJdbcTemplate;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void resetDatabase() {
        String databaseName = integrationTestJdbcTemplate.queryForObject(
            "SELECT current_database()",
            String.class
        );

        assertEquals(
            "pawspot_integration_test",
            databaseName,
            "Refusing to truncate a non-test database"
        );

        integrationTestJdbcTemplate.execute("""
            TRUNCATE TABLE
                refresh_tokens,
                refresh_sessions,
                pet_posts,
                users
            RESTART IDENTITY CASCADE
            """);
    }

    protected void awaitBlockedDatabaseTransactions(int expectedCount) {
        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertEquals(
                expectedCount,
                integrationTestJdbcTemplate.queryForObject("""
                    SELECT count(*)
                    FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND cardinality(pg_blocking_pids(pid)) > 0
                    """, Integer.class),
                "Expected worker transactions to be waiting on the test lock"
            ));
    }
}
