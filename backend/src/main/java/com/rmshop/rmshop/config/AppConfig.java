package com.rmshop.rmshop.config;

import com.rmshop.rmshop.mail.AppsScriptEmailSender;
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
    public EmailSender emailSender(@Value("${rmshop.mail.relay-url:}") String relayUrl,
                                   @Value("${rmshop.mail.relay-secret:}") String relaySecret,
                                   @Value("${rmshop.mail.brevo-api-key:}") String apiKey,
                                   @Value("${rmshop.mail.from-email:}") String fromEmail,
                                   @Value("${rmshop.mail.from-name:RMShop}") String fromName) {
        return chooseSender(relayUrl, relaySecret, apiKey, fromEmail, fromName);
    }

    // The Gmail relay wins when configured; Brevo stays available for when the
    // shop has its own domain. With neither, emails only go to the log.
    static EmailSender chooseSender(String relayUrl, String relaySecret, String apiKey, String fromEmail, String fromName) {
        if (!relayUrl.isBlank() && !relaySecret.isBlank()) {
            log.info("Password-reset emails go through the Google Apps Script relay.");
            return new AppsScriptEmailSender(relayUrl, relaySecret);
        }
        if (!apiKey.isBlank() && !fromEmail.isBlank()) {
            log.info("Password-reset emails go through Brevo.");
            return new BrevoEmailSender(apiKey, fromEmail, fromName);
        }
        log.warn("No email service configured (EMAIL_RELAY_URL/EMAIL_RELAY_SECRET or BREVO_API_KEY/MAIL_FROM_EMAIL): "
                + "password-reset emails will only be written to the log.");
        return new LoggingEmailSender();
    }
}
