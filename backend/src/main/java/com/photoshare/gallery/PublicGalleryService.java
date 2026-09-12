package com.photoshare.gallery;

import com.photoshare.common.ApiException;
import com.photoshare.common.AttemptLimiter;
import com.photoshare.config.PhotoShareProperties;
import com.photoshare.photo.Photo;
import com.photoshare.photo.PhotoRepository;
import com.photoshare.photo.PhotoStatus;
import com.photoshare.photo.storage.PhotoStorage;
import com.photoshare.photo.storage.StorageException;
import com.photoshare.security.JwtService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PublicGalleryService {
    private final GalleryRepository galleries;
    private final GalleryPhotoRepository selections;
    private final PhotoRepository photos;
    private final PhotoStorage storage;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AttemptLimiter limiter;
    private final PhotoShareProperties properties;

    public PublicGalleryService(GalleryRepository galleries, GalleryPhotoRepository selections,
                                PhotoRepository photos, PhotoStorage storage, PasswordEncoder passwordEncoder,
                                JwtService jwtService, AttemptLimiter limiter, PhotoShareProperties properties) {
        this.galleries = galleries;
        this.selections = selections;
        this.photos = photos;
        this.storage = storage;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.limiter = limiter;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public GalleryDtos.GalleryAccessResponse access(String shareToken, String pin, String clientIp) {
        limiter.check("pin:ip:" + clientIp, properties.rateLimit().pinIpAttempts());
        limiter.check("pin:gallery:" + clientIp + ":" + shareToken, properties.rateLimit().pinAttempts());
        Gallery gallery = published(shareToken);
        if (!passwordEncoder.matches(pin, gallery.getPinHash())) {
            throw ApiException.unauthorized("INVALID_GALLERY_ACCESS", "Gallery access is invalid");
        }
        JwtService.Token token = jwtService.issueGallery(gallery.getId(), gallery.getPinVersion());
        return new GalleryDtos.GalleryAccessResponse(token.value(), "Bearer", token.expiresIn());
    }

    @Transactional(readOnly = true)
    public GalleryDtos.PublicGalleryResponse get(String shareToken, String authorization, int page, int pageSize) {
        Gallery gallery = authorizedGallery(shareToken, authorization);
        var selectionPage = selections.findAllByIdGalleryId(gallery.getId(), PageRequest.of(page, pageSize,
                Sort.by(Sort.Order.asc("position"), Sort.Order.asc("id.photoId"))));
        List<GalleryDtos.PublicPhoto> result = selectionPage.getContent().stream()
                .map(selection -> readyPhoto(gallery.getEventId(), selection.getId().getPhotoId()))
                .map(photo -> new GalleryDtos.PublicPhoto(photo.getId().toString(),
                        "/api/v1/public/galleries/" + shareToken + "/photos/" + photo.getId() + "/content",
                        photo.getWidthPx(), photo.getHeightPx()))
                .toList();
        return new GalleryDtos.PublicGalleryResponse(gallery.getTitle(), result, selectionPage.getNumber(),
                selectionPage.getSize(), selectionPage.getTotalElements(), selectionPage.getTotalPages(),
                selectionPage.hasNext());
    }

    @Transactional(readOnly = true)
    public PublicContent content(String shareToken, long photoId, String authorization) {
        Gallery gallery = authorizedGallery(shareToken, authorization);
        if (!selections.existsByIdGalleryIdAndIdPhotoId(gallery.getId(), photoId)) {
            throw ApiException.notFound("PHOTO_NOT_AVAILABLE", "Photo is unavailable");
        }
        Photo photo = readyPhoto(gallery.getEventId(), photoId);
        try {
            return new PublicContent(storage.read(photo.getStorageKey()), photo.getContentType(), photo.getId());
        } catch (StorageException ex) {
            throw ApiException.serviceUnavailable("STORAGE_UNAVAILABLE", "Stored photo is unavailable");
        }
    }

    private Gallery authorizedGallery(String shareToken, String authorization) {
        JwtService.GalleryGrant grant = jwtService.decodeGallery(bearer(authorization));
        Gallery gallery = published(shareToken);
        if (gallery.getId() != grant.galleryId() || gallery.getPinVersion() != grant.pinVersion()) {
            throw ApiException.unauthorized("INVALID_GALLERY_ACCESS", "Gallery access is invalid or expired");
        }
        return gallery;
    }

    private Gallery published(String shareToken) {
        Gallery gallery = galleries.findByShareTokenAndStatus(shareToken, GalleryStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("GALLERY_NOT_FOUND", "Gallery is unavailable"));
        if (gallery.isExpired(java.time.Instant.now())) {
            throw new ApiException(org.springframework.http.HttpStatus.GONE,
                    "GALLERY_EXPIRED", "Gallery has expired.");
        }
        return gallery;
    }

    private Photo readyPhoto(long eventId, long photoId) {
        return photos.findByIdAndEventIdAndStatus(photoId, eventId, PhotoStatus.READY)
                .orElseThrow(() -> ApiException.notFound("PHOTO_NOT_AVAILABLE", "Photo is unavailable"));
    }

    private String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() <= "Bearer ".length()) {
            throw ApiException.unauthorized("INVALID_GALLERY_ACCESS", "Gallery access is invalid or expired");
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    public record PublicContent(Resource resource, String contentType, long photoId) {}
}
