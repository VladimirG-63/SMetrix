package ru.smetrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void protectedApiRejectsAnonymousRequests() throws Exception {
        mvc.perform(get("/api/v1/materials").param("q", "цемент").param("region", "77"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationTokenAuthorizesProtectedApi() throws Exception {
        String email = "integration-" + System.nanoTime() + "@example.test";
        String response = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"StrongPass123\",\"name\":\"Test\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(response);
        String accessToken = json.get("access_token").asText();
        mvc.perform(get("/api/v1/user/profile")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }
}
