package com.photoshare.photo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PhotoRecordService {
    private final PhotoRepository photos;

    public PhotoRecordService(PhotoRepository photos) {
        this.photos = photos;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Photo createPending(Long eventId, Long uploadedBy, PhotoValidation.ValidatedImage image, String key) {
        return photos.saveAndFlush(new Photo(eventId, uploadedBy, image.filename(), key, image.contentType(),
                image.bytes().length, image.width(), image.height(), Instant.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Photo markReady(Long photoId) {
        Photo photo = photos.findById(photoId).orElseThrow();
        photo.markReady(Instant.now());
        return photos.saveAndFlush(photo);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long photoId, String failureCode) {
        photos.findById(photoId).ifPresent(photo -> {
            photo.markFailed(failureCode, Instant.now());
            photos.saveAndFlush(photo);
        });
    }
}

