package com.rmshop.rmshop.validation;

import com.rmshop.rmshop.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

// White-box: one case per branch of InputValidator, in the order the checks run.
class InputValidatorTest {

    // ---- email -------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "\"\"                         | EMAIL_REQUIRED",
            "\"   \"                      | EMAIL_REQUIRED",
            "joy leen@gmail.com           | EMAIL_HAS_SPACES",
            "joyleen.gmail.com            | EMAIL_MISSING_AT",
            "joy@leen@gmail.com           | EMAIL_MULTIPLE_AT",
            "@gmail.com                   | EMAIL_MISSING_NAME",
            "joyleen@                     | EMAIL_MISSING_DOMAIN",
            "joy#leen@gmail.com           | EMAIL_INVALID_CHARACTER",
            "joyleen@gma_il.com           | EMAIL_INVALID_CHARACTER",
            "joyleen@gmailcom             | EMAIL_MISSING_DOT",
            ".joyleen@gmail.com           | EMAIL_BAD_DOTS",
            "joyleen.@gmail.com           | EMAIL_BAD_DOTS",
            "joy..leen@gmail.com          | EMAIL_BAD_DOTS",
            "joyleen@.gmail.com           | EMAIL_BAD_DOTS",
            "joyleen@gmail.com.           | EMAIL_BAD_DOTS",
            "joyleen@gmail..com           | EMAIL_BAD_DOTS",
            "joyleen@gmail.c              | EMAIL_BAD_ENDING",
            "joyleen@gmail.c0m            | EMAIL_BAD_ENDING",
            "joyleen@gmial.com            | EMAIL_POSSIBLE_TYPO",
            "joyleen@gmail.con            | EMAIL_POSSIBLE_TYPO",
    })
    void emailMistakesAreIdentified(String input, String expectedCode) {
        ApiException error = catchThrowableOfType(ApiException.class, () -> InputValidator.validateEmail(input));
        assertThat(error).as("expected an error for \"%s\"", input).isNotNull();
        assertThat(error.getCode()).isEqualTo(expectedCode);
        assertThat(error.getField()).isEqualTo("email");
        assertThat(error.getStatus().value()).isEqualTo(400);
    }

    @Test
    void nullEmailIsRequired() {
        assertThatThrownBy(() -> InputValidator.validateEmail(null))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("EMAIL_REQUIRED");
    }

    @Test
    void emailThatIsTooLongIsRejected() {
        String longEmail = "a".repeat(250) + "@x.com";
        ApiException error = catchThrowableOfType(ApiException.class, () -> InputValidator.validateEmail(longEmail));
        assertThat(error.getCode()).isEqualTo("EMAIL_TOO_LONG");
    }

    @Test
    void invalidCharacterIsNamedInTheError() {
        ApiException error = catchThrowableOfType(ApiException.class, () -> InputValidator.validateEmail("joy!leen@gmail.com"));
        assertThat(error.getDetails()).containsEntry("character", "!");
        assertThat(error.getMessage()).contains("\"!\"");
    }

    @Test
    void typoSuggestsTheCorrectedAddress() {
        ApiException error = catchThrowableOfType(ApiException.class, () -> InputValidator.validateEmail("Joy@Gamil.com"));
        assertThat(error.getDetails()).containsEntry("suggestion", "joy@gmail.com");
    }

    @ParameterizedTest
    @ValueSource(strings = { "joy@gmail.com", "joy.leen+shop@store.co.zw", "o'neil@mail.example.org", "a-b_c%d@sub-domain.io" })
    void validEmailsPass(String email) {
        assertThat(InputValidator.validateEmail(email)).isEqualTo(email.toLowerCase());
    }

    @Test
    void emailIsTrimmedAndLowercased() {
        assertThat(InputValidator.validateEmail("  Joy@Gmail.COM ")).isEqualTo("joy@gmail.com");
    }

    // ---- 6-digit codes -----------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    void missingCodeIsRequired(String input) {
        assertCodeError(input, "CODE_REQUIRED");
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "\"12 456\" | CODE_HAS_SPACES",
            "\"123456 \"| CODE_HAS_SPACES",
            "12a456     | CODE_NOT_NUMERIC",
            "12-456     | CODE_NOT_NUMERIC",
            "١٢٣٤٥٦     | CODE_NOT_NUMERIC",
            "1234       | CODE_TOO_SHORT",
            "1          | CODE_TOO_SHORT",
            "1234567    | CODE_TOO_LONG",
    })
    void codeMistakesAreIdentified(String input, String expectedCode) {
        assertCodeError(input, expectedCode);
    }

    @Test
    void shortCodeSaysHowManyDigitsAreMissing() {
        ApiException error = catchThrowableOfType(ApiException.class,
                () -> InputValidator.validateSixDigitCode("1234", "accessCode", "access code"));
        assertThat(error.getMessage()).isEqualTo("Your access code has only 4 digits. Enter 2 more.");
        assertThat(error.getDetails()).containsEntry("digitsEntered", 4);
    }

    @Test
    void singleDigitMessageIsSingular() {
        ApiException error = catchThrowableOfType(ApiException.class,
                () -> InputValidator.validateSixDigitCode("7", "accessCode", "access code"));
        assertThat(error.getMessage()).contains("only 1 digit.");
    }

    @Test
    void validCodePasses() {
        assertThat(InputValidator.validateSixDigitCode("048213", "accessCode", "access code")).isEqualTo("048213");
    }

    // ---- choosing a new access code ----------------------------------------

    @Test
    void confirmationIsRequired() {
        assertNewCodeError("482913", "", "CONFIRM_REQUIRED", "confirmAccessCode");
    }

    @Test
    void confirmationMustMatch() {
        assertNewCodeError("482913", "482914", "CODES_DO_NOT_MATCH", "confirmAccessCode");
    }

    @ParameterizedTest
    @ValueSource(strings = { "000000", "777777", "123456", "345678", "987654", "654321" })
    void predictableCodesAreRefused(String code) {
        assertNewCodeError(code, code, "CODE_TOO_SIMPLE", "newAccessCode");
    }

    @Test
    void badNewCodeFormatIsReportedOnTheNewCodeField() {
        assertNewCodeError("12a", "12a", "CODE_NOT_NUMERIC", "newAccessCode");
    }

    @ParameterizedTest
    @ValueSource(strings = { "482913", "135792", "120000", "908172" })
    void reasonableCodesAreAccepted(String code) {
        assertThat(InputValidator.validateNewAccessCode(code, code)).isEqualTo(code);
    }

    @Test
    void tooSimpleDetection() {
        assertThat(InputValidator.isTooSimple("111111")).isTrue();
        assertThat(InputValidator.isTooSimple("012345")).isTrue();
        assertThat(InputValidator.isTooSimple("543210")).isTrue();
        assertThat(InputValidator.isTooSimple("123457")).isFalse();
        assertThat(InputValidator.isTooSimple("112233")).isFalse();
    }

    private static void assertCodeError(String input, String expectedCode) {
        ApiException error = catchThrowableOfType(ApiException.class,
                () -> InputValidator.validateSixDigitCode(input, "accessCode", "access code"));
        assertThat(error).as("expected an error for \"%s\"", input).isNotNull();
        assertThat(error.getCode()).isEqualTo(expectedCode);
        assertThat(error.getField()).isEqualTo("accessCode");
    }

    private static void assertNewCodeError(String code, String confirm, String expectedCode, String expectedField) {
        ApiException error = catchThrowableOfType(ApiException.class, () -> InputValidator.validateNewAccessCode(code, confirm));
        assertThat(error).isNotNull();
        assertThat(error.getCode()).isEqualTo(expectedCode);
        assertThat(error.getField()).isEqualTo(expectedField);
    }
}
