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
