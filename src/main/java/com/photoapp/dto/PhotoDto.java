package com.photoapp.dto;

import java.time.Instant;

/**
 * Public representation of a {@link com.photoapp.model.Photo}. Never exposes
 * GridFS ids, IVs or any encryption-related field.
 */
public record PhotoDto(
        String id,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        Instant takenAt,
        Instant uploadedAt,
        boolean isFavorite,
        boolean duplicate
) {

    public static PhotoDto from(com.photoapp.model.Photo photo) {
        return new PhotoDto(
                photo.getId(),
                photo.getOriginalFilename(),
                photo.getMimeType(),
                photo.getSizeBytes(),
                photo.getTakenAt(),
                photo.getUploadedAt(),
                photo.isFavorite(),
                false
        );
    }

    public static PhotoDto from(com.photoapp.model.Photo photo, boolean duplicate) {
        return new PhotoDto(
                photo.getId(),
                photo.getOriginalFilename(),
                photo.getMimeType(),
                photo.getSizeBytes(),
                photo.getTakenAt(),
                photo.getUploadedAt(),
                photo.isFavorite(),
                duplicate
        );
    }
}
