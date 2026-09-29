package com.rmshop.rmshop.validation;

import com.rmshop.rmshop.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.regex.Pattern;

// Checks user input and, when it is wrong, says exactly what is wrong and
// where, so the UI can point at the problem instead of a generic "invalid".
// Checks run from the most basic mistake to the most specific one; the first
// failing check wins.
public final class InputValidator {

    public static final int ACCESS_CODE_LENGTH = 6;
    public static final int MAX_EMAIL_LENGTH = 254;

    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private static final Pattern LOCAL_PART_CHARS = Pattern.compile("[A-Za-z0-9._%+'-]");
    private static final Pattern DOMAIN_CHARS = Pattern.compile("[A-Za-z0-9.-]");
    private static final Pattern TOP_LEVEL_DOMAIN = Pattern.compile("[A-Za-z]{2,}");

    // Misspellings of popular providers that are almost never real domains
    private static final Map<String, String> DOMAIN_TYPOS = Map.ofEntries(
            Map.entry("gmial.com", "gmail.com"), Map.entry("gmai.com", "gmail.com"),
            Map.entry("gamil.com", "gmail.com"), Map.entry("gmail.co", "gmail.com"),
            Map.entry("gmail.con", "gmail.com"), Map.entry("gmal.com", "gmail.com"),
            Map.entry("gnail.com", "gmail.com"), Map.entry("gmail.cm", "gmail.com"),
            Map.entry("yaho.com", "yahoo.com"), Map.entry("yahoo.con", "yahoo.com"),
            Map.entry("hotmial.com", "hotmail.com"), Map.entry("hotmail.con", "hotmail.com"),
            Map.entry("hotmal.com", "hotmail.com"), Map.entry("outlok.com", "outlook.com"),
            Map.entry("outlook.con", "outlook.com"), Map.entry("iclod.com", "icloud.com"));

    private InputValidator() {}

