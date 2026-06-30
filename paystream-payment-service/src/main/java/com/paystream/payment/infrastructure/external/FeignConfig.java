package com.paystream.payment.infrastructure.external;

import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/** Feign client configuration — timeouts and retry policy for downstream service calls. */
@Configuration
public class FeignConfig {

    @Bean
    public Request.Options requestOptions() {
        // connectTimeout=2s, readTimeout=5s — aligns with spec
        return new Request.Options(2, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, true);
    }

    @Bean
    public Retryer retryer() {
        // Retry once after 100ms, max 2 attempts — only 5xx triggers retry (Feign default)
        return new Retryer.Default(100L, 500L, 2);
    }
}
