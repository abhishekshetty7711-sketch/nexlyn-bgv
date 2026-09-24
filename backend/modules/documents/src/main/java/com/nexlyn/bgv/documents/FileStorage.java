package com.nexlyn.bgv.documents;

/**
 * The private file store (an S3-compatible bucket), for modules that keep their own files, such as
 * generated report PDFs. Keys are chosen by the caller and must not contain personal data.
 */
public interface FileStorage {

    void put(String key, byte[] content, String contentType);

    /** The stored bytes; throws a not-found error when the key does not exist. */
    byte[] get(String key);

    void delete(String key);
}
