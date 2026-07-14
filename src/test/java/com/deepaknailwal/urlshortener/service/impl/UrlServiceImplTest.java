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
import com.deepaknailwal.urlshortener.util.ShortCodeGenerator;
import com.deepaknailwal.urlshortener.util.UrlHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlServiceImplTest {

    @Mock
    private UrlRepository urlRepository;
    @Mock
    private ShortCodeGenerator shortCodeGenerator;
    @Mock
    private UrlPersister urlPersister;

    private UrlHasher urlHasher;
    private AppProperties appProperties;
    private UrlServiceImpl urlService;

    @BeforeEach
    void setUp() {
        urlHasher = new UrlHasher();
        appProperties = new AppProperties();
        appProperties.setBaseShortUrlDomain("http://localhost:8080");
        appProperties.getShortCode().setLength(7);
        appProperties.getShortCode().setMaxGenerationAttempts(3);
        urlService = new UrlServiceImpl(urlRepository, shortCodeGenerator, urlHasher, appProperties, urlPersister);
    }

    private CreateUrlRequest request(String originalUrl, String customAlias) {
        CreateUrlRequest req = new CreateUrlRequest();
        req.setOriginalUrl(originalUrl);
        req.setCustomAlias(customAlias);
        return req;
    }

    @Test
    void createShortUrl_happyPath_generatesAndPersistsCode() {
        String originalUrl = "https://example.com/very/long/path";
        when(urlRepository.findByUrlHashAndActiveTrue(any())).thenReturn(Optional.empty());
        when(shortCodeGenerator.generate(7)).thenReturn("abc1234");
        when(urlPersister.insertInNewTransaction(any(Url.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UrlResponse response = urlService.createShortUrl(request(originalUrl, null));

        assertThat(response.getShortCode()).isEqualTo("abc1234");
        assertThat(response.getShortUrl()).isEqualTo("http://localhost:8080/abc1234");
        assertThat(response.getOriginalUrl()).isEqualTo(originalUrl);
    }

    @Test
    void createShortUrl_duplicateUrl_returnsExistingActiveMapping() {
        String originalUrl = "https://example.com/dup";
        Url existing = Url.builder()
                .shortCode("existing")
                .originalUrl(originalUrl)
                .urlHash(urlHasher.hash(originalUrl))
                .active(true)
                .build();
        when(urlRepository.findByUrlHashAndActiveTrue(any())).thenReturn(Optional.of(existing));

        UrlResponse response = urlService.createShortUrl(request(originalUrl, null));

        assertThat(response.getShortCode()).isEqualTo("existing");
        verify(urlPersister, times(0)).insertInNewTransaction(any());
    }

    @Test
    void createShortUrl_shortCodeCollision_retriesWithFreshCode() {
        String originalUrl = "https://example.com/retry";
        when(urlRepository.findByUrlHashAndActiveTrue(any())).thenReturn(Optional.empty());
        when(shortCodeGenerator.generate(7)).thenReturn("collide", "fresh01");

        DataIntegrityViolationException collisionEx =
                new DataIntegrityViolationException("duplicate key value violates unique constraint \"uk_urls_short_code\"");
        when(urlPersister.insertInNewTransaction(any(Url.class)))
                .thenThrow(collisionEx)
                .thenAnswer(inv -> inv.getArgument(0));

        UrlResponse response = urlService.createShortUrl(request(originalUrl, null));

        assertThat(response.getShortCode()).isEqualTo("fresh01");
        verify(urlPersister, times(2)).insertInNewTransaction(any());
    }

    @Test
    void createShortUrl_exhaustsRetries_throwsShortCodeGenerationException() {
        String originalUrl = "https://example.com/exhausted";
        when(urlRepository.findByUrlHashAndActiveTrue(any())).thenReturn(Optional.empty());
        when(shortCodeGenerator.generate(anyInt())).thenReturn("code0001");

        DataIntegrityViolationException collisionEx =
                new DataIntegrityViolationException("duplicate key value violates unique constraint \"uk_urls_short_code\"");
        when(urlPersister.insertInNewTransaction(any(Url.class))).thenThrow(collisionEx);

        assertThatThrownBy(() -> urlService.createShortUrl(request(originalUrl, null)))
                .isInstanceOf(ShortCodeGenerationException.class);
        verify(urlPersister, times(3)).insertInNewTransaction(any());
    }

    @Test
    void createShortUrl_concurrentHashClash_returnsWinnersMapping() {
        String originalUrl = "https://example.com/race";
        String hash = urlHasher.hash(originalUrl);
        when(urlRepository.findByUrlHashAndActiveTrue(hash))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(Url.builder()
                        .shortCode("winner1")
                        .originalUrl(originalUrl)
                        .urlHash(hash)
                        .active(true)
                        .build()));
        when(shortCodeGenerator.generate(7)).thenReturn("loser01");

        DataIntegrityViolationException hashClash =
                new DataIntegrityViolationException("duplicate key value violates unique constraint \"uk_urls_url_hash\"");
        when(urlPersister.insertInNewTransaction(any(Url.class))).thenThrow(hashClash);

        UrlResponse response = urlService.createShortUrl(request(originalUrl, null));

        assertThat(response.getShortCode()).isEqualTo("winner1");
    }

    @Test
    void createShortUrl_customAlias_usesRequestedCode() {
        String originalUrl = "https://example.com/alias-me";
        when(urlRepository.existsByShortCode("my-alias")).thenReturn(false);
        when(urlPersister.insertInNewTransaction(any(Url.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UrlResponse response = urlService.createShortUrl(request(originalUrl, "my-alias"));

        assertThat(response.getShortCode()).isEqualTo("my-alias");
        verify(urlRepository, times(0)).findByUrlHashAndActiveTrue(any());
    }

    @Test
    void createShortUrl_customAliasAlreadyTaken_throwsConflict() {
        when(urlRepository.existsByShortCode("taken")).thenReturn(true);

        assertThatThrownBy(() -> urlService.createShortUrl(request("https://example.com/x", "taken")))
                .isInstanceOf(AliasAlreadyExistsException.class);
        verify(urlPersister, times(0)).insertInNewTransaction(any());
    }

    @Test
    void createShortUrl_customAliasRaceLostAtInsert_throwsConflict() {
        when(urlRepository.existsByShortCode("racey")).thenReturn(false);
        DataIntegrityViolationException collisionEx =
                new DataIntegrityViolationException("duplicate key value violates unique constraint \"uk_urls_short_code\"");
        when(urlPersister.insertInNewTransaction(any(Url.class))).thenThrow(collisionEx);

        assertThatThrownBy(() -> urlService.createShortUrl(request("https://example.com/y", "racey")))
                .isInstanceOf(AliasAlreadyExistsException.class);
    }

    @Test
    void createShortUrl_malformedUrl_throwsBadRequest() {
        assertThatThrownBy(() -> urlService.createShortUrl(request("not-a-url", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createShortUrl_blankHost_throwsBadRequest() {
        assertThatThrownBy(() -> urlService.createShortUrl(request("http:///no-host", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getActiveUrlByShortCode_unknownCode_throwsNotFound() {
        when(urlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.getActiveUrlByShortCode("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getActiveUrlByShortCode_inactiveCode_throwsNotFound() {
        Url inactive = Url.builder()
                .shortCode("gone")
                .originalUrl("https://example.com")
                .urlHash("hash")
                .active(false)
                .build();
        when(urlRepository.findByShortCode("gone")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> urlService.getActiveUrlByShortCode("gone"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getActiveUrlByShortCode_expiredCode_throwsNotFound() {
        Url expired = Url.builder()
                .shortCode("old")
                .originalUrl("https://example.com")
                .urlHash("hash")
                .active(true)
                .expiresAt(OffsetDateTime.now().minusDays(1))
                .build();
        when(urlRepository.findByShortCode("old")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> urlService.getActiveUrlByShortCode("old"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getActiveUrlByShortCode_validCode_returnsUrl() {
        Url active = Url.builder()
                .shortCode("live")
                .originalUrl("https://example.com")
                .urlHash("hash")
                .active(true)
                .build();
        when(urlRepository.findByShortCode("live")).thenReturn(Optional.of(active));

        Url result = urlService.getActiveUrlByShortCode("live");

        assertThat(result.getOriginalUrl()).isEqualTo("https://example.com");
    }
}
