package com.example.movierecommendation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityPolicyIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void guestCannotOpenAdminRoutes() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotOpenAdminRoutes() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isForbidden());
    }

    @Test
    void guestCannotOpenPersonalRecommendations() throws Exception {
        mockMvc.perform(get("/user/recommendations")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "USER")
    void aiToolPostRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/ai-chat/recommend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"add The Matrix to my watchlist\"}"))
            .andExpect(status().isForbidden());
    }
}
