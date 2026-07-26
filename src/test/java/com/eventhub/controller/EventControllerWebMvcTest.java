package com.eventhub.controller;

import com.eventhub.controller.dto.*;
import com.eventhub.domain.EventStatus;
import com.eventhub.exception.ResourceNotFoundException;
import com.eventhub.service.EventService;
import com.eventhub.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
class EventControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventService eventService;

    @MockBean
    private JwtService jwtService; // Yêu cầu mock do SecurityConfig cần quét JwtAuthenticationFilter

    @Test
    @WithMockUser(username = "admin@email.com", roles = {"EVENT_ADMIN"})
    void create_Success() throws Exception {
        // Given
        CreateEventRequest request = new CreateEventRequest(
                "My Event", "Event description", "HCM City",
                LocalDateTime.now().plusDays(2), 50
        );

        EventResponse response = new EventResponse(
                1L, "My Event", "Event description", "HCM City",
                request.startAt(), 50, 50, EventStatus.DRAFT, Instant.now()
        );

        when(eventService.create(any(CreateEventRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/events")
                        .with(csrf()) // Cần csrf() do Spring Security mặc định enable csrf trong test
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/events/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("My Event"));

        verify(eventService).create(any(CreateEventRequest.class));
    }

    @Test
    @WithMockUser(username = "admin@email.com", roles = {"EVENT_ADMIN"})
    void create_BadRequest_WhenFieldsAreInvalid() throws Exception {
        // Given
        CreateEventRequest request = new CreateEventRequest(
                "", "", "", // Blank fields
                LocalDateTime.now().minusDays(1), // Past date
                0 // Invalid capacity
        );

        // When & Then
        mockMvc.perform(post("/api/events")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations").isArray())
                .andExpect(jsonPath("$.violations.length()").value(5)); // 5 validation errors

        verify(eventService, never()).create(any());
    }

    @Test
    @WithMockUser(username = "user@email.com", roles = {"PARTICIPANT"})
    void findById_NotFound() throws Exception {
        // Given
        Long eventId = 999L;
        when(eventService.findById(eventId)).thenThrow(new ResourceNotFoundException("Event not found with id: " + eventId));

        // When & Then
        mockMvc.perform(get("/api/events/{eventId}", eventId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Event not found with id: " + eventId));
    }

    @Test
    @WithMockUser(username = "user@email.com", roles = {"PARTICIPANT"})
    void search_BadRequest_WhenPageIsNegative() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/events")
                        .param("page", "-1")
                        .param("size", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Page number must be zero or positive"));

        verify(eventService, never()).search(any(), any(), any(), any());
    }
}
