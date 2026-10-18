package com.lumintra.backend.shared.exception;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class ErrorResponse {
    private String code;
    private String message;
    private int status;
    private String timestamp;
    private String traceId;
    private List<ErrorDetail> details;

    @Getter
    @Builder
    public static class ErrorDetail {
        private String field;
        private String message;
    }
}