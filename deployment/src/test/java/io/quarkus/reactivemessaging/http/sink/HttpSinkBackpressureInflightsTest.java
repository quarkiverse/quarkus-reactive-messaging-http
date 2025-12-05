package io.quarkus.reactivemessaging.http.sink;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.inject.Inject;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.reactivemessaging.http.sink.app.Dto;
import io.quarkus.reactivemessaging.http.sink.app.HttpEmitterWithOverflow;
import io.quarkus.reactivemessaging.http.sink.app.HttpEndpoint;
import io.quarkus.reactivemessaging.utils.ToUpperCaseSerializer;
import io.quarkus.test.QuarkusUnitTest;

class HttpSinkBackpressureInflightsTest {

    private static final int MAX_INFLIGHT_MESSAGES = 1000; // same as in test properties

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ToUpperCaseSerializer.class, Dto.class, HttpEmitterWithOverflow.class,
                            HttpEndpoint.class))
            .withConfigurationResource("http-sink-backpressure-inflights-test-application.properties");

    @Inject
    HttpEmitterWithOverflow emitter;
    @Inject
    HttpEndpoint endpoint;

    @Test
    void shouldHandleUpToMaxInflights() throws InterruptedException {
        endpoint.pause();

        List<CompletionStage<Void>> emissions = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();

        for (int i = 0; i < MAX_INFLIGHT_MESSAGES + 1; i++) {
            try {
                emissions.add(emitter.emitAndThrowOnOverflow(i));
            } catch (RuntimeException failure) {
                failures.add(new Failure(i, failure));
            }
        }
        // Emitter is set to fail on overflow. Inflights are being processed and one should fail.
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> failures, Matchers.hasSize(1));

        endpoint.release();
        try {
            for (CompletionStage<Void> emission : emissions) {
                emission.toCompletableFuture().get(1, TimeUnit.SECONDS);
            }
        } catch (ExecutionException | TimeoutException e) {
            fail("failed waiting for success for the inflights emissions", e);
        }
    }

    @AfterEach
    void cleanUp() {
        endpoint.reset();
    }

    private static class Failure {

        int attempt;
        Throwable failure;

        Failure(int attempt, Throwable failure) {
            this.attempt = attempt;
            this.failure = failure;
        }
    }

}
