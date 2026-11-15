package com.lumintra.backend.shared.exception.commerce;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class OrderFulfillmentException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;

    public OrderFulfillmentException(HttpStatus httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public OrderFulfillmentException(HttpStatus httpStatus, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }
}
