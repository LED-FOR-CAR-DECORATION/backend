package com.lumintra.backend.store.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Store category tag")
public record StoreTagResponse(Long tagId, String name, String description) {}
