package com.deepaknailwal.urlshortener.service.impl;

import com.deepaknailwal.urlshortener.config.AppProperties;
import com.deepaknailwal.urlshortener.dto.CreateUrlRequest;
import com.deepaknailwal.urlshortener.dto.UrlResponse;
import com.deepaknailwal.urlshortener.exception.AliasAlreadyExistsException;
import com.deepaknailwal.urlshortener.exception.BadRequestException;
import com.deepaknailwal.urlshortener.exception.ResourceNotFoundException;
import com.deepaknailwal.urlshortener.exception.ShortCodeGenerationException;
import com.deepaknailwal.urlshortener.model.Url;
import com.deepaknailwal.urlshortener.repository.UrlRepository;
import com.deepaknailwal.urlshortener.service.UrlService;
import com.deepaknailwal.urlshortener.util.ShortCodeGenerator;
import com.deepaknailwal.urlshortener.util.UrlHasher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class UrlServiceImpl implements UrlService {

    private static final String URL_HASH_CONSTRAINT = "uk_urls_url_hash";
    private static final String SHORT_CODE_CONSTRAINT = "uk_urls_short_code";

    private final UrlRepository urlRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final UrlHasher urlHasher;
    private final AppProperties appProperties;
    private final UrlPersister urlPersister;

    public UrlServiceImpl(UrlRepository urlRepository,
                           ShortCodeGenerator shortCodeGenerator,
                           UrlHasher urlHasher,
                           AppProperties appProperties,
                           UrlPersister urlPersister) {
        this.urlRepository = urlRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.urlHasher = urlHasher;
        this.appProperties = appProperties;
        this.urlPersister = urlPersister;
    }

    @Override
    public UrlResponse createShortUrl(CreateUrlRequest request) {
        String originalUrl = request.getOriginalUrl();
        validateUrl(originalUrl);
        String hash = urlHasher.hash(originalUrl);
        String customAlias = request.getCustomAlias();

        if (customAlias != null && !customAlias.isBlank()) {
            // A custom alias is an explicit request for that exact code, so it bypasses
            // the dedup-by-hash shortcut below: the same URL can have both an
            // auto-generated code and one or more custom aliases.
            return createWithCustomAlias(originalUrl, hash, customAlias);
        }

        // Dedup is global (no per-user scoping): the same URL always maps back
        // to whichever short_code first claimed it.
        Optional<Url> existing = urlRepository.findByUrlHashAndActiveTrue(hash);
        if (existing.isPresent() && !isExpired(existing.get())) {
            return toResponse(existing.get());
        }

        return createWithGeneratedCode(originalUrl, hash);
    }

    private UrlResponse createWithCustomAlias(String originalUrl, String hash, String customAlias) {
        if (urlRepository.existsByShortCode(customAlias)) {
            throw new AliasAlreadyExistsException("custom_alias '" + customAlias + "' is already in use");
        }
        Url url = Url.builder()
                .shortCode(customAlias)
                .originalUrl(originalUrl)
                .urlHash(hash)
                .active(true)
                .build();
        try {
            Url saved = urlPersister.insertInNewTransaction(url);
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            if (violates(e, SHORT_CODE_CONSTRAINT)) {
                throw new AliasAlreadyExistsException("custom_alias '" + customAlias + "' is already in use");
            }
            throw e;
        }
    }

    private UrlResponse createWithGeneratedCode(String originalUrl, String hash) {
        int maxAttempts = appProperties.getShortCode().getMaxGenerationAttempts();
        int codeLength = appProperties.getShortCode().getLength();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String candidate = shortCodeGenerator.generate(codeLength);
            Url url = Url.builder()
                    .shortCode(candidate)
                    .originalUrl(originalUrl)
                    .urlHash(hash)
                    .active(true)
                    .build();
            try {
                // Fresh transaction per attempt: Postgres aborts the whole transaction on
                // a unique-constraint violation, so reusing one transaction across retries
                // would make every attempt after the first fail regardless of the new code.
                Url saved = urlPersister.insertInNewTransaction(url);
                return toResponse(saved);
            } catch (DataIntegrityViolationException e) {
                if (violates(e, URL_HASH_CONSTRAINT)) {
                    // Someone else shortened this exact URL concurrently; use their code
                    // instead of minting a second one for the same destination.
                    return toResponse(fetchByHashOrThrow(hash));
                }
                // short_code collision: retry with a fresh code.
            }
        }
        throw new ShortCodeGenerationException(
                "Failed to generate a unique short code after " + maxAttempts + " attempts");
    }

    private Url fetchByHashOrThrow(String hash) {
        return urlRepository.findByUrlHashAndActiveTrue(hash)
                .orElseThrow(() -> new ShortCodeGenerationException(
                        "Concurrent insert detected for this URL but the row could not be re-read"));
    }

    private boolean violates(DataIntegrityViolationException e, String constraintName) {
        Throwable root = e.getMostSpecificCause();
        return root.getMessage() != null && root.getMessage().toLowerCase().contains(constraintName);
    }

    private boolean isExpired(Url url) {
        return url.getExpiresAt() != null && url.getExpiresAt().isBefore(OffsetDateTime.now());
    }

    @Override
    public Url getActiveUrlByShortCode(String shortCode) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found: " + shortCode));

        if (!url.isActive() || isExpired(url)) {
            throw new ResourceNotFoundException("Short URL not found: " + shortCode);
        }
        return url;
    }

    private void validateUrl(String originalUrl) {
        try {
            URI uri = new URI(originalUrl);
            URL url = uri.toURL();
            if (url.getProtocol() == null || url.getHost() == null || url.getHost().isBlank()) {
                throw new BadRequestException("original_url must be a well-formed URL");
            }
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
            throw new BadRequestException("original_url must be a well-formed URL");
        }
    }

    private UrlResponse toResponse(Url url) {
        String shortUrl = appProperties.getBaseShortUrlDomain() + "/" + url.getShortCode();
        return new UrlResponse(url.getShortCode(), shortUrl, url.getOriginalUrl());
    }
}
