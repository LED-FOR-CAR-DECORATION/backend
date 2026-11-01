package com.lumintra.backend.firmware.domain.model;

public class FirmwareException extends RuntimeException {
    private final String code;
    public FirmwareException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}
