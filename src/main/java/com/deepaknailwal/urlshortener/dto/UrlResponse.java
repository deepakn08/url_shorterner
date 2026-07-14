package com.deepaknailwal.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UrlResponse {

    @JsonProperty("short_code")
    private String shortCode;

    @JsonProperty("short_url")
    private String shortUrl;

    @JsonProperty("original_url")
    private String originalUrl;
}
