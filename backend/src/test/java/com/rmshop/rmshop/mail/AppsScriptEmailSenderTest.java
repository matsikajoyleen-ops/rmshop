package com.rmshop.rmshop.mail;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

// Talks to a local stand-in for the Apps Script web app that behaves like the
// real one: POST /exec answers 302, and the redirect target serves the JSON.
class AppsScriptEmailSenderTest {

    private static final String SECRET = "test-secret";

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<Map<String, Object>> received = new AtomicReference<>();
    private volatile String resultJson;
    private volatile int resultStatus;

    @BeforeEach
    void startFakeAppsScript() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/exec", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = JsonMapper.builder().build().readValue(new String(body, StandardCharsets.UTF_8), Map.class);
            received.set(parsed);
            exchange.getResponseHeaders().add("Location", baseUrl + "/result");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/result", exchange -> {
            byte[] out = resultJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(resultStatus, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        resultJson = "{\"ok\":true,\"remainingToday\":99}";
        resultStatus = 200;
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private AppsScriptEmailSender sender() {
        return new AppsScriptEmailSender(baseUrl + "/exec", SECRET);
    }

    @Test
    void sendsTheEmailWithTheSecretAndFollowsTheRedirect() {
        assertThatCode(() -> sender().send("joy@shop.co.zw", "Your code", "Code: 123456", "<p>Code: 123456</p>"))
                .doesNotThrowAnyException();

        assertThat(received.get())
                .containsEntry("secret", SECRET)
                .containsEntry("to", "joy@shop.co.zw")
                .containsEntry("subject", "Your code")
                .containsEntry("text", "Code: 123456")
                .containsEntry("html", "<p>Code: 123456</p>");
    }

    @Test
    void refusalFromTheScriptIsReportedWithItsReason() {
        resultJson = "{\"ok\":false,\"error\":\"Wrong or missing secret\"}";
        EmailSender.EmailDeliveryException error = catchThrowableOfType(EmailSender.EmailDeliveryException.class,
                () -> sender().send("joy@shop.co.zw", "s", "t", "h"));
        assertThat(error).hasMessageContaining("Wrong or missing secret");
    }

    @Test
    void htmlInsteadOfJsonPointsAtTheDeploymentSettings() {
        resultJson = "<html>Sign in to continue</html>";
        EmailSender.EmailDeliveryException error = catchThrowableOfType(EmailSender.EmailDeliveryException.class,
                () -> sender().send("joy@shop.co.zw", "s", "t", "h"));
        assertThat(error).hasMessageContaining("'Anyone'");
    }

    @Test
    void httpErrorIsReported() {
        resultStatus = 500;
        resultJson = "{}";
        EmailSender.EmailDeliveryException error = catchThrowableOfType(EmailSender.EmailDeliveryException.class,
                () -> sender().send("joy@shop.co.zw", "s", "t", "h"));
        assertThat(error).hasMessageContaining("HTTP 500");
    }

    @Test
    void unreachableRelayIsReported() {
        server.stop(0);
        EmailSender.EmailDeliveryException error = catchThrowableOfType(EmailSender.EmailDeliveryException.class,
                () -> sender().send("joy@shop.co.zw", "s", "t", "h"));
        assertThat(error).hasMessageContaining("Could not reach");
    }
}
