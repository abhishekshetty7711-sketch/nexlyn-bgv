package com.nexlyn.bgv.documents.internal.storage;

import com.nexlyn.bgv.documents.FileStorage;
import org.springframework.stereotype.Component;

/** Hands the private store to other modules through the public {@link FileStorage} interface. */
@Component
class FileStorageAdapter implements FileStorage {

    private final StorageService storage;

    FileStorageAdapter(StorageService storage) {
        this.storage = storage;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        storage.put(key, content, contentType);
    }

    @Override
    public byte[] get(String key) {
        return storage.get(key);
    }

    @Override
    public void delete(String key) {
        storage.delete(key);
    }
}
