package com.rmshop.rmshop.exception;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

// An error the frontend can react to precisely: a machine-readable code
// (e.g. EMAIL_MISSING_AT), a human message, the form field at fault, and any
// extra numbers the UI needs (attempts left, seconds until unlock...).
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String field;
    private final Map<String, Object> details = new LinkedHashMap<>();

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, String field) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
    }

    public ApiException with(String key, Object value) {
        details.put(key, value);
        return this;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public String getField() { return field; }
    public Map<String, Object> getDetails() { return details; }

    public Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", getMessage());
        if (field != null) body.put("field", field);
        body.putAll(details);
        return body;
    }
}
