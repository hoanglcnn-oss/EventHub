package com.eventhub;

import com.eventhub.controller.dto.*;
import com.eventhub.domain.EventStatus;
import com.eventhub.domain.RegistrationStatus;
import com.eventhub.repository.EventRepository;
import com.eventhub.repository.ParticipantRepository;
import com.eventhub.repository.RegistrationRepository;
import com.eventhub.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EventHubIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        registrationRepository.deleteAll();
        userAccountRepository.deleteAll();
        participantRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void completeWorkflow_E2E() throws Exception {
        // -------------------------------------------------------------
        // Bước 1: Đăng ký tài khoản Participant mới (Công khai)
        // -------------------------------------------------------------
        RegisterUserRequest registerReq = new RegisterUserRequest(
                "john@email.com", "Password123!", "John Doe"
        );

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        ParticipantResponse registeredParticipant = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                ParticipantResponse.class
        );
        assertNotNull(registeredParticipant.id());
        assertEquals("John Doe", registeredParticipant.fullName());

        // -------------------------------------------------------------
        // Bước 2: Đăng nhập Participant vừa đăng ký lấy Token JWT
        // -------------------------------------------------------------
        LoginRequest participantLogin = new LoginRequest("john@email.com", "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(participantLogin)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse participantToken = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                LoginResponse.class
        );
        assertNotNull(participantToken.token());
        assertEquals("Bearer", participantToken.tokenType());

        // -------------------------------------------------------------
        // Bước 3: Đăng nhập tài khoản Admin mặc định lấy Token JWT
        // (Tài khoản Admin đã được tạo sẵn qua AdminBootstrap)
        // -------------------------------------------------------------
        // Đăng nhập lại do database clean đã xóa Admin. Vì vậy ta giả lập đăng nhập Admin
        // để tạo sự kiện. Cơ chế bootstrap chạy CommandLineRunner lúc khởi động. Do ta xóa DB ở BeforeEach,
        // ta sẽ đăng ký tay 1 Admin cho test.
        RegisterUserRequest adminRegister = new RegisterUserRequest("admin@eventhub.com", "AdminPassword123!", "Administrator");
        // Ta tạo trực tiếp trong DB cho nhanh do API register chỉ tạo PARTICIPANT
        com.eventhub.domain.UserAccount adminAccount = com.eventhub.domain.UserAccount.builder()
                .email("admin@eventhub.com")
                .passwordHash(passwordEncoder.encode("AdminPassword123!"))
                .role(com.eventhub.domain.UserRole.EVENT_ADMIN)
                .enabled(true)
                .createdAt(java.time.Instant.now())
                .build();
        userAccountRepository.save(adminAccount);

        LoginRequest adminLogin = new LoginRequest("admin@eventhub.com", "AdminPassword123!");
        MvcResult adminLoginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse adminToken = objectMapper.readValue(
                adminLoginResult.getResponse().getContentAsString(),
                LoginResponse.class
        );

        // -------------------------------------------------------------
        // Bước 4: Admin tạo một Event mới ở trạng thái DRAFT
        // -------------------------------------------------------------
        CreateEventRequest createEventReq = new CreateEventRequest(
                "Java Concurrency", "Deep dive into JVM threads", "R102",
                LocalDateTime.now().plusDays(2), 20
        );

        MvcResult createEventResult = mockMvc.perform(post("/api/events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createEventReq)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn();

        EventResponse createdEvent = objectMapper.readValue(
                createEventResult.getResponse().getContentAsString(),
                EventResponse.class
        );
        assertEquals(EventStatus.DRAFT, createdEvent.status());

        // -------------------------------------------------------------
        // Bước 5: Admin thực hiện PUBLISH Event sang trạng thái OPEN
        // -------------------------------------------------------------
        MvcResult publishResult = mockMvc.perform(post("/api/events/" + createdEvent.id() + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken.token()))
                .andExpect(status().isOk())
                .andReturn();

        EventResponse publishedEvent = objectMapper.readValue(
                publishResult.getResponse().getContentAsString(),
                EventResponse.class
        );
        assertEquals(EventStatus.OPEN, publishedEvent.status());

        // -------------------------------------------------------------
        // Bước 6: Participant thực hiện đăng ký tham gia Event (OPEN)
        // -------------------------------------------------------------
        RegisterParticipantRequest registerPartReq = new RegisterParticipantRequest(registeredParticipant.id());
        MvcResult registrationResult = mockMvc.perform(post("/api/events/" + publishedEvent.id() + "/registrations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + participantToken.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerPartReq)))
                .andExpect(status().isCreated())
                .andReturn();

        RegistrationResponse registration = objectMapper.readValue(
                registrationResult.getResponse().getContentAsString(),
                RegistrationResponse.class
        );
        assertEquals(RegistrationStatus.ACTIVE, registration.status());

        // Kiểm tra ghế trống giảm đi 1
        com.eventhub.domain.Event eventInDb = eventRepository.findById(publishedEvent.id()).orElseThrow();
        assertEquals(19, eventInDb.getAvailableSeats());

        // -------------------------------------------------------------
        // Bước 7: Participant thực hiện HỦY lượt đăng ký tham gia
        // -------------------------------------------------------------
        mockMvc.perform(delete("/api/events/" + publishedEvent.id() + "/registrations/" + registration.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + participantToken.token()))
                .andExpect(status().isNoContent());

        // Kiểm tra trạng thái lượt đăng ký chuyển sang CANCELLED và khôi phục ghế trống
        com.eventhub.domain.Registration regInDb = registrationRepository.findById(registration.id()).orElseThrow();
        assertEquals(RegistrationStatus.CANCELLED, regInDb.getStatus());
        assertNotNull(regInDb.getCancelledAt());

        eventInDb = eventRepository.findById(publishedEvent.id()).orElseThrow();
        assertEquals(20, eventInDb.getAvailableSeats());
    }
}
