package com.rmshop.rmshop.api;

import com.rmshop.rmshop.model.User;
import com.rmshop.rmshop.repository.AttendanceLogRepository;
import com.rmshop.rmshop.repository.PasswordResetTokenRepository;
import com.rmshop.rmshop.repository.UserRepository;
import com.rmshop.rmshop.support.CapturingEmailSender;
import com.rmshop.rmshop.support.MutableClock;
import com.rmshop.rmshop.support.TestBeans;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// Black-box base: starts the real server on a random port and talks to it
// over HTTP only, exactly like the browser does. Database rows are created
// directly purely as test setup (the "given"); every check goes through the API.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestBeans.class)
abstract class ApiTestBase {

    private static final AtomicInteger CLIENT_COUNTER = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    int port;

    @Autowired UserRepository userRepository;
    @Autowired AttendanceLogRepository attendanceLogRepository;
    @Autowired PasswordResetTokenRepository tokenRepository;
    @Autowired MutableClock clock;
    @Autowired CapturingEmailSender mail;

    /** A fresh device address per test, so lockouts from one test never leak into another. */
    String clientIp;

    @BeforeEach
    void resetState() {
        tokenRepository.deleteAll();
        attendanceLogRepository.deleteAll();
        userRepository.deleteAll();
        mail.clear();
        clientIp = "203.0.113." + CLIENT_COUNTER.incrementAndGet();
        // Lets any lockouts and request limits left by earlier tests expire
        clock.advance(Duration.ofHours(2));
    }

    record Response(int status, Map<String, Object> body) {
        Object get(String key) { return body.get(key); }
    }

    Response post(String path, Map<String, ?> body) {
        return send("POST", path, body, null);
    }

    Response send(String method, String path, Map<String, ?> body, Long asUserId) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", clientIp)
                    .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            if (asUserId != null) request.header("X-User-Id", String.valueOf(asUserId));
            HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = response.body().isBlank() ? Map.of() : JSON.readValue(response.body(), Map.class);
            return new Response(response.statusCode(), parsed);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    User givenUser(String name, User.Role role, String accessCode, String email) {
        User user = new User(name, role, sha256(accessCode));
        user.setEmail(email);
        return userRepository.save(user);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