    /** Returns the email trimmed and lowercased, or throws describing the first problem found. */
    public static String validateEmail(String raw) {
        if (raw == null || raw.isBlank()) {
            throw emailError("EMAIL_REQUIRED", "Enter your email address.");
        }
        String email = raw.strip();
        if (WHITESPACE.matcher(email).find()) {
            throw emailError("EMAIL_HAS_SPACES", "Email addresses can't contain spaces. Remove the space and try again.");
        }
        if (email.length() > MAX_EMAIL_LENGTH) {
            throw emailError("EMAIL_TOO_LONG", "That email is too long (max " + MAX_EMAIL_LENGTH + " characters).");
        }
        int at = email.indexOf('@');
        if (at < 0) {
            throw emailError("EMAIL_MISSING_AT", "Your email is missing the @ symbol, e.g. name@gmail.com.");
        }
        if (email.indexOf('@', at + 1) >= 0) {
            throw emailError("EMAIL_MULTIPLE_AT", "Your email has more than one @ symbol. It should have exactly one.");
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        if (local.isEmpty()) {
            throw emailError("EMAIL_MISSING_NAME", "Add your name part before the @, e.g. name@gmail.com.");
        }
        if (domain.isEmpty()) {
            throw emailError("EMAIL_MISSING_DOMAIN", "Add the provider after the @, e.g. @gmail.com.");
        }
        String badLocalChar = firstCharNotMatching(local, LOCAL_PART_CHARS);
        if (badLocalChar != null) {
            throw emailError("EMAIL_INVALID_CHARACTER", "The character \"" + badLocalChar + "\" isn't allowed in an email address.")
                    .with("character", badLocalChar);
        }
        String badDomainChar = firstCharNotMatching(domain, DOMAIN_CHARS);
        if (badDomainChar != null) {
            throw emailError("EMAIL_INVALID_CHARACTER", "The character \"" + badDomainChar + "\" isn't allowed after the @.")
                    .with("character", badDomainChar);
        }
        if (!domain.contains(".")) {
            throw emailError("EMAIL_MISSING_DOT", "The part after the @ needs a dot, e.g. gmail.com.");
        }
        if (local.startsWith(".") || local.endsWith(".") || local.contains("..")
                || domain.startsWith(".") || domain.endsWith(".") || domain.contains("..")) {
            throw emailError("EMAIL_BAD_DOTS", "Check the dots in your email: it can't start or end with a dot or have two dots in a row.");
        }
        String topLevel = domain.substring(domain.lastIndexOf('.') + 1);
        if (!TOP_LEVEL_DOMAIN.matcher(topLevel).matches()) {
            throw emailError("EMAIL_BAD_ENDING", "The ending \"." + topLevel + "\" doesn't look right. It should be something like .com or .co.zw.");
        }
        String normalized = email.toLowerCase();
        String suggestion = DOMAIN_TYPOS.get(domain.toLowerCase());
        if (suggestion != null) {
            String suggested = normalized.substring(0, at) + "@" + suggestion;
            throw emailError("EMAIL_POSSIBLE_TYPO", "Did you mean " + suggested + "?")
                    .with("suggestion", suggested);
        }
        return normalized;
    }

    /**
     * Checks a 6-digit code (a login access code or an emailed confirmation
     * code). {@code label} names it in messages, e.g. "access code".
     */
    public static String validateSixDigitCode(String raw, String field, String label) {
        if (raw == null || raw.isEmpty()) {
            throw codeError(field, "CODE_REQUIRED", "Enter your 6-digit " + label + ".");
        }
        if (WHITESPACE.matcher(raw).find()) {
            throw codeError(field, "CODE_HAS_SPACES", "Your " + label + " can't contain spaces.");
        }
        if (!raw.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw codeError(field, "CODE_NOT_NUMERIC", "Your " + label + " can only contain numbers (0-9).");
        }
        if (raw.length() < ACCESS_CODE_LENGTH) {
            int missing = ACCESS_CODE_LENGTH - raw.length();
            throw codeError(field, "CODE_TOO_SHORT", "Your " + label + " has only " + raw.length() + " digit"
                    + (raw.length() == 1 ? "" : "s") + ". Enter " + missing + " more.")
                    .with("digitsEntered", raw.length());
        }
        if (raw.length() > ACCESS_CODE_LENGTH) {
            throw codeError(field, "CODE_TOO_LONG", "Your " + label + " has " + raw.length() + " digits. It should have exactly 6.")
                    .with("digitsEntered", raw.length());
        }
        return raw;
    }

    /** Rules for choosing a new access code: valid, typed the same twice, and not trivially guessable. */
    public static String validateNewAccessCode(String newCode, String confirmCode) {
        validateSixDigitCode(newCode, "newAccessCode", "new access code");
        if (confirmCode == null || confirmCode.isEmpty()) {
            throw codeError("confirmAccessCode", "CONFIRM_REQUIRED", "Type your new access code a second time to confirm it.");
        }
        if (!newCode.equals(confirmCode)) {
            throw codeError("confirmAccessCode", "CODES_DO_NOT_MATCH", "The two codes don't match. Type the same 6 digits in both boxes.");
        }
        if (isTooSimple(newCode)) {
            throw codeError("newAccessCode", "CODE_TOO_SIMPLE",
                    "That code is too easy to guess (like 111111 or 123456). Choose a less predictable one.");
        }
        return newCode;
    }

    static boolean isTooSimple(String code) {
        boolean allSame = code.chars().distinct().count() == 1;
        boolean ascending = true;
        boolean descending = true;
        for (int i = 1; i < code.length(); i++) {
            int step = code.charAt(i) - code.charAt(i - 1);
            ascending &= step == 1;
            descending &= step == -1;
        }
        return allSame || ascending || descending;
    }

    private static String firstCharNotMatching(String text, Pattern allowed) {
        return text.codePoints()
                .mapToObj(Character::toString)
                .filter(c -> !allowed.matcher(c).matches())
                .findFirst()
                .orElse(null);
    }

    private static ApiException emailError(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, "email");
    }

    private static ApiException codeError(String field, String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, field);
    }
}
