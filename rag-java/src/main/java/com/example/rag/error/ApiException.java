package com.example.rag.error;

import org.springframework.http.HttpStatus;

/**
 * Carries an HTTP status plus a {@code detail} message, matching the error shape the Python
 * service returned via FastAPI's {@code HTTPException}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String detail) {
        super(detail);
        this.status = status;
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, detail);
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, detail);
    }

    public HttpStatus status() {
        return status;
    }

    public String detail() {
        return getMessage();
    }
}
