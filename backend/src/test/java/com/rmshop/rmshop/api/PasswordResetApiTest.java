package com.rmshop.rmshop.api;

import com.rmshop.rmshop.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Black-box: the whole "Forgot access code?" journey through the HTTP API,
// reading the confirmation code from the (captured) inbox like a real manager.
class PasswordResetApiTest extends ApiTestBase {

    private static final String EMAIL = "manager@shop.co.zw";
    private static final String OLD_CODE = "482913";
    private static final String NEW_CODE = "730461";

    private User manager;

    @BeforeEach
    void givenManager() {
        manager = givenUser("Joy Manager", User.Role.MANAGER, OLD_CODE, EMAIL);
    }

    private Response forgot(String email) {
        return post("/api/auth/forgot-password", Map.of("email", email));
    }

    private Response verify(String email, String code) {
        return post("/api/auth/verify-reset-code", Map.of("email", email, "code", code));
    }

    private Response reset(String email, String code, String newCode, String confirm) {
        return post("/api/auth/reset-password",
                Map.of("email", email, "code", code, "newAccessCode", newCode, "confirmAccessCode", confirm));
    }

    private Response login(String code) {
        return post("/api/users/login", Map.of("accessCode", code));
    }

    @Test
    void managerResetsAForgottenCodeEndToEnd() {
        Response requested = forgot(EMAIL);
        assertThat(requested.status()).isEqualTo(200);
        assertThat(requested.get("expiresInSeconds")).isEqualTo(900);

        String code = mail.latestCodeFor(EMAIL);
        assertThat(verify(EMAIL, code).get("valid")).isEqualTo(true);

        Response done = reset(EMAIL, code, NEW_CODE, NEW_CODE);
        assertThat(done.status()).isEqualTo(200);

        assertThat(login(NEW_CODE).status()).isEqualTo(200);
        assertThat(login(OLD_CODE).status()).as("old code no longer works").isEqualTo(401);
        assertThat(mail.sentTo(EMAIL)).extracting("subject").contains("Your RMShop access code was changed");
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "\"\"                   | EMAIL_REQUIRED",
            "manager shop@gmail.com | EMAIL_HAS_SPACES",
            "managergmail.com       | EMAIL_MISSING_AT",
            "man@ager@gmail.com     | EMAIL_MULTIPLE_AT",
            "@gmail.com             | EMAIL_MISSING_NAME",
            "manager@               | EMAIL_MISSING_DOMAIN",
            "manager@gmailcom       | EMAIL_MISSING_DOT",
            "manager@gmail..com     | EMAIL_BAD_DOTS",
            "manager@gmail.c        | EMAIL_BAD_ENDING",
            "man<ager@gmail.com     | EMAIL_INVALID_CHARACTER",
            "manager@gmial.com      | EMAIL_POSSIBLE_TYPO",
    })
    void emailMistakesArePinpointed(String input, String expectedCode) {
        Response response = forgot(input);
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body()).containsEntry("code", expectedCode).containsEntry("field", "email");
        assertThat((String) response.get("message")).isNotBlank();
    }

    @Test
    void typoResponseCarriesTheSuggestedAddress() {
        assertThat(forgot("manager@gamil.com").get("suggestion")).isEqualTo("manager@gmail.com");
    }

    @Test
    void unknownEmailLooksExactlyLikeAKnownOne() {
        Response known = forgot(EMAIL);
        Response unknown = forgot("stranger@shop.co.zw");
        assertThat(unknown.status()).isEqualTo(known.status());
        assertThat(unknown.body()).isEqualTo(known.body());
        assertThat(mail.sentTo("stranger@shop.co.zw")).isEmpty();
    }

    @Test
    void cashiersCannotResetThroughEmail() {
        givenUser("Tino Cashier", User.Role.EMPLOYEE, "905172", "tino@shop.co.zw");
        assertThat(forgot("tino@shop.co.zw").status()).isEqualTo(200);
        assertThat(mail.sentTo("tino@shop.co.zw")).isEmpty();
    }

    @Test
    void askingAgainTooQuicklyIsRefusedWithAWait() {
        forgot(EMAIL);
        Response again = forgot(EMAIL);
        assertThat(again.status()).isEqualTo(429);
        assertThat(again.get("code")).isEqualTo("RESET_TOO_SOON");
        assertThat(((Number) again.get("retryAfterSeconds")).intValue()).isBetween(1, 60);

        clock.advance(Duration.ofSeconds(60));
        assertThat(forgot(EMAIL).status()).isEqualTo(200);
    }

    @Test
    void emailOutageIsExplained() {
        mail.failNextSend();
        Response response = forgot(EMAIL);
        assertThat(response.status()).isEqualTo(502);
        assertThat(response.get("code")).isEqualTo("EMAIL_SEND_FAILED");
    }

    @Test
    void wrongConfirmationCodesCountDownThenCancel() {
        forgot(EMAIL);
        String right = mail.latestCodeFor(EMAIL);
        String wrong = right.equals("000001") ? "000002" : "000001";

        for (int remaining = 4; remaining >= 1; remaining--) {
            Response response = verify(EMAIL, wrong);
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.body()).containsEntry("code", "RESET_CODE_WRONG").containsEntry("attemptsRemaining", remaining);
        }
        assertThat(verify(EMAIL, wrong).get("code")).isEqualTo("RESET_CODE_LOCKED");
        assertThat(verify(EMAIL, right).get("code")).isEqualTo("RESET_CODE_INVALID");
    }

    @Test
    void confirmationCodeExpiresAfterFifteenMinutes() {
        forgot(EMAIL);
        String code = mail.latestCodeFor(EMAIL);
        clock.advance(Duration.ofMinutes(15));
        assertThat(verify(EMAIL, code).get("code")).isEqualTo("RESET_CODE_EXPIRED");
    }

    @ParameterizedTest(name = "[{index}] code \"{0}\" -> {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "\"\"  | CODE_REQUIRED",
            "12345 | CODE_TOO_SHORT",
            "12a456| CODE_NOT_NUMERIC",
    })
    void confirmationCodeTyposArePinpointed(String input, String expectedCode) {
        Response response = verify(EMAIL, input);
        assertThat(response.body()).containsEntry("code", expectedCode).containsEntry("field", "code");
    }

    @Test
    void newCodeProblemsAreEachExplained() {
        forgot(EMAIL);
        String code = mail.latestCodeFor(EMAIL);

        assertThat(reset(EMAIL, code, NEW_CODE, "730462").body())
                .containsEntry("code", "CODES_DO_NOT_MATCH").containsEntry("field", "confirmAccessCode");
        assertThat(reset(EMAIL, code, "123456", "123456").get("code")).isEqualTo("CODE_TOO_SIMPLE");
        assertThat(reset(EMAIL, code, OLD_CODE, OLD_CODE).get("code")).isEqualTo("CODE_SAME_AS_OLD");

        givenUser("Tino Cashier", User.Role.EMPLOYEE, "905172", null);
        Response taken = reset(EMAIL, code, "905172", "905172");
        assertThat(taken.status()).isEqualTo(409);
        assertThat(taken.get("code")).isEqualTo("CODE_IN_USE");

        assertThat(reset(EMAIL, code, NEW_CODE, NEW_CODE).status()).as("code still valid after those mistakes").isEqualTo(200);
    }

    @Test
    void resetUnlocksALockedTerminal() {
        for (int i = 0; i < 5; i++) login("111222");
        assertThat(login(OLD_CODE).status()).isEqualTo(429);

        forgot(EMAIL);
        reset(EMAIL, mail.latestCodeFor(EMAIL), NEW_CODE, NEW_CODE);
        assertThat(login(NEW_CODE).status()).isEqualTo(200);
    }

    @Test
    void confirmationCodeIsSingleUse() {
        forgot(EMAIL);
        String code = mail.latestCodeFor(EMAIL);
        reset(EMAIL, code, NEW_CODE, NEW_CODE);
        assertThat(reset(EMAIL, code, "590137", "590137").get("code")).isEqualTo("RESET_CODE_INVALID");
    }

    @Test
    void brokenRequestBodyIsReported() {
        Response response = send("POST", "/api/auth/forgot-password", null, null);
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.get("code")).isEqualTo("BAD_REQUEST_BODY");
    }

    // ---- managers setting the email on the staff page ------------------------

    @Test
    void managerCanSetAStaffEmailAndItIsValidated() {
        User cashier = givenUser("Tino Cashier", User.Role.EMPLOYEE, "905172", null);
        String path = "/api/users/" + cashier.getId() + "/email";

        Response bad = send("PUT", path, Map.of("email", "tino.gmail.com"), manager.getId());
        assertThat(bad.body()).containsEntry("code", "EMAIL_MISSING_AT");

        Response taken = send("PUT", path, Map.of("email", EMAIL), manager.getId());
        assertThat(taken.status()).isEqualTo(409);
        assertThat(taken.get("code")).isEqualTo("EMAIL_IN_USE");

        Response ok = send("PUT", path, Map.of("email", " Tino@Shop.co.zw "), manager.getId());
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.get("email")).isEqualTo("tino@shop.co.zw");
    }

    @Test
    void onlyManagersCanSetEmails() {
        User cashier = givenUser("Tino Cashier", User.Role.EMPLOYEE, "905172", null);
        Response response = send("PUT", "/api/users/" + manager.getId() + "/email", Map.of("email", "evil@x.com"), cashier.getId());
        assertThat(response.status()).isEqualTo(403);
    }
}
