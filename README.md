# EventHub Campus Event Registration API

Backend REST API for managing campus events, participants, and event registrations.

## 🛠 Tech Stack & Versions
*   **Java**: 17 (Microsoft OpenJDK 17.0.17)
*   **Spring Boot**: 3.3.1
*   **Database**: H2 Database (File-based for runtime, In-Memory for testing)
*   **Security**: Spring Security + JJWT 0.12.5 (Token-based authentication)
*   **Build Tool**: Maven (via Maven Wrapper `./mvnw`)

---

## 🚀 How to Run the Application

Since the system uses Java 17, make sure to set the `JAVA_HOME` pointing to Java 17 before running:

### Windows (PowerShell)
```powershell
$env:JAVA_HOME = "C:\Users\Admin\.jdks\ms-17.0.17"
./mvnw spring-boot:run
```

### Windows (CMD)
```cmd
set JAVA_HOME=C:\Users\Admin\.jdks\ms-17.0.17
mvnw spring-boot:run
```

---

## 📂 Package Architecture

To ensure the codebase is easily reusable for other projects with similar requirements (what we call a "template"), we organize code into a clean, decoupled **layered package structure**:

```text
com.eventhub
├── EventhubApplication.java   # Root Main Class (Spring Boot entry point)
├── config                     # Infrastructure configurations (Clock, Security, etc.)
│   └── ClockConfig.java       # UTC Clock bean declaration
├── domain                     # Database-mapped JPA Entities and Enums
│   ├── Event.java
│   ├── EventStatus.java       # DRAFT, OPEN, CLOSED, CANCELLED
│   ├── Participant.java
│   ├── Registration.java
│   ├── RegistrationStatus.java # ACTIVE, CANCELLED
│   ├── UserAccount.java       # Auth credentials
│   └── UserRole.java          # EVENT_ADMIN, PARTICIPANT
├── repository                 # Spring Data JPA repositories (Database operations)
│   ├── EventRepository.java
│   ├── ParticipantRepository.java
│   ├── RegistrationRepository.java
│   └── UserAccountRepository.java
├── service                    # Business logic implementations (Transactions)
├── controller                 # REST endpoints and request handlers
│   └── dto                    # Dedicated Request/Response Data Transfer Objects (DTO)
└── exception                  # Global Exception handling and API error contracts
```

---

## 🔗 Object Graph (Wiring Plan)

Spring IoC Container discovers and wires the components automatically at startup:

```text
HTTP Request (Client)
      │
      ▼
DispatcherServlet (Spring MVC controller dispatcher)
      │
      ▼
[Controller Layer]
  EventController / ParticipantController / RegistrationController
      │ (Constructor Injection)
      ▼
[Service Layer] (Transactional business boundaries)
  EventService / ParticipantService / RegistrationService
      │ (Constructor Injection)
      ├──► Clock (UTC Clock Bean for date validations)
      ▼
[Repository Layer] (Spring Data JPA)
  EventRepository / ParticipantRepository / RegistrationRepository
      │ (Hibernate / JPA)
      ▼
Database (H2 Database engine)
```

---

## 📝 Learning Disclosures
This project is part of a Methodical Spring Boot assignment. 
*   Initial project setup template fetched from start.spring.io.
*   Lombok is used for boilerplate-free POJO creation.
*   JJWT is used for robust JWT signing and parsing.

---

## 📞 API Endpoints & JSON Examples

All request bodies and responses use the `camelCase` naming style. Datetime fields use the **ISO 8601 UTC** format (ending with `Z` or representation offset).

### 1. Events API
* **Create an Event**
  * `POST /api/events`
  * **Request Body:**
    ```json
    {
      "title": "Spring Boot Hackathon 2026",
      "description": "24-hour coding challenge at campus.",
      "location": "IT Center, Room 502",
      "startAt": "2026-10-15T09:00:00",
      "capacity": 50
    }
    ```
  * **Response Body (201 Created with Location Header `/api/events/{id}`):**
    ```json
    {
      "id": 1,
      "title": "Spring Boot Hackathon 2026",
      "description": "24-hour coding challenge at campus.",
      "location": "IT Center, Room 502",
      "startAt": "2026-10-15T09:00:00",
      "capacity": 50,
      "availableSeats": 50,
      "status": "DRAFT",
      "createdAt": "2026-07-26T01:47:31.123Z"
    }
    ```

* **Get Event Details**
  * `GET /api/events/{eventId}`
  * **Response Body (200 OK):**
    ```json
    {
      "id": 1,
      "title": "Spring Boot Hackathon 2026",
      "description": "24-hour coding challenge at campus.",
      "location": "IT Center, Room 502",
      "startAt": "2026-10-15T09:00:00",
      "capacity": 50,
      "availableSeats": 50,
      "status": "DRAFT",
      "createdAt": "2026-07-26T01:47:31.123Z"
    }
    ```

* **Search/List Events (Paginated + Filtered)**
  * `GET /api/events?title=hackathon&status=DRAFT&page=0&size=20`
  * **Response Body (200 OK):**
    ```json
    {
      "content": [
        {
          "id": 1,
          "title": "Spring Boot Hackathon 2026",
          "location": "IT Center, Room 502",
          "startAt": "2026-10-15T09:00:00",
          "availableSeats": 50,
          "status": "DRAFT"
        }
      ],
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1
    }
    ```

