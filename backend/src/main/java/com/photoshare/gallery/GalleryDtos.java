package com.photoshare.gallery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class GalleryDtos {
    private GalleryDtos() {}

    public record CreateGalleryRequest(@NotBlank @Size(max = 150) String title) {}
    public record SelectionRequest(List<String> photoIds) {}
    public record PinRequest(@NotBlank @Pattern(regexp = "[0-9]{6}") String pin) {}
    public record PublishRequest(List<String> photoIds, @Pattern(regexp = "[0-9]{6}") String pin) {}
    public record ExpiryRequest(Instant expiresAt) {}

    public record GalleryResponse(
            String id,
            String eventId,
            String title,
            GalleryStatus status,
            List<String> photoIds,
            boolean pinSet,
            Instant publishedAt,
            Instant expiresAt,
            String shareUrl
    ) {}

    public record GalleryListResponse(List<GalleryResponse> items) {}
    public record GalleryAccessResponse(String accessToken, String tokenType, long expiresIn) {}

    public record PublicPhoto(String id, String contentPath, int widthPx, int heightPx) {}
    public record PublicGalleryResponse(String title, List<PublicPhoto> photos, int page, int pageSize,
                                        long totalItems, int totalPages, boolean hasNext) {}
}
