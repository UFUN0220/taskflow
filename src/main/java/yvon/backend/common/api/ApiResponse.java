package yvon.backend.common.api;

import yvon.backend.common.error.BusinessErrorCode;
import yvon.backend.common.trace.TraceIdContext;

import java.time.LocalDateTime;

/** Unified response envelope for JSON APIs. */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        LocalDateTime timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                BusinessErrorCode.SUCCESS.code(),
                BusinessErrorCode.SUCCESS.defaultMessage(),
                data,
                TraceIdContext.getOrCreate(),
                LocalDateTime.now()
        );
    }

    public static ApiResponse<Void> error(BusinessErrorCode errorCode, String message) {
        return new ApiResponse<>(
                errorCode.code(),
                message == null || message.isBlank() ? errorCode.defaultMessage() : message,
                null,
                TraceIdContext.getOrCreate(),
                LocalDateTime.now()
        );
    }
}
