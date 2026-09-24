package com.nexlyn.bgv.documents.internal.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the file store ({@code nexlyn.storage.*}, from the environment: CLAUDE.md section 14).
 * Blank {@code bucket} means "not configured": the app still starts (outside prod) and uploads say so.
 *
 * @param endpoint            blank for AWS; the URL of an S3-compatible store otherwise
 * @param accessKey           blank to use the AWS default credential chain (IAM role)
 * @param pathStyle           true for most S3-compatible stores (bucket in the path, not the host name)
 * @param serverSideEncryption ask the store to encrypt at rest (AES-256); leave off for stores that lack it
 * @param createBucket        create the bucket on first use if missing (local development only)
 */
@ConfigurationProperties(prefix = "nexlyn.storage")
public record StorageProperties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyle,
        boolean serverSideEncryption,
        boolean createBucket) {

    public boolean configured() {
        return bucket != null && !bucket.isBlank();
    }
}
