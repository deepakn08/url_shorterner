package com.deepaknailwal.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUrlRequest {

    @NotBlank(message = "original_url is required")
    @JsonProperty("original_url")
    private String originalUrl;
}
