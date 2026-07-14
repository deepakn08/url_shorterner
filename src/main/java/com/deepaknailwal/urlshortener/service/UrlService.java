package com.deepaknailwal.urlshortener.service;

import com.deepaknailwal.urlshortener.dto.CreateUrlRequest;
import com.deepaknailwal.urlshortener.dto.UrlResponse;
import com.deepaknailwal.urlshortener.model.Url;

public interface UrlService {

    UrlResponse createShortUrl(CreateUrlRequest request);

    Url getActiveUrlByShortCode(String shortCode);
}
