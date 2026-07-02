package com.paystream.fraud.infrastructure.config;

import com.paystream.fraud.infrastructure.security.InternalServiceAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final InternalServiceAuthFilter internalServiceAuthFilter;

    public SecurityConfig(InternalServiceAuthFilter internalServiceAuthFilter) {
        this.internalServiceAuthFilter = internalServiceAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/api/v1/fraud/check").permitAll()  // validated by InternalServiceAuthFilter
                .anyRequest().authenticated()
            )
            .addFilterBefore(internalServiceAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
