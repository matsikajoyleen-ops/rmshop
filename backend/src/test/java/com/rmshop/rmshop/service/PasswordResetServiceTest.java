package com.rmshop.rmshop.service;

import com.rmshop.rmshop.exception.ApiException;
import com.rmshop.rmshop.model.PasswordResetToken;
import com.rmshop.rmshop.model.User;
import com.rmshop.rmshop.repository.PasswordResetTokenRepository;
import com.rmshop.rmshop.repository.UserRepository;
import com.rmshop.rmshop.support.CapturingEmailSender;
import com.rmshop.rmshop.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// White-box: repositories are mocked so each internal branch of
// PasswordResetService can be forced and its side effects inspected.
class PasswordResetServiceTest {

    private static final String EMAIL = "manager@shop.co.zw";
    private static final String OLD_CODE = "482913";

    private UserRepository users;
    private PasswordResetTokenRepository tokens;
    private LoginAttemptService loginAttempts;
    private CapturingEmailSender mail;
    private MutableClock clock;
    private PasswordResetService service;
    private User manager;
    private final List<PasswordResetToken> savedTokens = new ArrayList<>();

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        tokens = mock(PasswordResetTokenRepository.class);
        loginAttempts = mock(LoginAttemptService.class);
        mail = new CapturingEmailSender();
        clock = new MutableClock(Instant.parse("2026-09-29T08:00:00Z"));
        service = new PasswordResetService(users, tokens, mail, loginAttempts, clock);

