package com.rmshop.rmshop.service;

import com.rmshop.rmshop.exception.ApiException;
import com.rmshop.rmshop.mail.EmailSender;
import com.rmshop.rmshop.model.PasswordResetToken;
import com.rmshop.rmshop.model.User;
import com.rmshop.rmshop.repository.PasswordResetTokenRepository;
import com.rmshop.rmshop.repository.UserRepository;
import com.rmshop.rmshop.validation.InputValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// "Forgot access code" for managers, in three steps:
//   1. requestReset: the manager enters their email; a 6-digit code is emailed.
//   2. verifyCode:   they type the code, proving they own the inbox.
//   3. resetAccessCode: code + new access code (typed twice) sets the new code.
//
// Step 1 answers the same way whether or not the email belongs to a manager,
// so the form can't be used to discover which emails have accounts. Typing
// mistakes in the email itself are still reported precisely.
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    public static final Duration CODE_LIFETIME = Duration.ofMinutes(15);
    public static final int MAX_CODE_ATTEMPTS = 5;
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    public static final int MAX_REQUESTS_PER_HOUR = 5;

    static final String REQUEST_ACCEPTED_MESSAGE =
            "If this email belongs to a manager account, a 6-digit confirmation code is on its way. "
            + "Check your inbox (and spam folder). The code expires in " + CODE_LIFETIME.toMinutes() + " minutes.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailSender emailSender;
    private final LoginAttemptService loginAttemptService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    // Request timestamps per email address, for the resend limits
    private final Map<String, Deque<Instant>> requestLog = new ConcurrentHashMap<>();

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                EmailSender emailSender, LoginAttemptService loginAttemptService, Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailSender = emailSender;
        this.loginAttemptService = loginAttemptService;
        this.clock = clock;
    }

    public record ResetRequestResult(String message, long expiresInSeconds, long resendAvailableInSeconds) {}

    @Transactional
    public ResetRequestResult requestReset(String rawEmail) {
        String email = InputValidator.validateEmail(rawEmail);
        throttle(email);

        Optional<User> manager = findActiveManager(email);
        if (manager.isPresent()) {
            User user = manager.get();
            LocalDateTime now = LocalDateTime.now(clock);
            cancelOpenTokens(user.getId(), now);
            String code = String.format("%06d", random.nextInt(1_000_000));
            tokenRepository.save(new PasswordResetToken(user.getId(), hashResetCode(user.getId(), code), now, now.plus(CODE_LIFETIME)));
            try {
                emailSender.send(email, "Your RMShop confirmation code: " + code,
                        resetEmailText(user.getFullName(), code), resetEmailHtml(user.getFullName(), code));
            } catch (EmailSender.EmailDeliveryException e) {
                log.error("Could not send password-reset email", e);
                throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_SEND_FAILED",
                        "We couldn't send the email right now. Please try again in a few minutes.", "email");
            }
        }
        return new ResetRequestResult(REQUEST_ACCEPTED_MESSAGE, CODE_LIFETIME.toSeconds(), RESEND_COOLDOWN.toSeconds());
    }

    /** Step 2: checks the emailed code. Wrong guesses are saved even though an error is thrown. */
    @Transactional(noRollbackFor = ApiException.class)
    public void verifyCode(String rawEmail, String rawCode) {
        String email = InputValidator.validateEmail(rawEmail);
        String code = InputValidator.validateSixDigitCode(rawCode, "code", "confirmation code");
        checkToken(email, code);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void resetAccessCode(String rawEmail, String rawCode, String newAccessCode, String confirmAccessCode, String clientKey) {
        // Check every field's format first, so a typo in the new code doesn't use up a code attempt
        String email = InputValidator.validateEmail(rawEmail);
        String code = InputValidator.validateSixDigitCode(rawCode, "code", "confirmation code");
        InputValidator.validateNewAccessCode(newAccessCode, confirmAccessCode);

        Verified verified = checkToken(email, code);
        User user = verified.user();
        String newHash = UserService.hashAccessCode(newAccessCode);
        if (newHash.equals(user.getAccessCode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CODE_SAME_AS_OLD",
                    "Your new access code must be different from your old one.", "newAccessCode");
        }
        if (userRepository.existsByAccessCode(newHash)) {
            throw new ApiException(HttpStatus.CONFLICT, "CODE_IN_USE",
                    "That access code is already used by another staff member. Choose a different one.", "newAccessCode");
        }

        user.setAccessCode(newHash);
        userRepository.save(user);
        verified.token().setUsedAt(LocalDateTime.now(clock));
        tokenRepository.save(verified.token());
        loginAttemptService.recordSuccess(clientKey);

        try {
            emailSender.send(email, "Your RMShop access code was changed",
                    "Hi " + user.getFullName() + ",\n\nYour RMShop manager access code was just changed. "
                            + "If this wasn't you, contact your store owner immediately.",
                    "<p>Hi " + escapeHtml(user.getFullName()) + ",</p><p>Your RMShop manager access code was just changed. "
                            + "If this wasn't you, contact your store owner immediately.</p>");
        } catch (EmailSender.EmailDeliveryException e) {
            // The reset itself succeeded; the notice is a courtesy
            log.warn("Could not send access-code-changed notice", e);
        }
    }

    private record Verified(User user, PasswordResetToken token) {}

    private Verified checkToken(String email, String code) {
        ApiException invalid = new ApiException(HttpStatus.BAD_REQUEST, "RESET_CODE_INVALID",
                "There's no active code for this email. Check the email address or request a new code.", "code");
        User user = findActiveManager(email).orElseThrow(() -> invalid);
        PasswordResetToken token = tokenRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> invalid);

        LocalDateTime now = LocalDateTime.now(clock);
        if (token.isExpired(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RESET_CODE_EXPIRED",
                    "This code has expired (codes last " + CODE_LIFETIME.toMinutes() + " minutes). Request a new one.", "code");
        }
        if (!hashResetCode(user.getId(), code).equals(token.getCodeHash())) {
            token.setFailedAttempts(token.getFailedAttempts() + 1);
            int remaining = MAX_CODE_ATTEMPTS - token.getFailedAttempts();
            if (remaining <= 0) {
                token.setUsedAt(now);
                tokenRepository.save(token);
                throw new ApiException(HttpStatus.BAD_REQUEST, "RESET_CODE_LOCKED",
                        "Too many wrong codes. For your security this code has been cancelled. Request a new one.", "code")
                        .with("attemptsRemaining", 0);
            }
            tokenRepository.save(token);
            throw new ApiException(HttpStatus.BAD_REQUEST, "RESET_CODE_WRONG",
                    "That code doesn't match the one we emailed. " + remaining + (remaining == 1 ? " attempt" : " attempts") + " left.", "code")
                    .with("attemptsRemaining", remaining);
        }
        return new Verified(user, token);
    }

    private Optional<User> findActiveManager(String email) {
        return userRepository.findByEmail(email)
                .filter(u -> u.isActive() && u.getRole() == User.Role.MANAGER);
    }

    private void cancelOpenTokens(Long userId, LocalDateTime now) {
        for (PasswordResetToken open : tokenRepository.findByUserIdAndUsedAtIsNull(userId)) {
            open.setUsedAt(now);
            tokenRepository.save(open);
        }
    }

    // Applies to every email typed, account or not, so limits reveal nothing
    private void throttle(String email) {
        Deque<Instant> times = requestLog.computeIfAbsent(email, k -> new ArrayDeque<>());
        synchronized (times) {
            Instant now = clock.instant();
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(Duration.ofHours(1)))) {
                times.pollFirst();
            }
            if (!times.isEmpty()) {
                long sinceLast = Duration.between(times.peekLast(), now).toSeconds();
                if (sinceLast < RESEND_COOLDOWN.toSeconds()) {
                    long wait = RESEND_COOLDOWN.toSeconds() - sinceLast;
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RESET_TOO_SOON",
                            "A code was just sent. Please wait " + wait + " seconds before asking for another.", "email")
                            .with("retryAfterSeconds", wait);
                }
            }
            if (times.size() >= MAX_REQUESTS_PER_HOUR) {
                long wait = Math.max(1, Duration.between(now, times.peekFirst().plus(Duration.ofHours(1))).toSeconds());
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RESET_LIMIT_REACHED",
                        "Too many code requests for this email. Try again in " + ((wait + 59) / 60) + " minutes.", "email")
                        .with("retryAfterSeconds", wait);
            }
            times.addLast(now);
        }
    }

    static String hashResetCode(Long userId, String code) {
        return Hashing.sha256("reset:" + userId + ":" + code);
    }

    private static String resetEmailText(String name, String code) {
        return "Hi " + name + ",\n\n"
                + "Someone asked to reset the RMShop access code for your manager account.\n\n"
                + "Your confirmation code is: " + code + "\n\n"
                + "It expires in " + CODE_LIFETIME.toMinutes() + " minutes. "
                + "If you didn't ask for this, ignore this email - your access code has not changed.";
    }

    private static String resetEmailHtml(String name, String code) {
        return "<div style=\"font-family:Arial,sans-serif;max-width:480px\">"
                + "<p>Hi " + escapeHtml(name) + ",</p>"
                + "<p>Someone asked to reset the RMShop access code for your manager account.</p>"
                + "<p>Your confirmation code is:</p>"
                + "<p style=\"font-size:32px;font-weight:bold;letter-spacing:8px;font-family:monospace\">" + code + "</p>"
                + "<p>It expires in " + CODE_LIFETIME.toMinutes() + " minutes.</p>"
                + "<p style=\"color:#666\">If you didn't ask for this, ignore this email. Your access code has not changed.</p>"
                + "</div>";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
