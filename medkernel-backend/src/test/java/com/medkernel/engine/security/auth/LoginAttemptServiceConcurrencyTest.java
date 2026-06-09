package com.medkernel.engine.security.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LoginAttemptServiceConcurrencyTest {

    private static final String TENANT_ID = "t-1";
    private static final String USERNAME = "parallel-unknown-user";
    private static final int CONCURRENCY = 8;

    @Autowired
    LoginAttemptService loginAttempts;

    @Autowired
    LoginAttemptStateRepository attemptStates;

    @AfterEach
    void cleanUp() {
        attemptStates.findByTenantIdAndUsername(TENANT_ID, USERNAME)
            .ifPresent(attemptStates::delete);
    }

    @Test
    void concurrentFailuresForSameUnknownUsernameAreSerializedWithoutLosingCount() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch ready = new CountDownLatch(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<LoginAttemptService.FailureOutcome>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENCY; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return loginAttempts.recordFailure(TENANT_ID, USERNAME, null);
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            for (Future<LoginAttemptService.FailureOutcome> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS))
                    .isEqualTo(LoginAttemptService.FailureOutcome.FAILED);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        LoginAttemptState state = attemptStates
            .findByTenantIdAndUsername(TENANT_ID, USERNAME)
            .orElseThrow();
        assertThat(state.failedCount()).isEqualTo(CONCURRENCY);
    }
}
