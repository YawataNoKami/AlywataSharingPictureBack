package com.photoapp.controller;

import com.photoapp.dto.PagedResponse;
import com.photoapp.dto.PhotoDto;
import com.photoapp.security.UserPrincipal;
import com.photoapp.service.PhotoService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    /**
     * Uploads one or more photos. Each file is validated, hashed (for
     * duplicate detection), thumbnailed and encrypted before being stored.
     * Duplicates are still accepted; {@code PhotoDto.duplicate} flags them
     * in the response so the client can warn the user.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<PhotoDto>> upload(
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal UserPrincipal principal) {
        var results = photoService.uploadPhotos(files, principal.getId());
        var dtos = results.stream().map(PhotoService.UploadResult::photoDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<PhotoDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        var result = photoService.listPhotos(page, size);
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PhotoDto> getMetadata(@PathVariable String id) {
        return ResponseEntity.ok(photoService.getPhotoMetadata(id));
    }

    /**
     * Streams the decrypted original file content. The response is
     * streamed directly from the decrypting cipher stream wrapped around
     * the GridFS download stream, avoiding a full in-memory buffer.
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> getContent(@PathVariable String id) {
        var content = photoService.getDecryptedOriginal(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + content.filename() + "\"")
                .body(new InputStreamResource(content.stream()));
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<InputStreamResource> getThumbnail(@PathVariable String id) {
        var content = photoService.getDecryptedThumbnail(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + content.filename() + "\"")
                .body(new InputStreamResource(content.stream()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        photoService.deletePhoto(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/favorite")
    public ResponseEntity<PhotoDto> toggleFavorite(@PathVariable String id) {
        return ResponseEntity.ok(photoService.toggleFavorite(id));
    }
}
