package io.quarkiverse.reactive.messaging.http.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ReactiveMessagingHttpResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/reactive-messaging-http")
                .then()
                .statusCode(200)
                .body(is("Hello reactive-messaging-http"));
    }
}
