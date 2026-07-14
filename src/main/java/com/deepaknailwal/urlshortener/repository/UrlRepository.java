package com.deepaknailwal.urlshortener.repository;

import com.deepaknailwal.urlshortener.model.Url;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    Page<Url> findByUser_Id(UUID userId, Pageable pageable);

    Optional<Url> findByUrlHashAndActiveTrue(String urlHash);
}
