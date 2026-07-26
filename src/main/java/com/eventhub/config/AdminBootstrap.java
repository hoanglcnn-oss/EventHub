package com.eventhub.config;

import com.eventhub.domain.UserAccount;
import com.eventhub.domain.UserRole;
import com.eventhub.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${admin.email:admin@eventhub.com}") String adminEmail,
            @Value("${admin.password:AdminPassword123!}") String adminPassword) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userAccountRepository.findByEmail(adminEmail).isEmpty()) {
            UserAccount admin = UserAccount.builder()
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .role(UserRole.EVENT_ADMIN)
                    .enabled(true)
                    .createdAt(Instant.now(clock))
                    .participant(null) // Admin không cần Participant profile
                    .build();

            userAccountRepository.save(admin);
            log.info("Idempotent Admin Bootstrap: Created default admin account with email: {}", adminEmail);
        } else {
            log.info("Idempotent Admin Bootstrap: Admin account with email: {} already exists", adminEmail);
        }
    }
}
