package com.lumintra.backend.shared.exception.commerce;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CarrierDispatchException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;

    public CarrierDispatchException(HttpStatus httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public CarrierDispatchException(HttpStatus httpStatus, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }
}
