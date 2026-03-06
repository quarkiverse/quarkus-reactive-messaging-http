package io.quarkus.reactivemessaging.http.source;

import static io.restassured.RestAssured.given;
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

import io.quarkus.reactivemessaging.http.source.app.QuotedChannelConsumer;
import io.quarkus.test.QuarkusUnitTest;

class QuotedChannelHttpSourceTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(QuotedChannelConsumer.class))
            .withConfigurationResource("http-source-quoted-channel-test-application.properties");

    @Inject
    QuotedChannelConsumer consumer;

    @AfterEach
    void cleanUp() {
        consumer.clear();
    }

    @Test
    void shouldConsumeUsingQuotedChannelNameContainingDots() {
        given()
                .body("some-text")
                .when()
                .post("/my-http-source")
                .then()
                .statusCode(202);

        await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> consumer.getMessages(), hasSize(1));
        assertThat(consumer.getMessages()).containsExactly("some-text");
    }
}
