package com.photoapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Metadata document describing a photo. The actual binary content (original
 * and thumbnail) is stored encrypted in GridFS; this document only holds
 * references ({@code gridFsFileId}, {@code thumbnailGridFsFileId}) plus
 * metadata required to list, search and decrypt the files.
 */
@Document(collection = "photos")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Photo {

    @Id
    private String id;

    /** GridFS file id of the encrypted original file. */
    private String gridFsFileId;

    /** GridFS file id of the encrypted thumbnail. */
    private String thumbnailGridFsFileId;

    /** Id of the {@link User} who uploaded this photo. */
    private String uploadedBy;

    private String originalFilename;

    private String mimeType;

    private long sizeBytes;

    /** SHA-256 hash of the original (plaintext) file content, used for duplicate detection. */
    @Indexed
    private String sha256Hash;

    /** Date the photo was taken, extracted from EXIF if available, otherwise upload date. */
    private Instant takenAt;

    private Instant uploadedAt;

    private boolean isFavorite;

    private boolean isEncrypted;

    /** Base64-encoded IV used to encrypt the original file. Thumbnail uses its own IV (see thumbnailEncryptionIv). */
    private String encryptionIv;

    /** Base64-encoded IV used to encrypt the thumbnail file. */
    private String thumbnailEncryptionIv;
}
