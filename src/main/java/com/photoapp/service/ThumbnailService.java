package com.photoapp.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Generates JPEG thumbnails (max width 400px, aspect ratio preserved) from
 * uploaded photo content using Thumbnailator.
 */
@Service
public class ThumbnailService {

    private static final int MAX_WIDTH = 400;

    /**
     * Produces a JPEG thumbnail no wider than 400px from the given source
     * image bytes. The source format is auto-detected by Thumbnailator
     * (JPEG/PNG/WEBP); HEIC is not guaranteed to be supported by the
     * underlying ImageIO plugins available at runtime.
     *
     * @throws IOException if the source bytes cannot be decoded as an image
     */
    public byte[] generateThumbnail(byte[] sourceBytes) throws IOException {
        try (var input = new ByteArrayInputStream(sourceBytes);
             var output = new ByteArrayOutputStream()) {
            Thumbnails.of(input)
                    .size(MAX_WIDTH, MAX_WIDTH)
                    .keepAspectRatio(true)
                    .outputFormat("jpg")
                    .toOutputStream(output);
            return output.toByteArray();
        }
    }
}
