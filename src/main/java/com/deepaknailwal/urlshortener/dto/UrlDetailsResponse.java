package com.deepaknailwal.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class UrlDetailsResponse {

    private Long id;

    @JsonProperty("short_code")
    private String shortCode;

    @JsonProperty("original_url")
    private String originalUrl;

    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("expires_at")
    private OffsetDateTime expiresAt;

    @JsonProperty("is_active")
    private boolean active;
}
