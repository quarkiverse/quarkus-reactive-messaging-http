package io.quarkus.reactivemessaging.http.source;

import static org.assertj.core.util.Arrays.asList;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.reactivemessaging.http.source.app.HttpProcessingConsumer;
import io.quarkus.reactivemessaging.utils.VertxFriendlyLock;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.vertx.core.http.HttpMethod;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.http.HttpClient;

public class HttpProcessingSourceTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(HttpProcessingConsumer.class, VertxFriendlyLock.class))
            .withConfigurationResource("http-processing-source-test-application.properties");

    @Inject
    HttpProcessingConsumer consumer;

    @Inject
    Vertx vertx;

    @TestHTTPResource
    URI baseUri;

    @AfterEach
    void setUp() {
        consumer.clear();
    }

    @Test
    void shouldReturnNoStatusWhenLocked() {
        consumer.pause();
        List<Response> responses = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            responses.add(sendAndGetResponse("msg " + i, "/processing-endpoint"));
        }

        await().pollDelay(3, TimeUnit.SECONDS).until(
                () -> countCodes(responses, 202), Predicate.isEqual(0));

        consumer.resume();

        await().atMost(10, TimeUnit.SECONDS).until(
                () -> countCodes(responses, 202), Predicate.isEqual(10));
    }

    @Test
    void shouldReturnStatusWithoutBody() {
        List<Response> responses = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            responses.add(sendAndGetResponse("msg " + i, "/processing-endpoint"));
        }

        await().atMost(10, TimeUnit.SECONDS).until(
                () -> countCodes(responses, 202), Predicate.isEqual(10));
    }

    @Test
    void shouldReturnAckBody() {
        List<Response> responses = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            responses.add(sendAndGetResponse("msg " + i, "/processing-endpoint"));
        }

        await().atMost(10, TimeUnit.SECONDS).until(
                () -> countCodes(responses, 202), Predicate.isEqual(10));

        consumer.ackAll();

        await().atMost(10, TimeUnit.SECONDS).until(
                () -> countAcks(responses), Predicate.isEqual(10));
    }

    @Test
    void shouldReturnNackBody() {
        List<Response> responses = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            responses.add(sendAndGetResponse("msg " + i, "/processing-endpoint"));
        }

        await().atMost(10, TimeUnit.SECONDS).until(
                () -> countCodes(responses, 202), Predicate.isEqual(10));

        consumer.nackAll();

        await().atMost(10, TimeUnit.SECONDS).until(
                () -> countNacks(responses), Predicate.isEqual(10));
    }

    private int countCodes(List<Response> responses, int... codes) {
        List<Integer> statusCodes = new ArrayList<>();
        List<CompletableFuture<Integer>> sendStates = responses.stream().map(res -> res.status).toList();
        for (Future<Integer> sendState : sendStates) {
            if (sendState.isDone()) {
                try {
                    statusCodes.add(sendState.get());
                } catch (InterruptedException | ExecutionException e) {
                    fail("checking the status code for http connection failed unexpectedly", e);
                }
            }
        }
        return (int) statusCodes.stream().filter(asList(codes)::contains).count();
    }

    private int countAcks(List<Response> responses) {
        int count = 0;
        for (Response response : responses) {
            count += (int) response.chunks.stream()
                    .filter("ACK"::equals)
                    .count();
        }
        return count;
    }

    private int countNacks(List<Response> responses) {
        int count = 0;
        for (Response response : responses) {
            count += (int) response.chunks.stream()
                    .filter("NACK"::equals)
                    .count();
        }
        return count;
    }

    Response sendAndGetResponse(String body, String path) {
        HttpClient httpClient = vertx.createHttpClient();

        CompletableFuture<Integer> result = new CompletableFuture<>();
        List<String> chunks = new ArrayList<>();

        httpClient.request(HttpMethod.POST, baseUri.getPort(), baseUri.getHost(), path)
                .onItem().transformToUni(req -> req.send(body))
                .onItem().transformToMulti(resp -> {
                    result.complete(resp.statusCode());
                    return resp.toMulti();
                })
                .onItem().invoke(chunk -> {
                    chunks.add(chunk.toString());
                }).subscribe().with(buffer -> {
                });

        return new Response(result, chunks);
    }

    record Response(CompletableFuture<Integer> status, List<String> chunks) {
    }
}
