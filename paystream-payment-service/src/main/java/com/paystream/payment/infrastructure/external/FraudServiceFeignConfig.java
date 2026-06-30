package com.paystream.payment.infrastructure.external;

import feign.Request;
import org.springframework.context.annotation.Bean;

public class FraudServiceFeignConfig {

    @Bean
    public Request.Options fraudServiceOptions() {
        return new Request.Options(2000, java.util.concurrent.TimeUnit.MILLISECONDS,
                5000, java.util.concurrent.TimeUnit.MILLISECONDS, true);
    }
}
