package com.reportserver.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine in-process cache configuration.
 *
 * Caches:
 *  - "compiledReports"  : JasperReport compiled objects keyed by (path + lastModified).
 *                         Avoids recompiling JRXML on every generate request.
 *                         TTL 30 min, max 200 entries (≈ memory bound).
 *  - "reportParameters" : Extracted parameter metadata per JRXML file.
 *                         TTL 10 min, max 500 entries.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String COMPILED_REPORTS_CACHE = "compiledReports";
    public static final String REPORT_PARAMETERS_CACHE = "reportParameters";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                COMPILED_REPORTS_CACHE,
                REPORT_PARAMETERS_CACHE
        );
        manager.setCaffeine(defaultCaffeineSpec());
        return manager;
    }

    /**
     * Default spec: expire 30 min after write, max 200 entries per cache.
     * Individual caches can be overridden by registering named CaffeineCache beans.
     */
    private Caffeine<Object, Object> defaultCaffeineSpec() {
        return Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(200)
                .recordStats();
    }
}
