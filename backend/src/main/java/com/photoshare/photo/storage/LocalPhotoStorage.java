package com.photoshare.photo.storage;

import com.photoshare.config.PhotoShareProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;

@Component
@ConditionalOnProperty(prefix = "photoshare.storage", name = "driver", havingValue = "local", matchIfMissing = true)
public class LocalPhotoStorage implements PhotoStorage {
    private final Path root;

    public LocalPhotoStorage(PhotoShareProperties properties) {
        this.root = properties.storage().localRoot().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Local photo storage could not be initialized", ex);
        }
    }

    @Override
    public StoredPhoto put(String key, byte[] bytes, String contentType, String originalFilename) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return new StoredPhoto(key, contentType, originalFilename, bytes.length);
        } catch (IOException ex) {
            throw new StorageException("Photo bytes could not be stored", ex);
        }
    }

    @Override
    public Resource read(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            throw new StorageException("Stored photo is unavailable", null);
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            throw new StorageException("Stored photo could not be cleaned up", ex);
        }
    }

    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new StorageException("Invalid storage key", null);
        }
        return target;
    }
}
