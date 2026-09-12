package com.photoshare.photo.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.photoshare.config.PhotoShareProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudinaryPhotoStorageTest {
    @TempDir
    Path tempDirectory;

    @Test
    void cloudinaryProviderIsSelectedAndConfigurationIsMapped() {
        propertiesRunner("cloudinary", "test-cloud", "test-key", "test-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PhotoStorage.class);
                    assertThat(context.getBean(PhotoStorage.class)).isInstanceOf(CloudinaryPhotoStorage.class);
                    Cloudinary cloudinary = context.getBean(Cloudinary.class);
                    assertThat(cloudinary.config.cloudName).isEqualTo("test-cloud");
                    assertThat(cloudinary.config.apiKey).isEqualTo("test-key");
                    assertThat(cloudinary.config.apiSecret).isEqualTo("test-secret");
                    assertThat(cloudinary.config.secure).isTrue();
                });
    }

    @Test
    void localProviderRemainsTheExplicitTestFallback() {
        propertiesRunner("local", "unused", "unused", "unused")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PhotoStorage.class);
                    assertThat(context.getBean(PhotoStorage.class)).isInstanceOf(LocalPhotoStorage.class);
                    assertThat(context).doesNotHaveBean(Cloudinary.class);
                });
    }

    @Test
    void missingCloudinaryConfigurationFailsAtStartupWithoutEchoingValues() {
        assertMissingConfiguration("", "test-key", "test-secret", "CLOUDINARY_CLOUD_NAME");
        assertMissingConfiguration("test-cloud", "", "test-secret", "CLOUDINARY_API_KEY");
        assertMissingConfiguration("test-cloud", "test-key", "", "CLOUDINARY_API_SECRET");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void sdkUploadUsesAuthenticatedDeliveryAndTheGeneratedPublicId() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class))).thenReturn(Map.of(
                "public_id", "events/42/photos/photo_1",
                "format", "png",
                "bytes", 3,
                "resource_type", "image",
                "type", "authenticated"));
        SdkCloudinaryAssetClient client = new SdkCloudinaryAssetClient(cloudinary);

        client.upload(new byte[]{1, 2, 3}, "events/42/photos/photo_1", "png", "wedding.png");

        var options = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(any(byte[].class), options.capture());
        assertThat(options.getValue())
                .containsEntry("public_id", "events/42/photos/photo_1")
                .containsEntry("resource_type", "image")
                .containsEntry("type", "authenticated")
                .containsEntry("overwrite", false)
                .containsEntry("unique_filename", false)
                .containsEntry("return_error", true)
                .containsEntry("filename_override", "wedding.png");
    }

    @Test
    void uploadMapsAuthenticatedCloudinaryResultToStorageMetadata() {
        RecordingClient client = new RecordingClient();
        byte[] bytes = {1, 2, 3, 4};
        client.uploadResult = new CloudinaryAssetClient.UploadResult(
                "events/42/photos/photo_1", "jpg", bytes.length + 7, "image", "authenticated");
        CloudinaryPhotoStorage storage = new CloudinaryPhotoStorage(client, 1024);

        PhotoStorage.StoredPhoto result = storage.put(
                "events/42/photos/photo_1.jpg", bytes, "image/jpeg", "wedding.jpg");

        assertThat(client.uploadPublicId).isEqualTo("events/42/photos/photo_1");
        assertThat(client.uploadFormat).isEqualTo("jpg");
        assertThat(client.uploadFilename).isEqualTo("wedding.jpg");
        assertThat(result.storageKey()).isEqualTo("events/42/photos/photo_1.jpg");
        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.originalFilename()).isEqualTo("wedding.jpg");
        assertThat(result.fileSizeBytes()).isEqualTo(bytes.length);
    }

    @Test
    void uploadRejectsACloudinaryResponseWithoutStoredBytes() {
        RecordingClient client = new RecordingClient();
        client.uploadResult = new CloudinaryAssetClient.UploadResult(
                "events/42/photos/photo_1", "png", 0, "image", "authenticated");
        CloudinaryPhotoStorage storage = new CloudinaryPhotoStorage(client, 1024);

        assertThatThrownBy(() -> storage.put(
                "events/42/photos/photo_1.png", new byte[]{1}, "image/png", "photo.png"))
                .isInstanceOf(StorageException.class)
                .hasMessage("Cloudinary upload metadata did not match the storage request")
                .isInstanceOfSatisfying(StorageException.class,
                        exception -> assertThat(exception.cleanupAllowed()).isTrue());
    }

    @Test
    void uploadWithoutAConfirmedMatchingPublicIdDoesNotAllowCleanup() {
        RecordingClient client = new RecordingClient();
        CloudinaryPhotoStorage storage = new CloudinaryPhotoStorage(client, 1024);
        for (String publicId : new String[]{"", "events/42/photos/someone_else"}) {
            client.uploadResult = new CloudinaryAssetClient.UploadResult(publicId, "png", 1, "image", "authenticated");
            assertThatThrownBy(() -> storage.put("events/42/photos/photo_1.png", new byte[]{1}, "image/png", "photo.png"))
                    .isInstanceOfSatisfying(StorageException.class,
                            exception -> assertThat(exception.cleanupAllowed()).isFalse());
        }
        client.failure = new RuntimeException("provider failed");
        assertThatThrownBy(() -> storage.put("events/42/photos/photo_1.png", new byte[]{1}, "image/png", "photo.png"))
                .isInstanceOfSatisfying(StorageException.class,
                        exception -> assertThat(exception.cleanupAllowed()).isFalse());
    }

    @Test
    void storageFailureExposesOnlyTheRootExceptionTypeForSafeDiagnostics() {
        RecordingClient client = new RecordingClient();
        client.failure = new IOException("credential-bearing provider details must not be logged");
        CloudinaryPhotoStorage storage = new CloudinaryPhotoStorage(client, 1024);

        assertThatThrownBy(() -> storage.put(
                "events/42/photos/photo_1.png", new byte[]{1}, "image/png", "photo.png"))
                .isInstanceOfSatisfying(StorageException.class,
                        exception -> assertThat(exception.rootCauseType()).isEqualTo("IOException"));
    }

    @Test
    void uploadReadAndDeleteFailuresArePropagatedAsStorageFailures() {
        RecordingClient client = new RecordingClient();
        client.failure = new IOException("provider unavailable");
        CloudinaryPhotoStorage storage = new CloudinaryPhotoStorage(client, 1024);

        assertThatThrownBy(() -> storage.put(
                "events/42/photos/photo_1.png", new byte[]{1}, "image/png", "photo.png"))
                .isInstanceOf(StorageException.class)
                .hasMessage("Photo bytes could not be stored");
        assertThatThrownBy(() -> storage.read("events/42/photos/photo_1.png"))
                .isInstanceOf(StorageException.class)
                .hasMessage("Stored photo is unavailable");
        assertThatThrownBy(() -> storage.delete("events/42/photos/photo_1.png"))
                .isInstanceOf(StorageException.class)
                .hasMessage("Stored photo could not be cleaned up");
    }

    private ApplicationContextRunner propertiesRunner(
            String driver, String cloudName, String apiKey, String apiSecret) {
        PhotoShareProperties properties = new PhotoShareProperties(null, null, null,
                new PhotoShareProperties.Storage(driver, tempDirectory,
                        new PhotoShareProperties.Storage.Cloudinary(cloudName, apiKey, apiSecret),
                        1024, 4096, 20, 40_000_000), null);
        return new ApplicationContextRunner()
                .withPropertyValues("photoshare.storage.driver=" + driver)
                .withBean(PhotoShareProperties.class, () -> properties)
                .withUserConfiguration(CloudinaryStorageConfiguration.class, LocalPhotoStorage.class);
    }

    private void assertMissingConfiguration(
            String cloudName, String apiKey, String apiSecret, String variableName) {
        propertiesRunner("cloudinary", cloudName, apiKey, apiSecret)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            variableName + " must be configured when STORAGE_DRIVER=cloudinary");
                });
    }

    private static final class RecordingClient implements CloudinaryAssetClient {
        private UploadResult uploadResult;
        private Exception failure;
        private String uploadPublicId;
        private String uploadFormat;
        private String uploadFilename;

        @Override
        public UploadResult upload(byte[] bytes, String publicId, String format, String originalFilename)
                throws Exception {
            if (failure != null) throw failure;
            uploadPublicId = publicId;
            uploadFormat = format;
            uploadFilename = originalFilename;
            return uploadResult;
        }

        @Override
        public byte[] read(String publicId, String format) throws Exception {
            if (failure != null) throw failure;
            return new byte[]{1};
        }

        @Override
        public DeleteResult delete(String publicId) throws Exception {
            if (failure != null) throw failure;
            return new DeleteResult("ok");
        }
    }
}
