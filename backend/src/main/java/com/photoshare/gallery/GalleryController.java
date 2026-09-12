package com.photoshare.gallery;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events/{eventId}/galleries")
public class GalleryController {
    private final GalleryService service;

    public GalleryController(GalleryService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GalleryDtos.GalleryResponse create(@PathVariable long eventId,
                                       @Valid @RequestBody GalleryDtos.CreateGalleryRequest request) {
        return service.create(eventId, request);
    }

    @GetMapping
    GalleryDtos.GalleryListResponse list(@PathVariable long eventId) {
        return service.list(eventId);
    }

    @GetMapping("/{galleryId}")
    GalleryDtos.GalleryResponse get(@PathVariable long eventId, @PathVariable long galleryId) {
        return service.get(eventId, galleryId);
    }

    @PutMapping("/{galleryId}/photos")
    GalleryDtos.GalleryResponse replaceSelection(@PathVariable long eventId, @PathVariable long galleryId,
                                                  @RequestBody GalleryDtos.SelectionRequest request) {
        return service.replaceDraftSelection(eventId, galleryId, request);
    }

    @PutMapping("/{galleryId}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setPin(@PathVariable long eventId, @PathVariable long galleryId,
                @Valid @RequestBody GalleryDtos.PinRequest request) {
        service.setDraftPin(eventId, galleryId, request.pin());
    }

    @PutMapping("/{galleryId}/expiry")
    GalleryDtos.GalleryResponse setExpiry(@PathVariable long eventId, @PathVariable long galleryId,
                                          @RequestBody GalleryDtos.ExpiryRequest request) {
        return service.setExpiry(eventId, galleryId, request.expiresAt());
    }

    @PostMapping("/{galleryId}/publish")
    GalleryDtos.GalleryResponse publish(@PathVariable long eventId, @PathVariable long galleryId,
                                        @Valid @RequestBody(required = false) GalleryDtos.PublishRequest request) {
        return service.publish(eventId, galleryId,
                request == null ? new GalleryDtos.PublishRequest(null, null) : request);
    }
}
