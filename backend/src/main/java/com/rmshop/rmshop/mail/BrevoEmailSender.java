package com.rmshop.rmshop.mail;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

// Sends through Brevo's HTTP API (https://developers.brevo.com). HTTP is used
// instead of SMTP because Render's free tier blocks outgoing SMTP ports.
public class BrevoEmailSender implements EmailSender {

    private final RestClient restClient;
    private final String fromEmail;
    private final String fromName;

    public BrevoEmailSender(String apiKey, String fromEmail, String fromName) {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .defaultHeader("api-key", apiKey)
                .build();
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    @Override
    public void send(String to, String subject, String textBody, String htmlBody) {
        Map<String, Object> payload = Map.of(
                "sender", Map.of("name", fromName, "email", fromEmail),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "textContent", textBody,
                "htmlContent", htmlBody);
        try {
            restClient.post()
                    .uri("/smtp/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new EmailDeliveryException("Brevo rejected or did not accept the email", e);
        }
    }
}
