package com.photoshare.photo;

import com.photoshare.common.ApiException;
import com.photoshare.config.PhotoShareProperties;
import com.photoshare.event.EventAccessService;
import com.photoshare.photo.storage.PhotoStorage;
import com.photoshare.photo.storage.StorageException;
import com.photoshare.security.CurrentUser;
import com.photoshare.user.UserRole;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PhotoService {
    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);
    private final PhotoRepository photos;
    private final PhotoRecordService records;
    private final PhotoStorage storage;
    private final PhotoValidation validation;
    private final EventAccessService eventAccess;
    private final CurrentUser currentUser;
    private final PhotoShareProperties properties;

    public PhotoService(PhotoRepository photos, PhotoRecordService records, PhotoStorage storage,
                        PhotoValidation validation, EventAccessService eventAccess, CurrentUser currentUser,
                        PhotoShareProperties properties) {
        this.photos = photos;
        this.records = records;
        this.storage = storage;
        this.validation = validation;
        this.eventAccess = eventAccess;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    public PhotoDtos.UploadResponse upload(long eventId, List<MultipartFile> files) {
        CurrentUser.Principal principal = currentUser.require();
        eventAccess.requireAssigned(eventId, principal);
        if (files == null || files.isEmpty() || files.size() > properties.storage().maxFilesPerUpload()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Upload must contain an allowed number of files");
        }
        long aggregate = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (aggregate > properties.storage().maxRequestSizeBytes()) {
            throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "REQUEST_TOO_LARGE", "Upload request is too large");
        }

        List<PhotoDtos.UploadResult> results = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            String safeName = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
            Photo pending = null;
            String key = null;
            try {
                PhotoValidation.ValidatedImage image = validation.validate(file);
                safeName = image.filename();
                key = "events/" + eventId + "/photos/" + UUID.randomUUID() + "." + image.extension();
                pending = records.createPending(eventId, principal.id(), image, key);
                PhotoStorage.StoredPhoto stored = storage.put(
                        key, image.bytes(), image.contentType(), image.filename());
                if (!key.equals(stored.storageKey())
                        || !image.contentType().equals(stored.contentType())
                        || image.bytes().length != stored.fileSizeBytes()) {
                    throw new StorageException("Stored photo metadata did not match the validated upload", null);
                }
                Photo ready = records.markReady(pending.getId());
                results.add(PhotoDtos.UploadResult.uploaded(index, safeName, ready));
            } catch (ApiException ex) {
                results.add(PhotoDtos.UploadResult.failed(index, safeName, ex.code(), ex.getMessage()));
            } catch (StorageException ex) {
                log.warn("Photo storage write failed [provider={}, eventId={}, photoId={}, storageError={}, rootType={}]",
                        properties.storage().driver(), eventId,
                        pending == null ? "not-created" : pending.getId(),
                        ex.getMessage(), ex.rootCauseType());
                if (!ex.cleanupAllowed()) {
                    log.warn("Photo storage cleanup skipped [eventId={}, photoId={}, reason=no_confirmed_asset]",
                            eventId, pending == null ? "not-created" : pending.getId());
                }
                failAndClean(pending, ex.cleanupAllowed() ? key : null, "STORAGE_WRITE_FAILED");
                results.add(PhotoDtos.UploadResult.failed(index, safeName,
                        "STORAGE_WRITE_FAILED", "Photo could not be stored"));
            } catch (RuntimeException ex) {
                failAndClean(pending, key, "METADATA_SAVE_FAILED");
                results.add(PhotoDtos.UploadResult.failed(index, safeName,
                        "METADATA_SAVE_FAILED", "Photo metadata could not be saved"));
            }
        }
        return new PhotoDtos.UploadResponse(results);
    }

    @Transactional(readOnly = true)
    public PhotoDtos.PhotoListResponse list(long eventId, int page, int pageSize, String search,
                                             Long uploaderId, Boolean selected) {
        CurrentUser.Principal principal = currentUser.require();
        eventAccess.requireReadable(eventId, principal);
        PageRequest request = PageRequest.of(page, pageSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Photo> result;
        if (principal.role() == UserRole.ADMIN) {
            result = photos.searchAdminPhotos(eventId, PhotoStatus.READY,
                    search == null ? "" : search.trim(), uploaderId, selected, request);
        } else {
            if ((search != null && !search.isBlank()) || uploaderId != null || selected != null) {
                throw ApiException.forbidden("FORBIDDEN", "Photo filters are available to Admins only");
            }
            result = photos.findAllByEventIdAndUploadedByAndStatus(
                    eventId, principal.id(), PhotoStatus.READY, request);
        }
        return new PhotoDtos.PhotoListResponse(result.getContent().stream()
                .map(PhotoDtos.PhotoResponse::from).toList(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    @Transactional(readOnly = true)
    public PhotoDtos.PhotoResponse get(long eventId, long photoId) {
        return PhotoDtos.PhotoResponse.from(requireReadablePhoto(eventId, photoId));
    }

    @Transactional(readOnly = true)
    public PhotoContent content(long eventId, long photoId) {
        Photo photo = requireReadablePhoto(eventId, photoId);
        try {
            return new PhotoContent(storage.read(photo.getStorageKey()), photo.getContentType(),
                    photo.getOriginalFilename());
        } catch (StorageException ex) {
            throw ApiException.serviceUnavailable("STORAGE_UNAVAILABLE", "Stored photo is unavailable");
        }
    }

    @Transactional(readOnly = true)
    public Photo requireReady(long eventId, long photoId) {
        return photos.findByIdAndEventIdAndStatus(photoId, eventId, PhotoStatus.READY)
                .orElseThrow(() -> ApiException.notFound("PHOTO_NOT_AVAILABLE", "Photo is unavailable"));
    }

    private Photo requireReadablePhoto(long eventId, long photoId) {
        CurrentUser.Principal principal = currentUser.require();
        eventAccess.requireReadable(eventId, principal);
        Photo photo = requireReady(eventId, photoId);
        if (principal.role() == UserRole.TEAM_MEMBER && photo.getUploadedBy() != principal.id()) {
            throw ApiException.notFound("PHOTO_NOT_AVAILABLE", "Photo is unavailable");
        }
        return photo;
    }

    private void failAndClean(Photo pending, String key, String code) {
        if (key != null) {
            try {
                storage.delete(key);
            } catch (StorageException exception) {
                log.warn("Photo storage cleanup failed for photo record {}",
                        pending == null ? "not-created" : pending.getId());
            }
        }
        if (pending != null) {
            try {
                records.markFailed(pending.getId(), code);
            } catch (RuntimeException exception) {
                log.warn("Photo failure status could not be recorded for photo record {}", pending.getId());
            }
        }
    }

    public record PhotoContent(Resource resource, String contentType, String filename) {}
}
