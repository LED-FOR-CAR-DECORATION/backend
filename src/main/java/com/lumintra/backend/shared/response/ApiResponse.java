package com.lumintra.backend.shared.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "Standard API response envelope")
@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    @Schema(description = "Response payload data")
    private final T data;

    @Schema(description = "Optional metadata for pagination, async jobs, or supplementary info")
    private final Object meta;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> success(T data, Object meta) {
        return new ApiResponse<>(data, meta);
    }
}