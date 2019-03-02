package it.fvaleri.example;

import io.quarkus.test.junit.DisabledOnNativeImage;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.StringContains.containsString;

@QuarkusTest
class KafkaClientIT {
    @Test
    @DisabledOnNativeImage
    void testBareClients() {
        given()
                .formParam("key", "my-key")
                .formParam("value", "my-value")
                .when()
                    .post("/api/kafka")
                .then()
                    .statusCode(200);

        await()
                .atMost(Duration.ofMinutes(1))
                .untilAsserted(() ->
                        get("/api/kafka")
                                .then()
                                .statusCode(200)
                                .body(containsString("my-key-my-value"))
                );

        get("/api/kafka/topics")
                .then()
                .statusCode(200)
                .body(containsString("my-topic"));
    }
}
