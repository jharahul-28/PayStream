package com.paystream.auth.security.config;

import com.paystream.auth.security.jwt.JwtAuthFilter;
import com.paystream.auth.security.jwt.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtService          jwtService;
    private final StringRedisTemplate redisTemplate;

    public SecurityConfig(JwtService jwtService, StringRedisTemplate redisTemplate) {
        this.jwtService    = jwtService;
        this.redisTemplate = redisTemplate;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public auth endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        // JWKS endpoint — used by gateway on startup
                        .requestMatchers("/.well-known/jwks.json").permitAll()
                        // Health check
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthFilter(jwtService, redisTemplate),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * BCrypt strength 12 — compliant with NIST SP 800-63b for password hashing.
     * Strength 12 produces ~250ms verification time on modern hardware, which is
     * acceptable for a login endpoint but acts as a natural brute-force deterrent.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
