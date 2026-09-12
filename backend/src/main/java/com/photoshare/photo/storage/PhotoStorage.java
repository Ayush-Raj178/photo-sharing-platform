package com.photoshare.photo.storage;

import org.springframework.core.io.Resource;

public interface PhotoStorage {
    StoredPhoto put(String key, byte[] bytes, String contentType, String originalFilename);
    Resource read(String key);
    void delete(String key);

    record StoredPhoto(String storageKey, String contentType, String originalFilename, long fileSizeBytes) {}
}
