package com.rmshop.rmshop.config;

import com.rmshop.rmshop.mail.BrevoEmailSender;
import com.rmshop.rmshop.mail.EmailSender;
import com.rmshop.rmshop.mail.LoggingEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    // Injected wherever "now" matters so tests can move time forward
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public EmailSender emailSender(@Value("${rmshop.mail.brevo-api-key:}") String apiKey,
                                   @Value("${rmshop.mail.from-email:}") String fromEmail,
                                   @Value("${rmshop.mail.from-name:RMShop}") String fromName) {
        if (apiKey.isBlank() || fromEmail.isBlank()) {
            log.warn("BREVO_API_KEY or MAIL_FROM_EMAIL is not set: password-reset emails will only be written to the log.");
            return new LoggingEmailSender();
        }
        return new BrevoEmailSender(apiKey, fromEmail, fromName);
    }
}
