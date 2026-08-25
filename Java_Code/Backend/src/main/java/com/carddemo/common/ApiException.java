package com.carddemo.common;

import org.springframework.http.HttpStatus;

/**
 * Business failure carrying the exact COBOL-sourced message so the Angular screens can render
 * the same text the BMS {@code ERRMSG} field used to show.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String field;

    public ApiException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public ApiException(HttpStatus status, String message, String field) {
        super(message);
        this.status = status;
        this.field = field;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getField() {
        return field;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException notFound(String message, String field) {
        return new ApiException(HttpStatus.NOT_FOUND, message, field);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException badRequest(String message, String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, field);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException conflict(String message, String field) {
        return new ApiException(HttpStatus.CONFLICT, message, field);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }
}
