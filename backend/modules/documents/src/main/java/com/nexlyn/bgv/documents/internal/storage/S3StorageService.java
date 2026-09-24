package com.nexlyn.bgv.documents.internal.storage;

import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.net.URI;

/**
 * Stores files in an S3-compatible bucket. The client is built on first use, so the application can
 * start (and be tested) without a store configured; only an upload or download then fails.
 */
@Component
class S3StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final StorageProperties properties;
    private volatile S3Client client;

    S3StorageService(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        run("store", () -> {
            PutObjectRequest.Builder request = PutObjectRequest.builder()
                    .bucket(properties.bucket()).key(key).contentType(contentType).contentLength((long) content.length);
            if (properties.serverSideEncryption()) {
                request.serverSideEncryption(ServerSideEncryption.AES256);
            }
            client().putObject(request.build(), RequestBody.fromBytes(content));
            return null;
        });
    }

    @Override
    public byte[] get(String key) {
        return run("read", () -> {
            try {
                return client().getObjectAsBytes(GetObjectRequest.builder().bucket(properties.bucket()).key(key).build()).asByteArray();
            } catch (NoSuchKeyException e) {
                throw new ApiException(ErrorCode.NOT_FOUND, "The file is missing from storage.");
            }
        });
    }

    @Override
    public void delete(String key) {
        run("delete", () -> {
            client().deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
            return null;
        });
    }

    // ---- plumbing -----------------------------------------------------------------------------------------

    private interface Call<T> {
        T run();
    }

    /** Turns any storage failure into one plain 503, without leaking the store's details (they go to the log). */
    private <T> T run(String what, Call<T> call) {
        if (!properties.configured()) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "File storage is not set up on this server.");
        }
        try {
            return call.run();
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Could not {} a file in storage: {}", what, e.toString());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "File storage is not available right now. Please try again in a moment.");
        }
    }

    private S3Client client() {
        S3Client existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (client == null) {
                client = build();
                if (properties.createBucket()) {
                    ensureBucket(client);
                }
            }
            return client;
        }
    }

    private S3Client build() {
        AwsCredentialsProvider credentials = properties.accessKey() == null || properties.accessKey().isBlank()
                ? DefaultCredentialsProvider.create()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        var builder = S3Client.builder()
                .region(Region.of(properties.region() == null || properties.region().isBlank() ? "ap-south-1" : properties.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).build());
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    private void ensureBucket(S3Client s3) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (NoSuchBucketException e) {
            s3.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
            log.info("Created the storage bucket {}", properties.bucket());
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            if (e.statusCode() == 404) {
                s3.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
                log.info("Created the storage bucket {}", properties.bucket());
            } else {
                throw e;
            }
        }
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }
}
