package com.rmshop.rmshop.controller;

import jakarta.servlet.http.HttpServletRequest;

final class ClientAddress {

    private ClientAddress() {}

    // Behind Render's proxy every request comes from the proxy's address, so
    // the real client is the first entry of X-Forwarded-For.
    static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
