package com.photoshare.photo.storage;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CloudinaryPhotoStorage implements PhotoStorage {
    private static final Pattern KEY_PATTERN = Pattern.compile("events/[0-9]+/photos/[a-zA-Z0-9_-]+\\.(jpg|png)");
    private final CloudinaryAssetClient client;
    private final long maxFileSizeBytes;

    CloudinaryPhotoStorage(CloudinaryAssetClient client, long maxFileSizeBytes) {
        this.client = client;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public StoredPhoto put(String key, byte[] bytes, String contentType, String originalFilename) {
        AssetKey asset = parse(key);
        String expectedType = asset.format().equals("png") ? "image/png" : "image/jpeg";
        if (bytes == null || bytes.length == 0 || bytes.length > maxFileSizeBytes
                || !expectedType.equals(contentType)) {
            throw new StorageException("Invalid photo storage request", null, false);
        }
        boolean confirmedPublicId = false;
        try {
            CloudinaryAssetClient.UploadResult result = client.upload(
                    bytes, asset.publicId(), asset.format(), originalFilename);
            confirmedPublicId = result != null && asset.publicId().equals(result.publicId());
            if (!confirmedPublicId
                    || result.format() == null
                    || !asset.format().equals(result.format().toLowerCase(Locale.ROOT))
                    || result.bytes() <= 0
                    || !"image".equals(result.resourceType())
                    || !"authenticated".equals(result.deliveryType())) {
                throw new StorageException("Cloudinary upload metadata did not match the storage request", null,
                        confirmedPublicId);
            }
            return new StoredPhoto(key, expectedType, originalFilename, bytes.length);
        } catch (StorageException exception) {
            throw new StorageException(exception.getMessage(), exception, confirmedPublicId);
        } catch (Exception exception) {
            throw new StorageException("Photo bytes could not be stored", exception, confirmedPublicId);
        }
    }

    @Override
    public Resource read(String key) {
        AssetKey asset = parse(key);
        try {
            byte[] bytes = client.read(asset.publicId(), asset.format());
            if (bytes == null || bytes.length == 0 || bytes.length > maxFileSizeBytes) {
                throw new StorageException("Stored photo is unavailable", null);
            }
            return new ByteArrayResource(bytes);
        } catch (StorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new StorageException("Stored photo is unavailable", exception);
        }
    }

    @Override
    public void delete(String key) {
        AssetKey asset = parse(key);
        try {
            String result = client.delete(asset.publicId()).result();
            if (!("ok".equals(result) || "not found".equals(result))) {
                throw new StorageException("Stored photo could not be cleaned up", null);
            }
        } catch (StorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new StorageException("Stored photo could not be cleaned up", exception);
        }
    }

    private AssetKey parse(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new StorageException("Invalid storage key", null);
        }
        int dot = key.lastIndexOf('.');
        return new AssetKey(key.substring(0, dot), key.substring(dot + 1));
    }

    private record AssetKey(String publicId, String format) {}
}
