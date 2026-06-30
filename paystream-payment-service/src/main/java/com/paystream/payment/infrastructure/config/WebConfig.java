package com.paystream.payment.infrastructure.config;

import com.paystream.common.filter.CorrelationIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Registers shared filters and enables GlobalExceptionHandler from paystream-common. */
@Configuration
@ComponentScan(basePackages = "com.paystream.common.exception")
public class WebConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new CorrelationIdFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(1);
        return bean;
    }
}
