package com.lumintra.backend.shared.exception.store;

public class ProductException extends RuntimeException {
    private final String code;

    public ProductException(String message) {
        super(message);
        this.code = "PRODUCT_ERROR";
    }

    public ProductException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
