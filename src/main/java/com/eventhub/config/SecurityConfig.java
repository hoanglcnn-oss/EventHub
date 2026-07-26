package com.eventhub.config;

import com.eventhub.domain.UserRole;
import com.eventhub.exception.CustomAccessDeniedHandler;
import com.eventhub.exception.CustomAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomAuthenticationEntryPoint customAuthenticationEntryPoint,
            CustomAccessDeniedHandler customAccessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.customAuthenticationEntryPoint = customAuthenticationEntryPoint;
        this.customAccessDeniedHandler = customAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(customAuthenticationEntryPoint)
                    .accessDeniedHandler(customAccessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                    // Public endpoints
                    .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/{eventId}").permitAll()

                    // Admin only endpoints
                    .requestMatchers(HttpMethod.POST, "/api/events").hasRole(UserRole.EVENT_ADMIN.name())
                    .requestMatchers(HttpMethod.PUT, "/api/events/{eventId}").hasRole(UserRole.EVENT_ADMIN.name())
                    .requestMatchers(HttpMethod.POST, "/api/events/{eventId}/cancellations").hasRole(UserRole.EVENT_ADMIN.name())
                    .requestMatchers(HttpMethod.POST, "/api/events/{eventId}/publish").hasRole(UserRole.EVENT_ADMIN.name())
                    .requestMatchers(HttpMethod.GET, "/api/participants").hasRole(UserRole.EVENT_ADMIN.name())
                    .requestMatchers(HttpMethod.GET, "/api/events/{eventId}/registrations").hasRole(UserRole.EVENT_ADMIN.name())

                    // Admin & Participant endpoints (Ownership checks are implemented in Service layer)
                    .requestMatchers("/api/participants/{participantId}").hasAnyRole(UserRole.EVENT_ADMIN.name(), UserRole.PARTICIPANT.name())
                    .requestMatchers(HttpMethod.POST, "/api/events/{eventId}/registrations").hasAnyRole(UserRole.EVENT_ADMIN.name(), UserRole.PARTICIPANT.name())
                    .requestMatchers(HttpMethod.DELETE, "/api/events/{eventId}/registrations/{registrationId}").hasAnyRole(UserRole.EVENT_ADMIN.name(), UserRole.PARTICIPANT.name())

                    // Fallback: deny all other requests
                    .anyRequest().denyAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
