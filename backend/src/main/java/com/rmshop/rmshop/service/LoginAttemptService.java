package com.rmshop.rmshop.service;

import com.rmshop.rmshop.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Counts wrong access codes per client (device/network address). Login is by
// access code alone, so until a code matches there is no account to pin the
// failures on; the client is the only thing we can track.
//
// Attempts 1-4 get progressively stronger warnings, the 5th locks the client
// out. Each further lock lasts longer. Kept in memory: a server restart
// clears it, which is acceptable for a single small instance.
@Service
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    // How long a quiet period has to be before the failure count starts over
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    // Lock length for the 1st, 2nd, 3rd+ lock in a row
    static final Duration[] LOCK_DURATIONS = { Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(60) };
    // Lock history is forgotten after this long without trouble
    static final Duration LOCK_MEMORY = Duration.ofHours(24);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Clock clock;
    private final Map<String, ClientRecord> records = new ConcurrentHashMap<>();

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    private static final class ClientRecord {
        int failures;
        int lockCount;
        Instant lastFailure;
        Instant lockedUntil;
    }

    /** Throws ACCOUNT_LOCKED (429) if this client is currently locked out. */
    public void checkNotLocked(String clientKey) {
        ClientRecord record = records.get(clientKey);
        if (record == null) return;
        synchronized (record) {
            Instant now = clock.instant();
            if (record.lockedUntil != null && now.isBefore(record.lockedUntil)) {
                throw lockedError(record, now);
            }
        }
    }

    /**
     * Records a wrong code and throws the matching error: WRONG_ACCESS_CODE
     * (401) with a message that escalates per attempt, or ACCOUNT_LOCKED (429)
     * when this was the last allowed attempt.
     */
    public ApiException recordFailure(String clientKey) {
        cleanupIfLarge();
        ClientRecord record = records.computeIfAbsent(clientKey, k -> new ClientRecord());
        synchronized (record) {
            Instant now = clock.instant();
            if (record.lastFailure != null && Duration.between(record.lastFailure, now).compareTo(FAILURE_WINDOW) > 0) {
                record.failures = 0;
            }
            if (record.lastFailure != null && Duration.between(record.lastFailure, now).compareTo(LOCK_MEMORY) > 0) {
                record.lockCount = 0;
            }
            record.failures++;
            record.lastFailure = now;

            if (record.failures >= MAX_ATTEMPTS) {
                Duration lock = LOCK_DURATIONS[Math.min(record.lockCount, LOCK_DURATIONS.length - 1)];
                record.lockCount++;
                record.failures = 0;
                record.lockedUntil = now.plus(lock);
                return lockedError(record, now).with("attempt", MAX_ATTEMPTS);
            }
            return wrongCodeError(record.failures);
        }
    }

    public void recordSuccess(String clientKey) {
        records.remove(clientKey);
    }

    static ApiException wrongCodeError(int attempt) {
        int remaining = MAX_ATTEMPTS - attempt;
        String firstLockMinutes = String.valueOf(LOCK_DURATIONS[0].toMinutes());
        String message;
        String severity;
        switch (attempt) {
            case 1 -> {
                message = "That access code isn't right. Check the digits and try again.";
                severity = "info";
            }
            case 2 -> {
                message = "Wrong code again. You have " + remaining + " attempts left. "
                        + "Managers can use \"Forgot access code?\" to reset theirs; cashiers should ask their manager.";
                severity = "warning";
            }
            case 3 -> {
                message = "3 wrong attempts. " + remaining + " attempts left before this terminal is locked for "
                        + firstLockMinutes + " minutes.";
                severity = "warning";
            }
            default -> {
                message = "Last attempt! One more wrong code will lock this terminal for " + firstLockMinutes + " minutes.";
                severity = "danger";
            }
        }
        return new ApiException(HttpStatus.UNAUTHORIZED, "WRONG_ACCESS_CODE", message, "accessCode")
                .with("attempt", attempt)
                .with("attemptsRemaining", remaining)
                .with("maxAttempts", MAX_ATTEMPTS)
                .with("severity", severity);
    }

    private static ApiException lockedError(ClientRecord record, Instant now) {
        long seconds = Math.max(1, Duration.between(now, record.lockedUntil).toSeconds());
        long minutes = (seconds + 59) / 60;
        String wait = minutes <= 1 ? "about a minute" : minutes + " minutes";
        String repeat = record.lockCount > 1 ? " Repeated lockouts last longer each time." : "";
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED",
                "Too many wrong access codes. This terminal is locked. Try again in " + wait + "." + repeat)
                .with("lockedSeconds", seconds)
                .with("attemptsRemaining", 0)
                .with("maxAttempts", MAX_ATTEMPTS)
                .with("severity", "danger");
    }

    private void cleanupIfLarge() {
        if (records.size() < CLEANUP_THRESHOLD) return;
        Instant cutoff = clock.instant().minus(LOCK_MEMORY);
        records.entrySet().removeIf(e -> {
            ClientRecord r = e.getValue();
            return r.lastFailure == null || r.lastFailure.isBefore(cutoff);
        });
    }
}
