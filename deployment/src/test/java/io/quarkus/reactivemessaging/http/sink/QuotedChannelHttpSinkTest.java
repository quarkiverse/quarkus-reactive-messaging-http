package io.quarkus.reactivemessaging.http.sink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;

import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.reactivemessaging.http.sink.app.HttpEndpoint;
import io.quarkus.reactivemessaging.http.sink.app.QuotedChannelHttpEmitter;
import io.quarkus.reactivemessaging.utils.ToUpperCaseSerializer;
import io.quarkus.test.QuarkusUnitTest;

class QuotedChannelHttpSinkTest {

    @Inject
    HttpEndpoint httpEndpoint;

    @Inject
    QuotedChannelHttpEmitter emitter;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(QuotedChannelHttpEmitter.class, HttpEndpoint.class, ToUpperCaseSerializer.class))
            .withConfigurationResource("http-sink-quoted-channel-test-application.properties");

    @AfterEach
    void cleanUp() {
        httpEndpoint.getRequests().clear();
    }

    @Test
    void shouldUseCustomSerializerFromQuotedOutgoingChannel() {
        emitter.emit("some-text");

        await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> httpEndpoint.getRequests(), hasSize(1));

        assertThat(httpEndpoint.getRequests().get(0).getBody()).isEqualTo("SOME-TEXT");
    }
}
