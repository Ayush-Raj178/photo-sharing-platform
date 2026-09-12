package com.photoshare.gallery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface GalleryPhotoRepository extends JpaRepository<GalleryPhoto, GalleryPhotoId> {
    List<GalleryPhoto> findAllByIdGalleryIdOrderByPositionAsc(Long galleryId);
    boolean existsByIdGalleryIdAndIdPhotoId(Long galleryId, Long photoId);
    void deleteAllByIdGalleryId(Long galleryId);
    Page<GalleryPhoto> findAllByIdGalleryId(Long galleryId, Pageable pageable);
}
