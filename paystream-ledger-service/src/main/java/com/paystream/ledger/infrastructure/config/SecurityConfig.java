package com.paystream.ledger.infrastructure.config;

import com.paystream.ledger.security.InternalServiceAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Ledger service security — stateless, JWT already validated by gateway.
 * POST /entries is additionally guarded by InternalServiceAuthFilter.
 */
@Configuration
public class SecurityConfig {

    @Value("${paystream.internal.service-key:dev-only-local-key}")
    private String internalServiceKey;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(new InternalServiceAuthFilter(internalServiceKey),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
