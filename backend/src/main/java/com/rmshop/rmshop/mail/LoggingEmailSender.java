package com.rmshop.rmshop.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Fallback when no email provider is configured: writes the email to the
// server log so the reset flow can still be tried locally. Only the server
// owner can read these logs, but production should always set BREVO_API_KEY.
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String textBody, String htmlBody) {
        log.warn("Email provider not configured (BREVO_API_KEY unset). Email to {} with subject \"{}\":\n{}", to, subject, textBody);
    }
}
