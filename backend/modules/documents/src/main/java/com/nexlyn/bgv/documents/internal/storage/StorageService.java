package com.nexlyn.bgv.documents.internal.storage;

/**
 * Where the file bytes live: an S3-compatible bucket (AWS S3 in production, a local S3 store in
 * development). The bucket is private; files are only ever handed out by the application, after a
 * permission check. Failures surface as an {@code ApiException} with a plain message.
 */
public interface StorageService {

    void put(String key, byte[] content, String contentType);

    /** The stored bytes; throws when the key does not exist. */
    byte[] get(String key);

    void delete(String key);
}
