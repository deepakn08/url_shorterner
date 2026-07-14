package com.deepaknailwal.urlshortener.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private String baseShortUrlDomain;
    private ShortCode shortCode = new ShortCode();

    @Data
    public static class ShortCode {
        private int length = 7;
        private int maxGenerationAttempts = 5;
    }
}
