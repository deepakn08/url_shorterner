package com.deepaknailwal.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateUrlRequest {

    @NotBlank(message = "original_url is required")
    @JsonProperty("original_url")
    private String originalUrl;

    // Optional. When present, this exact code is used instead of a generated one.
    // Restricted to URL-safe characters and kept short so aliases stay shareable.
    @Pattern(regexp = "^[A-Za-z0-9_-]{3,30}$", message = "custom_alias must be 3-30 URL-safe characters (letters, digits, - or _)")
    @JsonProperty("custom_alias")
    private String customAlias;
}
