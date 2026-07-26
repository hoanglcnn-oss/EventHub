package com.eventhub.service;

import com.eventhub.controller.dto.LoginRequest;
import com.eventhub.controller.dto.LoginResponse;
import com.eventhub.controller.dto.ParticipantResponse;
import com.eventhub.controller.dto.RegisterUserRequest;
import com.eventhub.controller.mapper.ParticipantMapper;
import com.eventhub.domain.Participant;
import com.eventhub.domain.UserAccount;
import com.eventhub.domain.UserRole;
import com.eventhub.exception.DuplicateEmailException;
import com.eventhub.repository.ParticipantRepository;
import com.eventhub.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantMapper participantMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final Clock clock;

    @Value("${jwt.expiration:3600000}")
    private long jwtExpirationMs;

    public AuthService(
            UserAccountRepository userAccountRepository,
            ParticipantRepository participantRepository,
            ParticipantMapper participantMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.participantRepository = participantRepository;
        this.participantMapper = participantMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.clock = clock;
    }

    @Transactional
    public ParticipantResponse register(RegisterUserRequest request) {
        if (userAccountRepository.findByEmail(request.email()).isPresent() ||
                participantRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException(request.email());
        }

        // Tạo Participant trước
        Participant participant = Participant.builder()
                .fullName(request.fullName())
                .email(request.email())
                .createdAt(Instant.now(clock))
                .build();
        Participant savedParticipant = participantRepository.save(participant);

        // Tạo UserAccount liên kết với Participant
        UserAccount account = UserAccount.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.PARTICIPANT) // Luôn mặc định là PARTICIPANT cho đăng ký công khai
                .enabled(true)
                .createdAt(Instant.now(clock))
                .participant(savedParticipant)
                .build();
        userAccountRepository.save(account);

        return participantMapper.toResponse(savedParticipant);
    }

    public LoginResponse login(LoginRequest request) {
        // Xác thực thông tin đăng nhập qua AuthenticationManager
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        // Tải tài khoản để lấy vai trò và tạo token
        UserAccount account = userAccountRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("User account not found"));

        String token = jwtService.generateToken(account.getEmail(), account.getRole().name());

        return new LoginResponse(token, "Bearer", jwtExpirationMs / 1000);
    }
}
