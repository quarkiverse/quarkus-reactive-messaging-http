package io.quarkus.reactivemessaging.http;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.delete;
import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.VerificationException;
import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;

@QuarkusTest
@QuarkusTestResource(CEResourceMockService.class)
public class ReactiveMessagingHttpTest {

    WireMockServer cloudEventService;

    @Test
    void testCloudEvent() {
        given().body("aBody").header("ce-type", "aType").post("/celistener");
        await()
                .atMost(2, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(250)).until(() -> {
                    try {
                        cloudEventService.verify(postRequestedFor(urlEqualTo("/"))
                                .withHeader("ce-" + CloudEventMetadata.CE_ATTRIBUTE_TYPE, WireMock.equalTo("aType")));
                        return true;
                    } catch (VerificationException ex) {
                        return false;
                    }
                });
    }

    @Test
    public void shouldSendAndConsumeWebSocketAndUseCustomSerializer() {
        //@formatter:off
        given()
                .body("test-message")
        .when()
                .post("/websocket-helper")
        .then()
                .statusCode(204);
        //@formatter:on

        await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> get("/websocket-helper").getBody().asString(), Predicate.isEqual("TEST-MESSAGE"));
    }

    @Test
    public void shouldSendAndConsumeHttpAndUseCustomSerializer() throws Exception {
        //@formatter:off
        given()
                .body("test-message")
                .when()
        .post("/http-helper")
                .then()
                .statusCode(204);
        //@formatter:on

        await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> get("/http-helper").getBody().asString(), Predicate.isEqual("TEST-MESSAGE"));
    }

    @AfterEach
    public void cleanUp() {
        delete("/http-helper").then().statusCode(204);
        delete("/websocket-helper").then().statusCode(204);
    }
}
