package com.rmshop.rmshop.service;

import com.rmshop.rmshop.exception.ApiException;
import com.rmshop.rmshop.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

// White-box: drives LoginAttemptService's counters and lock timers directly.
class LoginAttemptServiceTest {

    private static final String CLIENT = "10.0.0.1";

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-29T08:00:00Z"));
        service = new LoginAttemptService(clock);
    }

    @Test
    void eachWrongAttemptGetsItsOwnMessageAndSeverity() {
        ApiException first = service.recordFailure(CLIENT);
        ApiException second = service.recordFailure(CLIENT);
        ApiException third = service.recordFailure(CLIENT);
        ApiException fourth = service.recordFailure(CLIENT);

        assertThat(first.getDetails()).containsEntry("attempt", 1).containsEntry("attemptsRemaining", 4).containsEntry("severity", "info");
        assertThat(second.getDetails()).containsEntry("attempt", 2).containsEntry("attemptsRemaining", 3).containsEntry("severity", "warning");
        assertThat(third.getDetails()).containsEntry("attempt", 3).containsEntry("attemptsRemaining", 2).containsEntry("severity", "warning");
        assertThat(fourth.getDetails()).containsEntry("attempt", 4).containsEntry("attemptsRemaining", 1).containsEntry("severity", "danger");

        assertThat(first.getMessage()).startsWith("That access code isn't right");
        assertThat(second.getMessage()).contains("Forgot access code?");
        assertThat(third.getMessage()).contains("locked for 5 minutes");
        assertThat(fourth.getMessage()).startsWith("Last attempt!");
        assertThat(first.getStatus().value()).isEqualTo(401);
    }

    @Test
    void fifthWrongAttemptLocksForFiveMinutes() {
        failTimes(4);
        ApiException locked = service.recordFailure(CLIENT);

        assertThat(locked.getCode()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(locked.getStatus().value()).isEqualTo(429);
        assertThat(locked.getDetails()).containsEntry("lockedSeconds", 300L);
        assertLocked();

        clock.advance(Duration.ofMinutes(4).plusSeconds(59));
        assertLocked();

        clock.advance(Duration.ofSeconds(1));
        assertThatCode(() -> service.checkNotLocked(CLIENT)).doesNotThrowAnyException();
    }

    @Test
    void lockMessageCountsDownRemainingTime() {
        failTimes(5);
        clock.advance(Duration.ofMinutes(3));
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.checkNotLocked(CLIENT));
        assertThat(error.getDetails()).containsEntry("lockedSeconds", 120L);
        assertThat(error.getMessage()).contains("2 minutes");
    }

    @Test
    void repeatedLockoutsLastLonger() {
        failTimes(5);
        clock.advance(Duration.ofMinutes(5));

        ApiException secondLock = failTimes(5);
        assertThat(secondLock.getDetails()).containsEntry("lockedSeconds", 900L);
        assertThat(secondLock.getMessage()).contains("Repeated lockouts last longer");
        clock.advance(Duration.ofMinutes(15));

        ApiException thirdLock = failTimes(5);
        assertThat(thirdLock.getDetails()).containsEntry("lockedSeconds", 3600L);
        clock.advance(Duration.ofMinutes(60));

        ApiException fourthLock = failTimes(5);
        assertThat(fourthLock.getDetails()).as("capped at the longest lock").containsEntry("lockedSeconds", 3600L);
    }

    @Test
    void successfulLoginClearsTheCount() {
        failTimes(3);
        service.recordSuccess(CLIENT);
        assertThat(service.recordFailure(CLIENT).getDetails()).containsEntry("attempt", 1);
    }

    @Test
    void countStartsOverAfterAQuietPeriod() {
        failTimes(3);
        clock.advance(LoginAttemptService.FAILURE_WINDOW.plusSeconds(1));
        assertThat(service.recordFailure(CLIENT).getDetails()).containsEntry("attempt", 1);
    }

    @Test
    void countDoesNotResetWithinTheWindow() {
        failTimes(3);
        clock.advance(LoginAttemptService.FAILURE_WINDOW);
        assertThat(service.recordFailure(CLIENT).getDetails()).containsEntry("attempt", 4);
    }

    @Test
    void lockHistoryIsForgottenAfterADay() {
        failTimes(5);
        clock.advance(LoginAttemptService.LOCK_MEMORY.plusMinutes(1));
        ApiException lock = failTimes(5);
        assertThat(lock.getDetails()).as("back to the first, shortest lock").containsEntry("lockedSeconds", 300L);
    }

    @Test
    void clientsAreTrackedSeparately() {
        failTimes(5);
        assertThatCode(() -> service.checkNotLocked("10.0.0.2")).doesNotThrowAnyException();
        assertThat(service.recordFailure("10.0.0.2").getDetails()).containsEntry("attempt", 1);
    }

    @Test
    void unknownClientIsNotLocked() {
        assertThatCode(() -> service.checkNotLocked("never-seen")).doesNotThrowAnyException();
    }

    private ApiException failTimes(int times) {
        ApiException last = null;
        for (int i = 0; i < times; i++) last = service.recordFailure(CLIENT);
        return last;
    }

    private void assertLocked() {
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.checkNotLocked(CLIENT));
        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo("ACCOUNT_LOCKED");
    }
}
