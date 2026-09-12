package com.photoshare.common;

import java.util.List;

public record ApiErrorResponse(ErrorBody error) {
    public record ErrorBody(String code, String message, List<FieldError> fields, String requestId) {}
    public record FieldError(String field, String message) {}

    public static ApiErrorResponse of(String code, String message, String requestId) {
        return new ApiErrorResponse(new ErrorBody(code, message, null, requestId));
    }

    public static ApiErrorResponse of(String code, String message, List<FieldError> fields, String requestId) {
        return new ApiErrorResponse(new ErrorBody(code, message, fields, requestId));
    }
}

