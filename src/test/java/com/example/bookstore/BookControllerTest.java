package com.example.bookstore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class BookControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    private static String body(String isbn, String title) {
        return """
                {"isbn":"%s","title":"%s","author":"Author","price":9.99}
                """.formatted(isbn, title);
    }

    @Test
    void listReturnsJsonArray() {
        webTestClient.get().uri("/books")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody().jsonPath("$").isArray();
    }

    @Test
    void createReturns201WithResource() {
        webTestClient.post().uri("/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body("create-1", "Created"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.isbn").isEqualTo("create-1")
                .jsonPath("$.title").isEqualTo("Created");
    }

    @Test
    void duplicateIsbnReturns422ProblemJson() {
        webTestClient.post().uri("/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body("dup-1", "First"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri("/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body("dup-1", "Second"))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.isbn").isEqualTo("dup-1");
    }

    @Test
    void getReturnsBookOr404() {
        webTestClient.post().uri("/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body("get-1", "Fetchable"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.get().uri("/books/get-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.isbn").isEqualTo("get-1");

        webTestClient.get().uri("/books/missing-xyz")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void putUpsertsWith201Then200() {
        webTestClient.put().uri("/books/put-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body("put-1", "Created via PUT"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.title").isEqualTo("Created via PUT");

        webTestClient.put().uri("/books/put-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body("put-1", "Updated via PUT"))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.title").isEqualTo("Updated via PUT");
    }

    @Test
    void deleteReturns204ThenGetReturns404() {
        webTestClient.post().uri("/books")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body("del-1", "Doomed"))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.delete().uri("/books/del-1")
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get().uri("/books/del-1")
                .exchange()
                .expectStatus().isNotFound();
    }
}
