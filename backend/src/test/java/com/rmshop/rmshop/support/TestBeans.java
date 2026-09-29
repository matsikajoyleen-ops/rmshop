package com.rmshop.rmshop.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

@TestConfiguration
public class TestBeans {

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(Instant.parse("2026-09-29T08:00:00Z"));
    }

    @Bean
    @Primary
    public CapturingEmailSender capturingEmailSender() {
        return new CapturingEmailSender();
    }
}
