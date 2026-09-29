package com.rmshop.rmshop.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

// Black-box: which website addresses the browser will let call the API.
class CorsApiTest extends ApiTestBase {

    @ParameterizedTest(name = "[{index}] {0} allowed={1}")
    @CsvSource({
            "https://rmshop.pages.dev,             true",
            "https://a1b2c3d4.rmshop.pages.dev,    true",
            "https://rmshop-seven.vercel.app,      true",
            "http://localhost:5500,                true",
            "https://evil.pages.dev,               false",
            "https://rmshop.pages.dev.evil.com,    false",
            "https://notrmshop.pages.dev,          false",
    })
    void onlyKnownFrontendsMayCallTheApi(String origin, boolean allowed) throws Exception {
        HttpRequest preflight = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/products"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "x-user-id")
                .build();
        HttpResponse<Void> response = HttpClient.newHttpClient().send(preflight, HttpResponse.BodyHandlers.discarding());

        String allowedOrigin = response.headers().firstValue("Access-Control-Allow-Origin").orElse(null);
        if (allowed) {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(allowedOrigin).isEqualTo(origin);
        } else {
            assertThat(allowedOrigin).isNull();
        }
    }
}
