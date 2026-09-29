package com.rmshop.rmshop.api;

import com.rmshop.rmshop.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Black-box: POST /api/users/login as seen from the outside.
class LoginApiTest extends ApiTestBase {

    private static final String MANAGER_CODE = "482913";
    private static final String CASHIER_CODE = "905172";
    private static final String WRONG_CODE = "111222";

    @BeforeEach
    void givenStaff() {
        givenUser("Joy Manager", User.Role.MANAGER, MANAGER_CODE, "manager@shop.co.zw");
        givenUser("Tino Cashier", User.Role.EMPLOYEE, CASHIER_CODE, null);
    }

    private Response login(String code) {
        return post("/api/users/login", Map.of("accessCode", code));
    }

    @Test
    void correctCodeLogsInWithRole() {
        Response manager = login(MANAGER_CODE);
        assertThat(manager.status()).isEqualTo(200);
        assertThat(manager.get("fullName")).isEqualTo("Joy Manager");
        assertThat(manager.get("role")).isEqualTo("MANAGER");
        assertThat(manager.get("email")).isEqualTo("manager@shop.co.zw");
        assertThat(manager.body()).doesNotContainKey("accessCode");

        assertThat(login(CASHIER_CODE).get("role")).isEqualTo("EMPLOYEE");
    }

    @Test
    void eachWrongAttemptGetsADifferentMessageThenTheFifthLocks() {
        Response first = login(WRONG_CODE);
        Response second = login(WRONG_CODE);
        Response third = login(WRONG_CODE);
        Response fourth = login(WRONG_CODE);
        Response fifth = login(WRONG_CODE);

        assertThat(first.status()).isEqualTo(401);
        assertThat(first.body()).containsEntry("code", "WRONG_ACCESS_CODE").containsEntry("attempt", 1)
                .containsEntry("attemptsRemaining", 4).containsEntry("severity", "info").containsEntry("field", "accessCode");
        assertThat(second.body()).containsEntry("attempt", 2).containsEntry("attemptsRemaining", 3).containsEntry("severity", "warning");
        assertThat(third.body()).containsEntry("attempt", 3).containsEntry("attemptsRemaining", 2);
        assertThat(fourth.body()).containsEntry("attempt", 4).containsEntry("attemptsRemaining", 1).containsEntry("severity", "danger");

        assertThat(fifth.status()).isEqualTo(429);
        assertThat(fifth.body()).containsEntry("code", "ACCOUNT_LOCKED").containsEntry("lockedSeconds", 300);

        assertThat(first.get("message")).isNotEqualTo(second.get("message"));
        assertThat(second.get("message")).isNotEqualTo(third.get("message"));
        assertThat(third.get("message")).isNotEqualTo(fourth.get("message"));
    }

    @Test
    void lockedTerminalRefusesEvenTheCorrectCodeUntilTheLockEnds() {
        for (int i = 0; i < 5; i++) login(WRONG_CODE);

        Response whileLocked = login(MANAGER_CODE);
        assertThat(whileLocked.status()).isEqualTo(429);
        assertThat(whileLocked.get("code")).isEqualTo("ACCOUNT_LOCKED");

        clock.advance(Duration.ofMinutes(5));
        assertThat(login(MANAGER_CODE).status()).isEqualTo(200);
    }

    @Test
    void successfulLoginResetsTheAttemptCount() {
        login(WRONG_CODE);
        login(WRONG_CODE);
        assertThat(login(CASHIER_CODE).status()).isEqualTo(200);
        assertThat(login(WRONG_CODE).get("attempt")).isEqualTo(1);
    }

    @Test
    void anotherDeviceIsNotAffectedByALockout() {
        for (int i = 0; i < 5; i++) login(WRONG_CODE);
        clientIp = "198.51.100.77";
        assertThat(login(MANAGER_CODE).status()).isEqualTo(200);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "\"\"      | CODE_REQUIRED",
            "\"48 913\"| CODE_HAS_SPACES",
            "48291a    | CODE_NOT_NUMERIC",
            "4829      | CODE_TOO_SHORT",
            "4829134   | CODE_TOO_LONG",
    })
    void mistypedCodesAreExplainedAndDoNotCountAsAttempts(String input, String expectedCode) {
        Response response = login(input);
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).containsEntry("code", expectedCode).containsEntry("field", "accessCode");

        assertThat(login(WRONG_CODE).get("attempt")).as("typos are not counted").isEqualTo(1);
    }

    @Test
    void missingBodyFieldIsReported() {
        Response response = post("/api/users/login", Map.of());
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.get("code")).isEqualTo("CODE_REQUIRED");
    }

    @Test
    void deactivatedAccountIsToldSoAndItDoesNotCount() {
        User gone = givenUser("Former Staff", User.Role.EMPLOYEE, "730461", null);
        gone.setActive(false);
        userRepository.save(gone);

        Response response = login("730461");
        assertThat(response.status()).isEqualTo(403);
        assertThat(response.get("code")).isEqualTo("ACCOUNT_DEACTIVATED");
        assertThat(login(WRONG_CODE).get("attempt")).isEqualTo(1);
    }

    @Test
    void loginIsRecordedInAttendance() {
        Response response = login(CASHIER_CODE);
        long id = ((Number) response.get("id")).longValue();
        assertThat(attendanceLogRepository.findByEmployeeIdOrderByLoginTimeDesc(id)).hasSize(1);
    }
}
