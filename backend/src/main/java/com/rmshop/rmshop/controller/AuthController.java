package com.rmshop.rmshop.controller;

import com.rmshop.rmshop.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Open endpoints: the person using them has forgotten their code, so there is
// no logged-in user to check.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final PasswordResetService passwordResetService;

    public AuthController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/forgot-password")
    public PasswordResetService.ResetRequestResult forgotPassword(@RequestBody ForgotPasswordRequest request) {
        return passwordResetService.requestReset(request.email());
    }

    @PostMapping("/verify-reset-code")
    public Map<String, Object> verifyResetCode(@RequestBody VerifyCodeRequest request) {
        passwordResetService.verifyCode(request.email(), request.code());
        return Map.of("valid", true, "message", "Code confirmed. Now choose your new access code.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        passwordResetService.resetAccessCode(request.email(), request.code(),
                request.newAccessCode(), request.confirmAccessCode(), ClientAddress.of(http));
        return ResponseEntity.ok(Map.of("message", "Your access code has been changed. You can now log in with it."));
    }

    public record ForgotPasswordRequest(String email) {}
    public record VerifyCodeRequest(String email, String code) {}
    public record ResetPasswordRequest(String email, String code, String newAccessCode, String confirmAccessCode) {}
}
