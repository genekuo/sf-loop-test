package com.example.bookstore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class BookControllerTest {

    private MockMvc mockMvc;

    @Autowired
    void setUp(WebApplicationContext context) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static String body(String isbn, String title) {
        return """
                {"isbn":"%s","title":"%s","author":"Author","price":9.99}
                """.formatted(isbn, title);
    }

    @Test
    void listReturnsJsonArray() throws Exception {
        mockMvc.perform(get("/books"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createReturns201WithResource() throws Exception {
        mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("create-1", "Created")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isbn").value("create-1"))
                .andExpect(jsonPath("$.title").value("Created"));
    }

    @Test
    void duplicateIsbnReturns422ProblemJson() throws Exception {
        mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dup-1", "First")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dup-1", "Second")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.isbn").value("dup-1"));
    }

    @Test
    void getReturnsBookOr404() throws Exception {
        mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("get-1", "Fetchable")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/books/get-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value("get-1"));

        mockMvc.perform(get("/books/missing-xyz"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void putUpsertsWith201Then200() throws Exception {
        mockMvc.perform(put("/books/put-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("put-1", "Created via PUT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Created via PUT"));

        mockMvc.perform(put("/books/put-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("put-1", "Updated via PUT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated via PUT"));
    }

    @Test
    void deleteReturns204ThenGetReturns404() throws Exception {
        mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("del-1", "Doomed")))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/books/del-1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/books/del-1"))
                .andExpect(status().isNotFound());
    }
}
