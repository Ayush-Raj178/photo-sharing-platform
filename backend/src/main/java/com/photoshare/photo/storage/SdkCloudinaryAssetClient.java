package com.photoshare.photo.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

final class SdkCloudinaryAssetClient implements CloudinaryAssetClient {
    private final Cloudinary cloudinary;
    private final HttpClient httpClient;
    private final CloudinaryDiagnostics diagnostics;

    SdkCloudinaryAssetClient(Cloudinary cloudinary) {
        this(cloudinary, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    SdkCloudinaryAssetClient(Cloudinary cloudinary, HttpClient httpClient) {
        this.cloudinary = cloudinary;
        this.httpClient = httpClient;
        this.diagnostics = new CloudinaryDiagnostics(cloudinary);
    }

    @Override
    public UploadResult upload(byte[] bytes, String publicId, String format, String originalFilename)
            throws Exception {
        Map<?, ?> result;
        String phase = "sdk_setup";
        try {
            var uploader = cloudinary.uploader();
            phase = "sdk_call_outcome_unknown";
            result = uploader.upload(bytes, ObjectUtils.asMap(
                "public_id", publicId,
                "format", format,
                "resource_type", "image",
                "type", "authenticated",
                "overwrite", false,
                "unique_filename", false,
                "filename_override", originalFilename,
                "return_error", true));
        } catch (Exception exception) {
            diagnostics.exceptionFailure("upload", phase, exception);
            throw new StorageException("Photo bytes could not be stored", exception, false);
        }
        if (result != null && result.get("error") instanceof Map<?, ?> error) {
            diagnostics.providerFailure("upload", error);
            throw new StorageException("Photo bytes could not be stored", null, false);
        }
        if (result == null) {
            throw new StorageException("Cloudinary returned no upload response", null, false);
        }
        return new UploadResult(text(result.get("public_id")), text(result.get("format")),
                number(result.get("bytes")), text(result.get("resource_type")), text(result.get("type")));
    }

    @Override
    public byte[] read(String publicId, String format) throws Exception {
        HttpRequest request;
        try {
            String url = cloudinary.url()
                    .resourceType("image")
                    .type("authenticated")
                    .format(format)
                    .signed(true)
                    .generate(publicId);
            if (url == null || url.isBlank()) {
                throw new IllegalStateException("Cloudinary SDK returned an empty delivery URL");
            }
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30)).GET().build();
        } catch (Exception exception) {
            diagnostics.readFailure("signed_url_generation", null, publicId, format, exception, null);
            throw new StorageException("Stored photo is unavailable", exception);
        }
        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            diagnostics.readFailure("http_fetch", null, publicId, format, exception, null);
            throw new StorageException("Stored photo is unavailable", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            diagnostics.readFailure("provider_response", response.statusCode(), publicId, format, null,
                    diagnostics.readErrorMessage(response));
            throw new StorageException("Stored photo is unavailable", null);
        }
        if (response.body() == null || response.body().length == 0) {
            diagnostics.readFailure("provider_response", response.statusCode(), publicId, format, null,
                    "Cloudinary returned an empty photo");
            throw new StorageException("Stored photo is unavailable", null);
        }
        return response.body();
    }

    @Override
    public DeleteResult delete(String publicId) throws Exception {
        Map<?, ?> result;
        String phase = "sdk_setup";
        try {
            var uploader = cloudinary.uploader();
            phase = "sdk_call_outcome_unknown";
            result = uploader.destroy(publicId, ObjectUtils.asMap(
                "resource_type", "image",
                "type", "authenticated",
                "invalidate", true,
                "return_error", true));
        } catch (Exception exception) {
            diagnostics.exceptionFailure("delete", phase, exception);
            throw new StorageException("Stored photo could not be cleaned up", exception);
        }
        if (result != null && result.get("error") instanceof Map<?, ?> error) {
            diagnostics.providerFailure("delete", error);
            throw new StorageException("Stored photo could not be cleaned up", null);
        }
        if (result == null) throw new StorageException("Cloudinary returned no cleanup response", null);
        return new DeleteResult(text(result.get("result")));
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : -1;
    }
}
