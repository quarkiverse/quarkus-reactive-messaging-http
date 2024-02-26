package io.quarkus.reactivemessaging.http;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.delete;
import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.CloudEventMetadata;

@QuarkusTest
@QuarkusTestResource(CEResourceMockService.class)
public class ReactiveMessagingHttpTest {

    WireMockServer cloudEventService;

    @Test
    void testCloudEvent() {
        given().body("aBody").header("ce-type", "aType").header("ce-extension", "aExtension").post("/celistener");
        await().timeout(Duration.ofSeconds(2)).untilAsserted(() -> cloudEventService.verify(postRequestedFor(urlEqualTo("/"))
                .withHeader("ce-" + CloudEventMetadata.CE_ATTRIBUTE_TYPE, equalTo("aType"))
                .withHeader("ce-extension", equalTo("aExtension"))));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "2024-02-22T16:48:00Z|2024-02-22T16:48:00Z",
            "2024-02-22T16:48:12Z|2024-02-22T16:48:12Z",
            "2024-02-22T16:48:00.000Z|2024-02-22T16:48:00Z",
            "2024-02-22T16:48:12.000Z|2024-02-22T16:48:12Z",
            "2024-02-22T16:48:12.653Z|2024-02-22T16:48:12.653Z",
            "2024-02-22T16:48:12.653+08:00|2024-02-22T16:48:12.653+08:00"
    }, delimiter = '|')
    void testCloudEventTime(String time, String expected) {
        given().body("aBody").header("ce-type", "aType")
                .header("ce-time", time).post("/celistener");
        await().timeout(Duration.ofSeconds(2)).untilAsserted(() -> cloudEventService.verify(postRequestedFor(urlEqualTo("/"))
                .withHeader("ce-" + CloudEventMetadata.CE_ATTRIBUTE_TYPE, equalTo("aType"))
                .withHeader("ce-" + CloudEventMetadata.CE_ATTRIBUTE_TIME, equalTo(expected))));
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

    @Test
    void testGetRootShouldReturnIndexHtml() {
        given()
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .body("html.body", Matchers.equalTo("Hello"));
    }

    @AfterEach
    public void cleanUp() {
        delete("/http-helper").then().statusCode(204);
        delete("/websocket-helper").then().statusCode(204);
    }
}
