package com.planwise.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planwise.TestcontainersConfiguration;
import com.planwise.user.UserRepository;

/**
 * Full app (security, controllers, services, real PostgreSQL) driven through HTTP requests.
 * Each test uses its own random email, so tests don't depend on each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository users;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions register(String email, String password) throws Exception {
        return postJson("/api/auth/register",
                "{\"email\": \"%s\", \"password\": \"%s\", \"monthlyIncome\": 4200.00}".formatted(email, password));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password));
    }

    private String tokenFrom(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void registerReturnsTokenAndStoresOnlyAPasswordHash() throws Exception {
        String email = uniqueEmail();

        register(email, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600));

        String storedHash = users.findByEmail(email).orElseThrow().getPasswordHash();
        assertThat(storedHash).isNotEqualTo(PASSWORD).startsWith("$2"); // BCrypt hashes start with $2
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        register(email.toUpperCase(), PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with this email already exists"));
    }

    @Test
    void registerValidatesInput() throws Exception {
        register("not-an-email", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Email must be a valid email address"));
        register(uniqueEmail(), "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Password must be 8 to 72 characters"));
    }

    @Test
    void loginWithCorrectPasswordReturnsToken() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);

        login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginFailuresAllLookTheSame() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);

        login(email, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void protectedEndpointRequiresValidToken() throws Exception {
        String email = uniqueEmail();
        String token = tokenFrom(register(email, PASSWORD));

        // No token
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        // Token with a changed character: the signature no longer matches
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
        // Real token
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.monthlyIncome").value(4200.00))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
