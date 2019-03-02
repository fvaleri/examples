package it.fvaleri.example;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class GreetingResourceIT {
    @Test
    public void testGreetEndpoint() {
        given()
          .when().get("/api/greet")
          .then()
             .statusCode(200)
             .body(is("{\"message\":\"hello\"}"));
    }

    @Test
    public void testGreetNameEndpoint() {
        String uuid = UUID.randomUUID().toString();
        given()
            .pathParam("name", uuid)
            .when().get("/api/greet/{name}")
            .then()
                .statusCode(200)
                .body(is("{\"message\":\"hello " + uuid + "\"}"));
    }
}
