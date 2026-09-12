package com.photoshare.photo;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
@RequestMapping("/api/v1/events/{eventId}/photos")
public class PhotoController {
    private final PhotoService service;

    public PhotoController(PhotoService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    PhotoDtos.UploadResponse upload(@PathVariable long eventId,
                                    @RequestPart("files") List<MultipartFile> files) {
        return service.upload(eventId, files);
    }

    @GetMapping
    PhotoDtos.PhotoListResponse list(@PathVariable long eventId,
                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                     @RequestParam(defaultValue = "24") @Min(1) @Max(100) int pageSize,
                                     @RequestParam(defaultValue = "") @Size(max = 255) String search,
                                     @RequestParam(required = false) @Positive Long uploaderId,
                                     @RequestParam(required = false) Boolean selected) {
        return service.list(eventId, page, pageSize, search, uploaderId, selected);
    }

    @GetMapping("/{photoId}")
    PhotoDtos.PhotoResponse get(@PathVariable long eventId, @PathVariable long photoId) {
        return service.get(eventId, photoId);
    }

    @GetMapping("/{photoId}/content")
    ResponseEntity<Resource> content(@PathVariable long eventId, @PathVariable long photoId) {
        PhotoService.PhotoContent content = service.content(eventId, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.filename()).build().toString())
                .body(content.resource());
    }
}
