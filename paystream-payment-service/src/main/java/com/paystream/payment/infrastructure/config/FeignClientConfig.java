package com.paystream.payment.infrastructure.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/** Enables Feign clients for the payment service. */
@Configuration
@EnableFeignClients(basePackages = "com.paystream.payment.infrastructure.external")
public class FeignClientConfig {}
