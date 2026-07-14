package com.deepaknailwal.urlshortener.service.impl;

import com.deepaknailwal.urlshortener.model.Url;
import com.deepaknailwal.urlshortener.repository.UrlRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each insert attempt runs in its own brand-new transaction/connection.
 * Postgres aborts the entire transaction on a unique-constraint violation,
 * so retrying a failed insert on the *same* transaction would just fail
 * again with "current transaction is aborted" instead of actually retrying.
 * REQUIRES_NEW gives every attempt a clean transaction to fail or succeed in.
 */
@Component
public class UrlPersister {

    private final UrlRepository urlRepository;

    public UrlPersister(UrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Url insertInNewTransaction(Url url) {
        return urlRepository.saveAndFlush(url);
    }
}
