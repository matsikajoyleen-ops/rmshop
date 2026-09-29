package com.rmshop.rmshop.mail;

import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

// Sends through a Google Apps Script web app running in the shop owner's
// Google account (see docs/email-relay.gs). The script sends with MailApp,
// so emails really come from their Gmail and pass Gmail's sender checks,
// with no domain or email provider needed.
//
// Apps Script always answers HTTP 200 (after a redirect), so success is read
// from the JSON body: {"ok": true} or {"ok": false, "error": "..."}.
public class AppsScriptEmailSender implements EmailSender {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final URI relayUrl;
    private final String secret;
    private final HttpClient http;

    public AppsScriptEmailSender(String relayUrl, String secret) {
        this.relayUrl = URI.create(relayUrl);
        this.secret = secret;
        // Apps Script replies to a POST with a 302 to the result page
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void send(String to, String subject, String textBody, String htmlBody) {
        String payload = JSON.writeValueAsString(Map.of(
                "secret", secret,
                "to", to,
                "subject", subject,
                "text", textBody,
                "html", htmlBody));
        HttpRequest request = HttpRequest.newBuilder(relayUrl)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailDeliveryException("Interrupted while contacting the email relay", e);
        } catch (Exception e) {
            throw new EmailDeliveryException("Could not reach the email relay", e);
        }

        if (response.statusCode() != 200) {
            throw new EmailDeliveryException("Email relay answered HTTP " + response.statusCode(), null);
        }
        Map<?, ?> result;
        try {
            result = JSON.readValue(response.body(), Map.class);
        } catch (Exception e) {
            // Usually an HTML page: the deployment isn't public or the URL is wrong
            throw new EmailDeliveryException("Email relay did not answer with JSON. Check the web app URL and that access is set to 'Anyone'", e);
        }
        if (!Boolean.TRUE.equals(result.get("ok"))) {
            throw new EmailDeliveryException("Email relay refused the email: " + result.get("error"), null);
        }
    }
}
