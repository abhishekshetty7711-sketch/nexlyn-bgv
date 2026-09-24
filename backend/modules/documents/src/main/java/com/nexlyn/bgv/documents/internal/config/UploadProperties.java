package com.nexlyn.bgv.documents.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Upload limits ({@code nexlyn.documents.*}).
 *
 * @param maxBytes  largest accepted file (default 10 MB, CLAUDE.md section 11.4)
 * @param maxPixels largest accepted image, width times height, so a tiny file cannot expand into gigabytes of memory
 * @param retentionDays how long the stored bytes of a removed document are kept before being deleted for good (default 90; 0 = never delete)
 */
@ConfigurationProperties(prefix = "nexlyn.documents")
public record UploadProperties(Long maxBytes, Long maxPixels, Integer retentionDays) {

    public int retentionDaysOrDefault() {
        return retentionDays == null ? 90 : Math.max(0, retentionDays);
    }

    public long maxBytesOrDefault() {
        return maxBytes == null ? 10L * 1024 * 1024 : maxBytes;
    }

    public long maxPixelsOrDefault() {
        return maxPixels == null ? 25_000_000L : maxPixels;
    }
}
