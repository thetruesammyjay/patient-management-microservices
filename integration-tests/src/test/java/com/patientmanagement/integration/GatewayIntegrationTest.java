package com.patientmanagement.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GatewayIntegrationTest {

  @BeforeAll
  static void configureBaseUrl() {
    RestAssured.baseURI = System.getProperty(
        "test.baseUrl",
        System.getenv().getOrDefault("TEST_BASE_URL", "http://localhost:4004"));
  }

  @Test
  void validLoginReturnsToken() {
    given()
        .contentType("application/json")
        .body("""
            {"email":"testuser@test.com","password":"password123"}
            """)
        .when()
        .post("/auth/login")
        .then()
        .statusCode(200)
        .body("token", notNullValue());
  }

  @Test
  void invalidLoginIsUnauthorized() {
    given()
        .contentType("application/json")
        .body("""
            {"email":"unknown@example.com","password":"wrongpassword"}
            """)
        .when()
        .post("/auth/login")
        .then()
        .statusCode(401);
  }

  @Test
  void patientRouteRequiresBearerToken() {
    given()
        .when()
        .get("/api/patients")
        .then()
        .statusCode(401);
  }

  @Test
  void authenticatedPatientListIsAJsonArray() {
    String token = login();

    given()
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/api/patients")
        .then()
        .statusCode(200)
        .body("size()", greaterThanOrEqualTo(0));
  }

  private String login() {
    Response response = given()
        .contentType("application/json")
        .body("""
            {"email":"testuser@test.com","password":"password123"}
            """)
        .when()
        .post("/auth/login")
        .then()
        .statusCode(200)
        .extract()
        .response();
    return response.jsonPath().getString("token");
  }
}