        manager = new User("Joy Manager", User.Role.MANAGER, UserService.hashAccessCode(OLD_CODE));
        manager.setId(1L);
        manager.setEmail(EMAIL);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(manager));
        when(tokens.save(any())).thenAnswer(inv -> {
            PasswordResetToken t = inv.getArgument(0);
            if (!savedTokens.contains(t)) savedTokens.add(t);
            return t;
        });
        when(tokens.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(anyLong())).thenAnswer(inv ->
                savedTokens.stream().filter(t -> t.getUserId().equals(inv.getArgument(0)) && !t.isUsed())
                        .reduce((a, b) -> b));
        when(tokens.findByUserIdAndUsedAtIsNull(anyLong())).thenAnswer(inv ->
                savedTokens.stream().filter(t -> t.getUserId().equals(inv.getArgument(0)) && !t.isUsed()).toList());
    }

    // ---- step 1: request -----------------------------------------------------

    @Test
    void managerGetsAnEmailWithACodeAndOnlyTheHashIsStored() {
        PasswordResetService.ResetRequestResult result = service.requestReset(EMAIL);

        String code = mail.latestCodeFor(EMAIL);
        assertThat(code).matches("\\d{6}");
        assertThat(savedTokens).hasSize(1);
        PasswordResetToken token = savedTokens.get(0);
        assertThat(token.getCodeHash()).isEqualTo(PasswordResetService.hashResetCode(1L, code)).doesNotContain(code);
        assertThat(token.getExpiresAt()).isEqualTo(LocalDateTime.now(clock).plusMinutes(15));
        assertThat(result.message()).isEqualTo(PasswordResetService.REQUEST_ACCEPTED_MESSAGE);
        assertThat(result.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void unknownEmailGetsTheSameAnswerButNoEmail() {
        PasswordResetService.ResetRequestResult result = service.requestReset("nobody@shop.co.zw");
        assertThat(result.message()).isEqualTo(PasswordResetService.REQUEST_ACCEPTED_MESSAGE);
        assertThat(mail.sentTo("nobody@shop.co.zw")).isEmpty();
        verify(tokens, never()).save(any());
    }

    @Test
    void cashierEmailGetsNoCode() {
        User cashier = new User("Cashier", User.Role.EMPLOYEE, "x");
        cashier.setId(2L);
        when(users.findByEmail("cashier@shop.co.zw")).thenReturn(Optional.of(cashier));
        service.requestReset("cashier@shop.co.zw");
        assertThat(mail.sentTo("cashier@shop.co.zw")).isEmpty();
    }

    @Test
    void deactivatedManagerGetsNoCode() {
        manager.setActive(false);
        service.requestReset(EMAIL);
        assertThat(mail.sentTo(EMAIL)).isEmpty();
    }

    @Test
    void emailIsNormalisedBeforeLookup() {
        service.requestReset("  Manager@Shop.CO.ZW ");
        assertThat(mail.sentTo(EMAIL)).hasSize(1);
    }

    @Test
    void newRequestCancelsTheOlderCode() {
        service.requestReset(EMAIL);
        clock.advance(PasswordResetService.RESEND_COOLDOWN);
        service.requestReset(EMAIL);
        assertThat(savedTokens).hasSize(2);
        assertThat(savedTokens.get(0).isUsed()).isTrue();
        assertThat(savedTokens.get(1).isUsed()).isFalse();
    }

    @Test
    void secondRequestWithinCooldownIsRefusedWithWaitTime() {
        service.requestReset(EMAIL);
        clock.advance(Duration.ofSeconds(20));
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.requestReset(EMAIL));
        assertThat(error.getCode()).isEqualTo("RESET_TOO_SOON");
        assertThat(error.getDetails()).containsEntry("retryAfterSeconds", 40L);
    }

    @Test
    void cooldownAlsoAppliesToUnknownEmailsSoItRevealsNothing() {
        service.requestReset("nobody@shop.co.zw");
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.requestReset("nobody@shop.co.zw"));
        assertThat(error.getCode()).isEqualTo("RESET_TOO_SOON");
    }

    @Test
    void hourlyLimitApplies() {
        for (int i = 0; i < PasswordResetService.MAX_REQUESTS_PER_HOUR; i++) {
            service.requestReset(EMAIL);
            clock.advance(PasswordResetService.RESEND_COOLDOWN);
        }
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.requestReset(EMAIL));
        assertThat(error.getCode()).isEqualTo("RESET_LIMIT_REACHED");

        clock.advance(Duration.ofHours(1));
        assertThatCode(() -> service.requestReset(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void emailOutageIsReportedAsItsOwnError() {
        mail.failNextSend();
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.requestReset(EMAIL));
        assertThat(error.getCode()).isEqualTo("EMAIL_SEND_FAILED");
        assertThat(error.getStatus().value()).isEqualTo(502);
    }

    @Test
    void badEmailFormatIsRejectedBeforeAnyLookup() {
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.requestReset("manager.shop.co.zw"));
        assertThat(error.getCode()).isEqualTo("EMAIL_MISSING_AT");
        verifyNoInteractions(users);
    }

    // ---- step 2: verify ------------------------------------------------------

    @Test
    void correctCodeVerifies() {
        service.requestReset(EMAIL);
        assertThatCode(() -> service.verifyCode(EMAIL, mail.latestCodeFor(EMAIL))).doesNotThrowAnyException();
    }

    @Test
    void wrongCodeCountsDownThenCancelsTheCode() {
        service.requestReset(EMAIL);
        String wrong = otherThan(mail.latestCodeFor(EMAIL));

        for (int remaining = 4; remaining >= 1; remaining--) {
            ApiException error = catchThrowableOfType(ApiException.class, () -> service.verifyCode(EMAIL, wrong));
            assertThat(error.getCode()).isEqualTo("RESET_CODE_WRONG");
            assertThat(error.getDetails()).containsEntry("attemptsRemaining", remaining);
        }
        ApiException locked = catchThrowableOfType(ApiException.class, () -> service.verifyCode(EMAIL, wrong));
        assertThat(locked.getCode()).isEqualTo("RESET_CODE_LOCKED");

        ApiException afterwards = catchThrowableOfType(ApiException.class,
                () -> service.verifyCode(EMAIL, mail.latestCodeFor(EMAIL)));
        assertThat(afterwards.getCode()).as("even the right code is now refused").isEqualTo("RESET_CODE_INVALID");
    }

    @Test
    void lastAttemptMessageIsSingular() {
        service.requestReset(EMAIL);
        String wrong = otherThan(mail.latestCodeFor(EMAIL));
        ApiException last = null;
        for (int i = 0; i < 4; i++) last = catchThrowableOfType(ApiException.class, () -> service.verifyCode(EMAIL, wrong));
        assertThat(last.getMessage()).endsWith("1 attempt left.");
    }

    @Test
    void expiredCodeIsRefused() {
        service.requestReset(EMAIL);
        clock.advance(PasswordResetService.CODE_LIFETIME);
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.verifyCode(EMAIL, mail.latestCodeFor(EMAIL)));
        assertThat(error.getCode()).isEqualTo("RESET_CODE_EXPIRED");
    }

    @Test
    void codeWorksUntilJustBeforeExpiry() {
        service.requestReset(EMAIL);
        clock.advance(PasswordResetService.CODE_LIFETIME.minusSeconds(1));
        assertThatCode(() -> service.verifyCode(EMAIL, mail.latestCodeFor(EMAIL))).doesNotThrowAnyException();
    }

    @Test
    void noActiveCodeForUnknownEmail() {
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.verifyCode("nobody@shop.co.zw", "482913"));
        assertThat(error.getCode()).isEqualTo("RESET_CODE_INVALID");
    }

    @Test
    void oldCodeStopsWorkingOnceANewOneIsSent() {
        service.requestReset(EMAIL);
        String first = mail.latestCodeFor(EMAIL);
        clock.advance(PasswordResetService.RESEND_COOLDOWN);
        service.requestReset(EMAIL);
        String second = mail.latestCodeFor(EMAIL);
        if (!first.equals(second)) {
            ApiException error = catchThrowableOfType(ApiException.class, () -> service.verifyCode(EMAIL, first));
            assertThat(error.getCode()).isEqualTo("RESET_CODE_WRONG");
        }
        assertThatCode(() -> service.verifyCode(EMAIL, second)).doesNotThrowAnyException();
    }

    // ---- step 3: reset -------------------------------------------------------

    @Test
    void resetStoresTheNewHashMarksTheCodeUsedAndClearsLockout() {
        service.requestReset(EMAIL);
        String code = mail.latestCodeFor(EMAIL);

        service.resetAccessCode(EMAIL, code, "905172", "905172", "10.0.0.9");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getAccessCode()).isEqualTo(UserService.hashAccessCode("905172"));
        assertThat(savedTokens.get(0).isUsed()).isTrue();
        verify(loginAttempts).recordSuccess("10.0.0.9");
        assertThat(mail.sentTo(EMAIL)).extracting(CapturingEmailSender.SentEmail::subject)
                .contains("Your RMShop access code was changed");
    }

    @Test
    void codeCannotBeUsedTwice() {
        service.requestReset(EMAIL);
        String code = mail.latestCodeFor(EMAIL);
        service.resetAccessCode(EMAIL, code, "905172", "905172", "ip");
        ApiException error = catchThrowableOfType(ApiException.class,
                () -> service.resetAccessCode(EMAIL, code, "905173", "905173", "ip"));
        assertThat(error.getCode()).isEqualTo("RESET_CODE_INVALID");
    }

    @Test
    void newCodeMustDifferFromOld() {
        service.requestReset(EMAIL);
        ApiException error = catchThrowableOfType(ApiException.class,
                () -> service.resetAccessCode(EMAIL, mail.latestCodeFor(EMAIL), OLD_CODE, OLD_CODE, "ip"));
        assertThat(error.getCode()).isEqualTo("CODE_SAME_AS_OLD");
    }

    @Test
    void newCodeMustNotBelongToSomeoneElse() {
        when(users.existsByAccessCode(UserService.hashAccessCode("905172"))).thenReturn(true);
        service.requestReset(EMAIL);
        ApiException error = catchThrowableOfType(ApiException.class,
                () -> service.resetAccessCode(EMAIL, mail.latestCodeFor(EMAIL), "905172", "905172", "ip"));
        assertThat(error.getCode()).isEqualTo("CODE_IN_USE");
        assertThat(error.getStatus().value()).isEqualTo(409);
    }

    @Test
    void mismatchedNewCodesDoNotUseUpACodeAttempt() {
        service.requestReset(EMAIL);
        String code = mail.latestCodeFor(EMAIL);
        ApiException error = catchThrowableOfType(ApiException.class,
                () -> service.resetAccessCode(EMAIL, code, "905172", "905173", "ip"));
        assertThat(error.getCode()).isEqualTo("CODES_DO_NOT_MATCH");
        assertThat(savedTokens.get(0).getFailedAttempts()).isZero();
        verify(users, never()).save(any());
    }

    @Test
    void failedChangeNoticeDoesNotUndoTheReset() {
        service.requestReset(EMAIL);
        String code = mail.latestCodeFor(EMAIL);
        mail.failNextSend();
        assertThatCode(() -> service.resetAccessCode(EMAIL, code, "905172", "905172", "ip")).doesNotThrowAnyException();
        verify(users).save(any());
    }

    @Test
    void wrongCodeAtResetStepAlsoCounts() {
        service.requestReset(EMAIL);
        String wrong = otherThan(mail.latestCodeFor(EMAIL));
        ApiException error = catchThrowableOfType(ApiException.class,
                () -> service.resetAccessCode(EMAIL, wrong, "905172", "905172", "ip"));
        assertThat(error.getCode()).isEqualTo("RESET_CODE_WRONG");
        assertThat(savedTokens.get(0).getFailedAttempts()).isEqualTo(1);
        verify(users, never()).save(any());
        verify(loginAttempts, never()).recordSuccess(anyString());
    }

    private static String otherThan(String code) {
        return code.equals("000001") ? "000002" : "000001";
    }
}
