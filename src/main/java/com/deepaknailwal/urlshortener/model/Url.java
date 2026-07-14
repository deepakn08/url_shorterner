package com.deepaknailwal.urlshortener.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "urls",
        indexes = {
                @Index(name = "idx_urls_user_id", columnList = "user_id")
        },
        // Named explicitly (rather than relying on unique = true on @Column) so the
        // service layer can tell, from a DataIntegrityViolationException, which
        // constraint fired: a short_code clash needs a fresh code / alias rejection,
        // while a url_hash clash means a concurrent request just shortened this same URL.
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_urls_short_code", columnNames = "short_code"),
                @UniqueConstraint(name = "uk_urls_url_hash", columnNames = "url_hash")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Long enough for the max custom_alias length (50 chars, see CreateUrlRequest); generated
    // codes are only 7 chars but share this column.
    @Column(name = "short_code", nullable = false, length = 50)
    private String shortCode;

    @Column(name = "original_url", nullable = false, columnDefinition = "text")
    private String originalUrl;

    // SHA-256 hex digest of original_url. Fixed-length (64 chars) so the uniqueness
    // check/index stays cheap regardless of how long the actual URL is; comparing or
    // indexing original_url itself directly would grow with the URL's length.
    @Column(name = "url_hash", nullable = false, length = 64)
    private String urlHash;

    // Not populated by the current shortening API (no auth/user concept wired up yet);
    // kept nullable on the schema so ownership can be added later without a migration.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