* **Update Event**
  * `PUT /api/events/{eventId}`
  * **Request Body:**
    ```json
    {
      "title": "Spring Boot Hackathon 2026 - Updated",
      "description": "Now 36-hour coding challenge.",
      "location": "Campus Main Hall",
      "startAt": "2026-10-15T08:00:00",
      "capacity": 100
    }
    ```
  * **Response Body (200 OK):**
    ```json
    {
      "id": 1,
      "title": "Spring Boot Hackathon 2026 - Updated",
      "description": "Now 36-hour coding challenge.",
      "location": "Campus Main Hall",
      "startAt": "2026-10-15T08:00:00",
      "capacity": 100,
      "availableSeats": 100,
      "status": "DRAFT",
      "createdAt": "2026-07-26T01:47:31.123Z"
    }
    ```

* **Cancel Event**
  * `POST /api/events/{eventId}/cancellations`
  * **Response (204 No Content - Empty Body)**

---

### 2. Participants API
* **Create Participant Profile**
  * `POST /api/participants`
  * **Request Body:**
    ```json
    {
      "fullName": "Nguyen Van A",
      "email": "vana@student.edu.vn"
    }
    ```
  * **Response Body (201 Created with Location Header `/api/participants/{id}`):**
    ```json
    {
      "id": 1,
      "fullName": "Nguyen Van A",
      "email": "vana@student.edu.vn",
      "createdAt": "2026-07-26T01:47:31.123Z"
    }
    ```

* **Get Participant Details**
  * `GET /api/participants/{participantId}`
  * **Response Body (200 OK):**
    ```json
    {
      "id": 1,
      "fullName": "Nguyen Van A",
      "email": "vana@student.edu.vn",
      "createdAt": "2026-07-26T01:47:31.123Z"
    }
    ```

* **List Participants (Paginated)**
  * `GET /api/participants?page=0&size=20`
  * **Response Body (200 OK):**
    ```json
    {
      "content": [
        {
          "id": 1,
          "fullName": "Nguyen Van A",
          "email": "vana@student.edu.vn",
          "createdAt": "2026-07-26T01:47:31.123Z"
        }
      ],
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1
    }
    ```

---

### 3. Registrations API
* **Register a Participant for an Event**
  * `POST /api/events/{eventId}/registrations`
  * **Request Body:**
    ```json
    {
      "participantId": 1
    }
    ```
  * **Response Body (201 Created with Location Header `/api/events/{eventId}/registrations/{id}`):**
    ```json
    {
      "id": 1,
      "eventId": 1,
      "eventTitle": "Spring Boot Hackathon 2026",
      "participantId": 1,
      "participantName": "Nguyen Van A",
      "registeredAt": "2026-07-26T01:47:31.123Z",
      "cancelledAt": null,
      "status": "ACTIVE"
    }
    ```

* **List Event Registrations (Paginated)**
  * `GET /api/events/{eventId}/registrations?page=0&size=20`
  * **Response Body (200 OK):**
    ```json
    {
      "content": [
        {
          "id": 1,
          "eventId": 1,
          "eventTitle": "Spring Boot Hackathon 2026",
          "participantId": 1,
          "participantName": "Nguyen Van A",
          "registeredAt": "2026-07-26T01:47:31.123Z",
          "cancelledAt": null,
          "status": "ACTIVE"
        }
      ],
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1
    }
    ```

* **Cancel Registration**
  * `DELETE /api/events/{eventId}/registrations/{registrationId}`
  * **Response (204 No Content - Empty Body)**

---

## 🔒 Concurrency & Transaction Integrity

### Naive Seat Check Concurrency Limitation
In our naive seat check implementation, the registration service performs the following sequential steps inside a transaction:
1. Fetch the event entity: `eventRepository.findById(eventId)`
2. Check in Java memory if `event.getAvailableSeats() > 0`
3. Decrement seats: `event.setAvailableSeats(seats - 1)`
4. Save the event and persist the registration.

**The Limitation:**
Under high concurrent registration requests for the same event, a race condition occurs. If two transactions read the `availableSeats` simultaneously (e.g., both see `1`), both will pass the check `availableSeats > 0` in memory. They will both decrement it to `0`, persist two separate active registrations, and save the event. 
*   **Result:** The event is overbooked (2 participants successfully registered when only 1 seat was actually left).

### Mitigation Strategies (Advanced Options)
To guarantee absolute transactional integrity under concurrency:
1.  **Pessimistic Locking:** Use `@Lock(LockModeType.PESSIMISTIC_WRITE)` on `findById` to lock the Event row at the database level (`SELECT ... FOR UPDATE`) during the read phase. This forces other concurrent transactions to wait until the current transaction commits or rolls back.
2.  **Optimistic Locking:** Add a version field (`@Version`) to the `Event` entity. If a concurrent transaction updates the event seats in the database first, the second transaction will fail to commit and throw an `OptimisticLockException`, which we can handle and retry or return a conflict error.
3.  **Database Constraint:** Add a database constraint `CHECK (available_seats >= 0)` on the `events` table as the final line of defense to reject any query that attempts to decrease seats below zero.


