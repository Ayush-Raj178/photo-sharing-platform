package com.photoshare.gallery;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
@RequestMapping("/api/v1/public/galleries/{shareToken}")
public class PublicGalleryController {
    private final PublicGalleryService service;

    public PublicGalleryController(PublicGalleryService service) {
        this.service = service;
    }

    @PostMapping("/access")
    GalleryDtos.GalleryAccessResponse access(@PathVariable String shareToken,
                                             @Valid @RequestBody GalleryDtos.PinRequest request,
                                             HttpServletRequest servletRequest) {
        return service.access(shareToken, request.pin(), servletRequest.getRemoteAddr());
    }

    @GetMapping
    GalleryDtos.PublicGalleryResponse get(@PathVariable String shareToken,
                                          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                          String authorization,
                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(defaultValue = "24") @Min(1) @Max(100) int pageSize) {
        return service.get(shareToken, authorization, page, pageSize);
    }

    @GetMapping("/photos/{photoId}/content")
    ResponseEntity<Resource> content(@PathVariable String shareToken, @PathVariable long photoId,
                                     @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                     String authorization) {
        PublicGalleryService.PublicContent content = service.content(shareToken, photoId, authorization);
        String extension = content.contentType().equals(MediaType.IMAGE_PNG_VALUE) ? ".png" : ".jpg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("photo-" + content.photoId() + extension).build().toString())
                .body(content.resource());
    }
}
