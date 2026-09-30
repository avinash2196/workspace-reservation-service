package com.example.workspace.reservation.controller;

import com.example.workspace.reservation.event.ReservationEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RED tests for reservation-events RE-M5: the creation path publishes ReservationCreated exactly once
 * per successful create, never for a rejected create, and a failed publish changes nothing about the
 * creation outcome. The broker boundary is doubled in process with Kafka's MockProducer (RE-R17).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("ReservationController - ReservationCreated publishing on the creation path")
class ReservationCreationEventPublishingTest {

    private static final String BASE_URL = "/api/v1/reservations";

    @TestConfiguration
    static class BrokerDoubleConfiguration {

        @Bean
        @Primary
        MockProducer<String, String> brokerDouble() {
            return new MockProducer<>(true, null, new StringSerializer(), new StringSerializer());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockProducer<String, String> brokerDouble;

    @Autowired
    private ReservationEventPublisher publisher;

    private String workspaceId;

    @BeforeEach
    void setUp() {
        brokerDouble.clear();
        brokerDouble.sendException = null;
        workspaceId = "ws-" + UUID.randomUUID();
    }

    private static String requestBody(String workspaceId, String userId, String startTime, String endTime) {
        return String.format("""
            {
                "workspaceId": "%s",
                "userId": "%s",
                "startTime": "%s",
                "endTime": "%s"
            }
            """, workspaceId, userId, startTime, endTime);
    }

    private MvcResult create(String body, int expectedStatus) throws Exception {
        return mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createAndAssertOnePublished() throws Exception {
        JsonNode created = json(create(
                requestBody(workspaceId, "user-1", "2026-10-02T09:00:00Z", "2026-10-02T10:00:00Z"), 201));
        String id = created.get("id").asText();
        assertThat(brokerDouble.history())
                .extracting(ProducerRecord::key)
                .containsExactly(id);
        return id;
    }

    @Test
    @DisplayName("successful create publishes exactly one event for the created reservation")
    void successfulCreatePublishesOneEventForTheCreatedReservation() throws Exception {
        JsonNode created = json(create(
                requestBody(workspaceId, "user-1", "2026-10-02T09:00:00Z", "2026-10-02T10:00:00Z"), 201));

        assertThat(brokerDouble.history())
                .extracting(ProducerRecord::key)
                .containsExactly(created.get("id").asText());
    }

    @Test
    @DisplayName("create rejected by request validation (400) publishes nothing")
    void createRejectedByRequestValidationPublishesNothing() throws Exception {
        String publishedId = createAndAssertOnePublished();

        create(requestBody("", "user-2", "2026-10-02T11:00:00Z", "2026-10-02T12:00:00Z"), 400);

        assertThat(brokerDouble.history())
                .extracting(ProducerRecord::key)
                .containsExactly(publishedId);
    }

    @Test
    @DisplayName("create rejected by time-range validation (400) publishes nothing")
    void createRejectedByTimeRangeValidationPublishesNothing() throws Exception {
        String publishedId = createAndAssertOnePublished();

        create(requestBody(workspaceId, "user-2", "2026-10-02T12:00:00Z", "2026-10-02T12:00:00Z"), 400);

        assertThat(brokerDouble.history())
                .extracting(ProducerRecord::key)
                .containsExactly(publishedId);
    }

    @Test
    @DisplayName("create rejected by conflict detection (409) publishes nothing")
    void createRejectedByConflictPublishesNothing() throws Exception {
        String publishedId = createAndAssertOnePublished();

        create(requestBody(workspaceId, "user-2", "2026-10-02T09:30:00Z", "2026-10-02T10:30:00Z"), 409);

        assertThat(brokerDouble.history())
                .extracting(ProducerRecord::key)
                .containsExactly(publishedId);
    }

    @Test
    @DisplayName("publish failure changes neither the 201 response nor the stored reservation")
    void publishFailureDoesNotChangeCreationResponseOrStoredReservation() throws Exception {
        brokerDouble.sendException =
                new TimeoutException("Topic reservation-events not present in metadata after 1000 ms.");
        long failuresBefore = publisher.failedPublishCount();

        JsonNode created = json(create(
                requestBody(workspaceId, "user-1", "2026-10-02T09:00:00Z", "2026-10-02T10:00:00Z"), 201));

        assertThat(publisher.failedPublishCount()).isEqualTo(failuresBefore + 1);
        List<String> fieldNames = new ArrayList<>();
        created.fieldNames().forEachRemaining(fieldNames::add);
        assertThat(fieldNames).containsExactlyInAnyOrder(
                "id", "workspaceId", "userId", "startTime", "endTime", "status");
        String id = created.get("id").asText();
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        assertThat(created.get("workspaceId").asText()).isEqualTo(workspaceId);
        assertThat(created.get("userId").asText()).isEqualTo("user-1");
        assertThat(created.get("startTime").asText()).isEqualTo("2026-10-02T09:00:00Z");
        assertThat(created.get("endTime").asText()).isEqualTo("2026-10-02T10:00:00Z");
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");

        JsonNode retrieved = json(mockMvc.perform(get(BASE_URL + "/" + id))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(retrieved).isEqualTo(created);

        create(requestBody(workspaceId, "user-2", "2026-10-02T09:30:00Z", "2026-10-02T10:30:00Z"), 409);
    }
}
