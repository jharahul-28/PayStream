package com.paystream.wallet.infrastructure.config;

import com.paystream.wallet.security.InternalServiceAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Wallet service security configuration.
 *
 * All requests arriving here have already been validated by the API Gateway JWT filter.
 * The gateway strips the Authorization header and injects X-User-Id / X-User-Role headers.
 * Internal debit/credit endpoints additionally require X-Internal-Service-Key.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
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
                        .anyRequest().permitAll()  // JWT already validated by gateway
                )
                .addFilterBefore(new InternalServiceAuthFilter(internalServiceKey),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
