package io.quarkus.reactivemessaging.http.sink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.reactivemessaging.http.sink.app.Dto;
import io.quarkus.reactivemessaging.http.sink.app.HttpProcessingEmitter;
import io.quarkus.reactivemessaging.http.sink.app.HttpProcessingEndpoint;
import io.quarkus.test.QuarkusUnitTest;

public class HttpSinkProcessingTest {

    private final static int MESSAGE_COUNT = 1000;

    @Inject
    HttpProcessingEmitter emitter;
    @Inject
    HttpProcessingEndpoint httpProcessingEndpoint;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(HttpProcessingEndpoint.class, HttpProcessingEmitter.class, Dto.class))
            .withConfigurationResource("http-sink-processing-test-application.properties");

    //  @Disabled
    @Test
    public void testSendingAndClosing() {
        AtomicInteger ackCounter = new AtomicInteger();
        AtomicInteger nackCounter = new AtomicInteger();

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            Message message = Message.of(String.valueOf(i))
                    .withAck(() -> {
                        ackCounter.incrementAndGet();
                        return new CompletableFuture<>();
                    })
                    .withNack(error -> {
                        nackCounter.incrementAndGet();
                        return new CompletableFuture<>();
                    });

            emitter.emitObject(message);
        }

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertThat(httpProcessingEndpoint.getSize()).isEqualTo(MESSAGE_COUNT));

        assertThat(ackCounter.get()).isEqualTo(0);
        assertThat(nackCounter.get()).isEqualTo(0);

        httpProcessingEndpoint.closeStreams();

        await().pollDelay(3, TimeUnit.SECONDS).untilAsserted(
                () -> assertThat(ackCounter.get()).isEqualTo(0));
        assertThat(nackCounter.get()).isEqualTo(0);
    }

    //  @Disabled
    @Test
    public void testSendingWithLateAck() {
        AtomicInteger ackCounter = new AtomicInteger();
        AtomicInteger nackCounter = new AtomicInteger();

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            Message message = Message.of(String.valueOf(i))
                    .withAck(() -> {
                        ackCounter.incrementAndGet();
                        return new CompletableFuture<>();
                    })
                    .withNack(error -> {
                        nackCounter.incrementAndGet();
                        return new CompletableFuture<>();
                    });

            emitter.emitObject(message);
        }

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertThat(httpProcessingEndpoint.getSize()).isEqualTo(MESSAGE_COUNT));

        assertThat(ackCounter.get()).isEqualTo(0);
        assertThat(nackCounter.get()).isEqualTo(0);

        httpProcessingEndpoint.ackAll();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertThat(ackCounter.get()).isEqualTo(MESSAGE_COUNT));
        assertThat(nackCounter.get()).isEqualTo(0);
    }

    //  @Disabled
    @Test
    public void testSendingWithLateNack() {
        AtomicInteger ackCounter = new AtomicInteger();
        AtomicInteger nackCounter = new AtomicInteger();

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            Message message = Message.of(String.valueOf(i))
                    .withAck(() -> {
                        ackCounter.incrementAndGet();
                        return new CompletableFuture<>();
                    })
                    .withNack(error -> {
                        nackCounter.incrementAndGet();
                        return new CompletableFuture<>();
                    });

            emitter.emitObject(message);
        }

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertThat(httpProcessingEndpoint.getSize()).isEqualTo(MESSAGE_COUNT));

        assertThat(ackCounter.get()).isEqualTo(0);
        assertThat(nackCounter.get()).isEqualTo(0);

        httpProcessingEndpoint.nackAll();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertThat(nackCounter.get()).isEqualTo(MESSAGE_COUNT));
        assertThat(ackCounter.get()).isEqualTo(0);
    }

    @Test
    public void testSendingSingleMessageWhenNoStatus() {
        httpProcessingEndpoint.pause();

        AtomicInteger ackCounter = new AtomicInteger();
        AtomicInteger nackCounter = new AtomicInteger();

        for (int i = 0; i < MESSAGE_COUNT; i++) {
            Message message = Message.of(String.valueOf(i))
                    .withAck(() -> {
                        ackCounter.incrementAndGet();
                        return new CompletableFuture<>();
                    })
                    .withNack(error -> {
                        nackCounter.incrementAndGet();
                        return new CompletableFuture<>();
                    });

            emitter.emitObject(message);
        }

        try {
            await().pollDelay(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> assertThat(httpProcessingEndpoint.getSize()).isEqualTo(1));
        } finally {
            httpProcessingEndpoint.resume();
        }

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertThat(httpProcessingEndpoint.getSize()).isEqualTo(MESSAGE_COUNT));
    }

    @AfterEach
    public void cleanUp() {
        httpProcessingEndpoint.closeStreams();
        httpProcessingEndpoint.clearList();
    }

}
