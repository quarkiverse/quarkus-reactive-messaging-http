package io.quarkus.reactivemessaging.http.tracing;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.reactivemessaging.http.tracing.app.ChainedHttpProcessor;
import io.quarkus.reactivemessaging.http.tracing.app.TraceCapturingEndpoint;
import io.quarkus.reactivemessaging.http.tracing.app.TracingSourceConsumer;
import io.quarkus.test.QuarkusUnitTest;

class TracingSourceTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TracingSourceConsumer.class, ChainedHttpProcessor.class, TraceCapturingEndpoint.class))
            .withConfigurationResource("tracing-test-application.properties");

    @Inject
    TracingSourceConsumer consumer;

    @Inject
    TraceCapturingEndpoint traceCapturingEndpoint;

    @AfterEach
    void tearDown() {
        consumer.clear();
        traceCapturingEndpoint.clear();
    }

    @Test
    void shouldContinueTraceFromTraceparentHeader() {
        String traceId = randomTraceId();
        postWithTraceparent("/tracing-enabled-http-source", traceId);

        await().atMost(10, TimeUnit.SECONDS).until(() -> !consumer.getTracingEnabledTraceIds().isEmpty());
        assertThat(consumer.getTracingEnabledTraceIds()).containsExactly(traceId);
        // asserts on the message itself, independent of whatever Span.current() shows for this delivery path
        assertThat(consumer.getTracingEnabledMetadataPresent()).containsExactly(true);
    }

    @Test
    void shouldStillProduceALiveContextWithoutInboundTraceparent() {
        given()
                .body("some-text")
                .when()
                .post("/tracing-enabled-http-source")
                .then()
                .statusCode(202);

        await().atMost(10, TimeUnit.SECONDS).until(() -> !consumer.getTracingEnabledTraceIds().isEmpty());
        String observedTraceId = consumer.getTracingEnabledTraceIds().get(0);
        assertThat(observedTraceId).isNotEqualTo("00000000000000000000000000000000");
    }

    // asserts on the stamped TracingMetadata rather than Span.current(): a single immediately-delivered
    // request stays on its own Vert.x context regardless of this toggle, so Span.current() would pass either way
    @Test
    void shouldNotStampTracingMetadataWhenTracingDisabledForTheChannel() {
        String traceId = randomTraceId();
        postWithTraceparent("/tracing-disabled-http-source", traceId);

        await().atMost(10, TimeUnit.SECONDS).until(() -> !consumer.getTracingDisabledMetadataPresent().isEmpty());
        assertThat(consumer.getTracingDisabledMetadataPresent()).containsExactly(false);
    }

    @Test
    void shouldContinueTraceIntoChainedOutgoingSinkRequest() {
        String traceId = randomTraceId();
        postWithTraceparent("/chained-http-source", traceId);

        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> !traceCapturingEndpoint.getTraceparentHeaders().isEmpty());

        String forwardedTraceparent = traceCapturingEndpoint.getTraceparentHeaders().get(0);
        assertThat(forwardedTraceparent).isNotNull();
        assertThat(forwardedTraceparent.split("-")[1]).isEqualTo(traceId);
    }

    @Test
    void shouldContinueTraceAcrossAsyncExecutorHopWithoutConsumerReadingTracingMetadata() {
        String traceId = randomTraceId();
        postWithTraceparent("/async-hop-http-source", traceId);

        await().atMost(10, TimeUnit.SECONDS).until(() -> !consumer.getAsyncHopTraceIds().isEmpty());
        assertThat(consumer.getAsyncHopTraceIds()).containsExactly(traceId);
    }

    private static void postWithTraceparent(String path, String traceId) {
        String traceparent = "00-" + traceId + "-" + randomSpanId() + "-01";
        given()
                .header("traceparent", traceparent)
                .body("some-text")
                .when()
                .post(path)
                .then()
                .statusCode(202);
    }

    private static String randomTraceId() {
        return randomHex(32);
    }

    private static String randomSpanId() {
        return randomHex(16);
    }

    private static String randomHex(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(Integer.toHexString(RANDOM.nextInt(16)));
        }
        return builder.toString();
    }
}
