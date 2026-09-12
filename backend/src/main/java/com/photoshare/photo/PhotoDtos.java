package com.photoshare.photo;

import java.time.Instant;
import java.util.List;

public final class PhotoDtos {
    private PhotoDtos() {}

    public record PhotoResponse(
            String id,
            String eventId,
            String uploadedBy,
            String filename,
            String contentType,
            long fileSizeBytes,
            int widthPx,
            int heightPx,
            Instant createdAt,
            String contentPath
    ) {
        public static PhotoResponse from(Photo photo) {
            return new PhotoResponse(
                    photo.getId().toString(),
                    photo.getEventId().toString(),
                    photo.getUploadedBy().toString(),
                    photo.getOriginalFilename(),
                    photo.getContentType(),
                    photo.getFileSizeBytes(),
                    photo.getWidthPx(),
                    photo.getHeightPx(),
                    photo.getCreatedAt(),
                    "/api/v1/events/" + photo.getEventId() + "/photos/" + photo.getId() + "/content"
            );
        }
    }

    public record PhotoListResponse(
            List<PhotoResponse> items,
            int page,
            int pageSize,
            long totalItems,
            int totalPages,
            boolean hasNext
    ) {}

    public record UploadResult(
            int index,
            String filename,
            String status,
            PhotoResponse photo,
            UploadError error
    ) {
        static UploadResult uploaded(int index, String filename, Photo photo) {
            return new UploadResult(index, filename, "UPLOADED", PhotoResponse.from(photo), null);
        }

        static UploadResult failed(int index, String filename, String code, String message) {
            return new UploadResult(index, filename, "FAILED", null, new UploadError(code, message));
        }
    }

    public record UploadError(String code, String message) {}
    public record UploadResponse(List<UploadResult> results) {}
}
