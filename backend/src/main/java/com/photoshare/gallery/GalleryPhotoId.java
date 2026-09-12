package com.photoshare.gallery;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class GalleryPhotoId implements Serializable {
    @Column(name = "gallery_id")
    private Long galleryId;
    @Column(name = "photo_id")
    private Long photoId;

    protected GalleryPhotoId() {}

    public GalleryPhotoId(Long galleryId, Long photoId) {
        this.galleryId = galleryId;
        this.photoId = photoId;
    }

    public Long getGalleryId() { return galleryId; }
    public Long getPhotoId() { return photoId; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof GalleryPhotoId that
                && Objects.equals(galleryId, that.galleryId) && Objects.equals(photoId, that.photoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(galleryId, photoId);
    }
}

