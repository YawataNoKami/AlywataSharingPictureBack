package com.photoapp.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

/**
 * Extracts the "date taken" EXIF field from an image, when present.
 */
@Service
public class ExifService {

    private static final Logger log = LoggerFactory.getLogger(ExifService.class);

    /**
     * Attempts to read the EXIF "date/time original" tag from the given
     * image bytes.
     *
     * @return the capture instant, or {@link Optional#empty()} if the image
     *         has no EXIF data, no date tag, or metadata parsing failed.
     */
    public Optional<Instant> extractTakenAt(byte[] imageBytes) {
        try (var input = new ByteArrayInputStream(imageBytes)) {
            Metadata metadata = ImageMetadataReader.readMetadata(input);
            ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (directory == null) {
                return Optional.empty();
            }
            var date = directory.getDateOriginal();
            return Optional.ofNullable(date).map(java.util.Date::toInstant);
        } catch (ImageProcessingException | IOException e) {
            log.debug("Could not extract EXIF metadata: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
