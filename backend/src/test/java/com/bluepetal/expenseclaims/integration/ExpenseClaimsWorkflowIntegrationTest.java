package com.bluepetal.expenseclaims.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exercises the real HTTP stack - controllers, Spring Security's JWT filter,
 * JPA against an in-memory H2 database, seeded with the same demo data the
 * app ships with. This is the test that proves all the pieces are actually
 * wired together correctly, not just individually correct.
 */
@SpringBootTest
@ActiveProfiles("test")
class ExpenseClaimsWorkflowIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    private MockMvc mvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        }
        return mockMvc;
    }

    private String loginAndGetToken(String email) throws Exception {
        String body = """
                {"email": "%s", "password": "password123"}
                """.formatted(email);
        String response = mvc().perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("token").asText();
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        String body = """
                {"email": "kavya.sundaram@bluepetal.in", "password": "not-the-right-password"}
                """;
        mvc().perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mvc().perform(get("/api/claims")).andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotReachTheFinancePayoutQueue() throws Exception {
        String kavyaToken = loginAndGetToken("kavya.sundaram@bluepetal.in");
        mvc().perform(get("/api/finance/queue").header("Authorization", "Bearer " + kavyaToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void aManagerCannotApproveHerOwnClaimOverRealHttp() throws Exception {
        // Ananya is seeded as a manager reporting to Deepak. Her own claim
        // (seeded, submitted) must not be approvable by her, even though
        // she's a manager and would normally have approve rights.
        String ananyaToken = loginAndGetToken("ananya.krishnan@bluepetal.in");

        String queueResponse = mvc().perform(get("/api/claims").header("Authorization", "Bearer " + ananyaToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode dashboard = mapper.readTree(queueResponse);
        Long ownClaimId = null;
        for (JsonNode c : dashboard.get("inFlight")) {
            ownClaimId = c.get("id").asLong();
        }
        assertThat(ownClaimId).as("Ananya should have at least one seeded claim of her own").isNotNull();

        mvc().perform(post("/api/manager/claims/" + ownClaimId + "/approve")
                        .header("Authorization", "Bearer " + ananyaToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeSeesAndCanApproveAManagerWithNoOneAboveThemsClaim() throws Exception {
        // Deepak is a manager with nobody above him, so his own claim
        // escalates to finance. This is what broke before: his approver_id
        // was left null in the seed data, so his claim silently never
        // appeared in anyone's queue, including finance's.
        String rameshToken = loginAndGetToken("ramesh.iyer@bluepetal.in");

        String queueResponse = mvc().perform(get("/api/manager/queue")
                        .header("Authorization", "Bearer " + rameshToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode pending = mapper.readTree(queueResponse).get("pending");
        Long deepaksClaimId = null;
        for (JsonNode c : pending) {
            if (c.get("ownerName").asText().equals("Deepak Verma")) {
                deepaksClaimId = c.get("id").asLong();
            }
        }
        assertThat(deepaksClaimId)
                .as("Deepak's seeded claim should be in finance's approval queue, not stuck nowhere")
                .isNotNull();

        mvc().perform(post("/api/manager/claims/" + deepaksClaimId + "/approve")
                        .header("Authorization", "Bearer " + rameshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void fullHappyPathFromDraftToPaid() throws Exception {
        String kavyaToken = loginAndGetToken("kavya.sundaram@bluepetal.in");
        String ananyaToken = loginAndGetToken("ananya.krishnan@bluepetal.in");
        String rameshToken = loginAndGetToken("ramesh.iyer@bluepetal.in");

        // 1. Kavya pastes a receipt and gets a draft back
        String draftResponse = mvc().perform(multipart("/api/claims/draft")
                        .param("rawText", "Rapido bike taxi\nFare Rs 220\n07-09-2026 08:40AM")
                        .header("Authorization", "Bearer " + kavyaToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long claimId = mapper.readTree(draftResponse).get("id").asLong();
        assertThat(mapper.readTree(draftResponse).get("amount").asDouble()).isEqualTo(220.0);

        // 2. She sends it to her manager (no duplicate for a fresh amount/date)
        String submitResponse = mvc().perform(post("/api/claims/" + claimId + "/submit")
                        .header("Authorization", "Bearer " + kavyaToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(submitResponse).get("claim").get("status").asText()).isEqualTo("SUBMITTED");

        // 3. Ananya (her manager) approves it
        mvc().perform(post("/api/manager/claims/" + claimId + "/approve")
                        .header("Authorization", "Bearer " + ananyaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 4. Ramesh (finance) pays it
        mvc().perform(post("/api/finance/claims/" + claimId + "/pay")
                        .header("Authorization", "Bearer " + rameshToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        // 5. A paid claim is frozen - even finance can't pay it again
        mvc().perform(post("/api/finance/claims/" + claimId + "/pay")
                        .header("Authorization", "Bearer " + rameshToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }
}
