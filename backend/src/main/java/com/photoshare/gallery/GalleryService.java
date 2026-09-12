package com.photoshare.gallery;

import com.photoshare.common.ApiException;
import com.photoshare.config.PhotoShareProperties;
import com.photoshare.event.EventAccessService;
import com.photoshare.photo.Photo;
import com.photoshare.photo.PhotoRepository;
import com.photoshare.photo.PhotoStatus;
import com.photoshare.security.CurrentUser;
import com.photoshare.user.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GalleryService {
    private final GalleryRepository galleries;
    private final GalleryPhotoRepository selections;
    private final PhotoRepository photos;
    private final EventAccessService eventAccess;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;
    private final PhotoShareProperties properties;
    private final SecureRandom random = new SecureRandom();

    public GalleryService(GalleryRepository galleries, GalleryPhotoRepository selections,
                          PhotoRepository photos, EventAccessService eventAccess, CurrentUser currentUser,
                          PasswordEncoder passwordEncoder, PhotoShareProperties properties) {
        this.galleries = galleries;
        this.selections = selections;
        this.photos = photos;
        this.eventAccess = eventAccess;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public GalleryDtos.GalleryResponse create(long eventId, GalleryDtos.CreateGalleryRequest request) {
        CurrentUser.Principal principal = requireAdmin();
        eventAccess.requireOwned(eventId, principal);
        if (!galleries.findAllByEventIdOrderByCreatedAtAsc(eventId).isEmpty()) {
            throw ApiException.conflict("GALLERY_EXISTS", "An event gallery already exists");
        }
        Gallery gallery = galleries.save(new Gallery(eventId, request.title().trim(), randomToken(), Instant.now()));
        return response(gallery);
    }

    @Transactional(readOnly = true)
    public GalleryDtos.GalleryListResponse list(long eventId) {
        CurrentUser.Principal principal = requireAdmin();
        eventAccess.requireOwned(eventId, principal);
        return new GalleryDtos.GalleryListResponse(galleries.findAllByEventIdOrderByCreatedAtAsc(eventId).stream()
                .map(this::response).toList());
    }

    @Transactional(readOnly = true)
    public GalleryDtos.GalleryResponse get(long eventId, long galleryId) {
        CurrentUser.Principal principal = requireAdmin();
        eventAccess.requireOwned(eventId, principal);
        return response(requireGallery(eventId, galleryId));
    }

    @Transactional
    public GalleryDtos.GalleryResponse replaceDraftSelection(long eventId, long galleryId,
                                                              GalleryDtos.SelectionRequest request) {
        CurrentUser.Principal principal = requireAdmin();
        eventAccess.requireOwned(eventId, principal);
        Gallery gallery = requireLocked(eventId, galleryId);
        requireDraft(gallery);
        replaceSelection(gallery, parsePhotoIds(request.photoIds()));
        return response(gallery);
    }

    @Transactional
    public void setDraftPin(long eventId, long galleryId, String pin) {
        CurrentUser.Principal principal = requireAdmin();
        eventAccess.requireOwned(eventId, principal);
        Gallery gallery = requireLocked(eventId, galleryId);
        requireDraft(gallery);
        gallery.setPin(passwordEncoder.encode(pin), Instant.now());
    }

    @Transactional
    public GalleryDtos.GalleryResponse setExpiry(long eventId, long galleryId, Instant expiresAt) {
        CurrentUser.Principal principal = requireAdmin();
        eventAccess.requireOwned(eventId, principal);
        Gallery gallery = requireLocked(eventId, galleryId);
        gallery.setExpiresAt(expiresAt, Instant.now());
        return response(gallery);
    }

    @Transactional
    public GalleryDtos.GalleryResponse publish(long eventId, long galleryId, GalleryDtos.PublishRequest request) {
        CurrentUser.Principal principal = requireAdmin();
        eventAccess.requireOwned(eventId, principal);
        Gallery gallery = requireLocked(eventId, galleryId);

        if (gallery.getStatus() == GalleryStatus.PUBLISHED
                && request.photoIds() == null && request.pin() == null) {
            return response(gallery);
        }
        if (request.photoIds() != null) {
            replaceSelection(gallery, parsePhotoIds(request.photoIds()));
        }
        if (request.pin() != null) {
            gallery.setPin(passwordEncoder.encode(request.pin()), Instant.now());
        }
        if (gallery.getPinHash() == null) {
            throw ApiException.conflict("PIN_REQUIRED", "A gallery PIN is required");
        }
        if (selections.findAllByIdGalleryIdOrderByPositionAsc(galleryId).isEmpty()) {
            throw ApiException.conflict("EMPTY_GALLERY", "At least one photo must be selected");
        }
        gallery.publish(Instant.now());
        return response(gallery);
    }

    private void replaceSelection(Gallery gallery, List<Long> photoIds) {
        if (photoIds.isEmpty()) {
            selections.deleteAllByIdGalleryId(gallery.getId());
            selections.flush();
            return;
        }
        List<Photo> found = photos.findAllByIdInAndEventIdAndStatus(photoIds, gallery.getEventId(), PhotoStatus.READY);
        if (found.size() != photoIds.size()) {
            throw ApiException.notFound("PHOTO_NOT_AVAILABLE", "One or more photos are unavailable");
        }
        Map<Long, Photo> byId = found.stream().collect(Collectors.toMap(Photo::getId, Function.identity()));
        Instant now = Instant.now();
        List<GalleryPhoto> replacements = new ArrayList<>();
        for (int index = 0; index < photoIds.size(); index++) {
            Long photoId = photoIds.get(index);
            if (!byId.containsKey(photoId)) {
                throw ApiException.notFound("PHOTO_NOT_AVAILABLE", "One or more photos are unavailable");
            }
            replacements.add(new GalleryPhoto(gallery.getId(), photoId, gallery.getEventId(), index + 1, now));
        }
        selections.deleteAllByIdGalleryId(gallery.getId());
        selections.flush();
        selections.saveAll(replacements);
        selections.flush();
    }

    private List<Long> parsePhotoIds(List<String> values) {
        if (values == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "photoIds is required");
        }
        List<Long> result = new ArrayList<>();
        Set<Long> unique = new HashSet<>();
        for (String value : values) {
            try {
                long id = Long.parseLong(value);
                if (id <= 0 || !unique.add(id)) throw new NumberFormatException();
                result.add(id);
            } catch (NumberFormatException ex) {
                throw ApiException.badRequest("VALIDATION_ERROR",
                        "photoIds must contain unique positive decimal values");
            }
        }
        return result;
    }

    private GalleryDtos.GalleryResponse response(Gallery gallery) {
        List<String> photoIds = selections.findAllByIdGalleryIdOrderByPositionAsc(gallery.getId()).stream()
                .map(selection -> selection.getId().getPhotoId().toString()).toList();
        String shareUrl = gallery.getStatus() == GalleryStatus.PUBLISHED
                ? properties.frontendOrigin().replaceAll("/+$", "") + "/gallery/" + gallery.getShareToken()
                : null;
        return new GalleryDtos.GalleryResponse(gallery.getId().toString(), gallery.getEventId().toString(),
                gallery.getTitle(), gallery.getStatus(), photoIds, gallery.getPinHash() != null,
                gallery.getPublishedAt(), gallery.getExpiresAt(), shareUrl);
    }

    private Gallery requireGallery(long eventId, long galleryId) {
        return galleries.findByIdAndEventId(galleryId, eventId)
                .orElseThrow(() -> ApiException.notFound("GALLERY_NOT_FOUND", "Gallery is unavailable"));
    }

    private Gallery requireLocked(long eventId, long galleryId) {
        return galleries.findLocked(galleryId, eventId)
                .orElseThrow(() -> ApiException.notFound("GALLERY_NOT_FOUND", "Gallery is unavailable"));
    }

    private void requireDraft(Gallery gallery) {
        if (gallery.getStatus() != GalleryStatus.DRAFT) {
            throw ApiException.conflict("GALLERY_ALREADY_PUBLISHED",
                    "Published gallery changes must use re-publication");
        }
    }

    private CurrentUser.Principal requireAdmin() {
        CurrentUser.Principal principal = currentUser.require();
        if (principal.role() != UserRole.ADMIN) {
            throw ApiException.forbidden("FORBIDDEN", "Operation is not permitted");
        }
        return principal;
    }

    private String randomToken() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
