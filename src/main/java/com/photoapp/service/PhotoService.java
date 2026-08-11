package com.photoapp.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.photoapp.dto.PhotoDto;
import com.photoapp.exception.EncryptionException;
import com.photoapp.exception.PhotoNotFoundException;
import com.photoapp.exception.UnsupportedFileTypeException;
import com.photoapp.model.Photo;
import com.photoapp.repository.PhotoRepository;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Core business logic for photo upload, retrieval, and deletion. All binary
 * content (original + thumbnail) is encrypted with AES-256-GCM via
 * {@link EncryptionService} before being written to GridFS, and decrypted
 * on read, streaming end-to-end to avoid loading whole files in memory
 * where practical.
 */
@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"
    );

    private final PhotoRepository photoRepository;
    private final GridFsTemplate gridFsTemplate;
    private final EncryptionService encryptionService;
    private final ThumbnailService thumbnailService;
    private final ExifService exifService;

    public PhotoService(
            PhotoRepository photoRepository,
            GridFsTemplate gridFsTemplate,
            EncryptionService encryptionService,
            ThumbnailService thumbnailService,
            ExifService exifService) {
        this.photoRepository = photoRepository;
        this.gridFsTemplate = gridFsTemplate;
        this.encryptionService = encryptionService;
        this.thumbnailService = thumbnailService;
        this.exifService = exifService;
    }

    /**
     * Result of a single-file upload, including whether the file's content
     * hash matched an already-stored photo (duplicate). Duplicates are
     * still stored, per product requirement.
     */
    public record UploadResult(PhotoDto photoDto, boolean duplicate) {
    }

    /**
     * Processes and stores one uploaded file: validates its MIME type,
     * computes its SHA-256 hash (for duplicate detection), extracts EXIF
     * date, generates a thumbnail, encrypts both the original and the
     * thumbnail with distinct random IVs, and persists them to GridFS plus
     * a {@link Photo} metadata document.
     */
    public UploadResult uploadPhoto(MultipartFile file, String uploadedByUserId) {
        var mimeType = file.getContentType();
        if (mimeType == null || !SUPPORTED_MIME_TYPES.contains(mimeType.toLowerCase())) {
            throw new UnsupportedFileTypeException("Unsupported file type: " + mimeType);
        }

        byte[] originalBytes;
        try {
            originalBytes = file.getBytes();
        } catch (IOException e) {
            throw new EncryptionException("Failed to read uploaded file", e);
        }

        var sha256 = sha256Hex(originalBytes);
        var existing = photoRepository.findBySha256Hash(sha256);
        var isDuplicate = existing.isPresent();
        if (isDuplicate) {
            log.info("Duplicate upload detected (sha256 match) for file '{}'", file.getOriginalFilename());
        }

        var takenAt = exifService.extractTakenAt(originalBytes).orElse(Instant.now());

        byte[] thumbnailBytes;
        try {
            thumbnailBytes = thumbnailService.generateThumbnail(originalBytes);
        } catch (IOException e) {
            throw new EncryptionException("Failed to generate thumbnail", e);
        }

        var originalIv = encryptionService.generateIv();
        var thumbnailIv = encryptionService.generateIv();

        var encryptedOriginal = encryptionService.encrypt(originalBytes, originalIv);
        var encryptedThumbnail = encryptionService.encrypt(thumbnailBytes, thumbnailIv);

        ObjectId originalFileId;
        ObjectId thumbnailFileId;
        try (var originalStream = new ByteArrayInputStream(encryptedOriginal);
             var thumbnailStream = new ByteArrayInputStream(encryptedThumbnail)) {
            originalFileId = gridFsTemplate.store(originalStream, file.getOriginalFilename(), "application/octet-stream");
            thumbnailFileId = gridFsTemplate.store(thumbnailStream, "thumb_" + file.getOriginalFilename(), "application/octet-stream");
        } catch (IOException e) {
            throw new EncryptionException("Failed to store encrypted file in GridFS", e);
        }

        var photo = Photo.builder()
                .gridFsFileId(originalFileId.toHexString())
                .thumbnailGridFsFileId(thumbnailFileId.toHexString())
                .uploadedBy(uploadedByUserId)
                .originalFilename(file.getOriginalFilename())
                .mimeType(mimeType)
                .sizeBytes(file.getSize())
                .sha256Hash(sha256)
                .takenAt(takenAt)
                .uploadedAt(Instant.now())
                .isFavorite(false)
                .isEncrypted(true)
                .encryptionIv(EncryptionService.toBase64(originalIv))
                .thumbnailEncryptionIv(EncryptionService.toBase64(thumbnailIv))
                .build();

        var saved = photoRepository.save(photo);
        return new UploadResult(PhotoDto.from(saved, isDuplicate), isDuplicate);
    }

    public List<UploadResult> uploadPhotos(List<MultipartFile> files, String uploadedByUserId) {
        return files.stream().map(f -> uploadPhoto(f, uploadedByUserId)).toList();
    }

    public Page<PhotoDto> listPhotos(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "takenAt"));
        return photoRepository.findAllByOrderByTakenAtDesc(pageable).map(PhotoDto::from);
    }

    public PhotoDto getPhotoMetadata(String id) {
        return PhotoDto.from(getPhotoOrThrow(id));
    }

    /**
     * Streams the decrypted original content of a photo to the given
     * consumer. The GridFS download stream and the decrypting stream are
     * both closed once {@code consumer} completes, avoiding a full
     * in-memory copy of the file.
     */
    public DecryptedContent getDecryptedOriginal(String id) {
        var photo = getPhotoOrThrow(id);
        var iv = EncryptionService.fromBase64(photo.getEncryptionIv());
        var gridFsSource = openGridFsStream(photo.getGridFsFileId());
        var decrypted = encryptionService.decryptingInputStream(gridFsSource, iv);
        return new DecryptedContent(decrypted, photo.getMimeType(), photo.getOriginalFilename());
    }

    public DecryptedContent getDecryptedThumbnail(String id) {
        var photo = getPhotoOrThrow(id);
        var iv = EncryptionService.fromBase64(photo.getThumbnailEncryptionIv());
        var gridFsSource = openGridFsStream(photo.getThumbnailGridFsFileId());
        var decrypted = encryptionService.decryptingInputStream(gridFsSource, iv);
        return new DecryptedContent(decrypted, "image/jpeg", "thumb_" + photo.getOriginalFilename());
    }

    public record DecryptedContent(InputStream stream, String mimeType, String filename) {
    }

    public void deletePhoto(String id) {
        var photo = getPhotoOrThrow(id);
        gridFsTemplate.delete(Query.query(Criteria.where("_id").is(new ObjectId(photo.getGridFsFileId()))));
        gridFsTemplate.delete(Query.query(Criteria.where("_id").is(new ObjectId(photo.getThumbnailGridFsFileId()))));
        photoRepository.deleteById(id);
    }

    public PhotoDto toggleFavorite(String id) {
        var photo = getPhotoOrThrow(id);
        photo.setFavorite(!photo.isFavorite());
        var saved = photoRepository.save(photo);
        return PhotoDto.from(saved);
    }

    private Photo getPhotoOrThrow(String id) {
        return photoRepository.findById(id).orElseThrow(() -> new PhotoNotFoundException(id));
    }

    private InputStream openGridFsStream(String gridFsId) {
        GridFSFile file = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(new ObjectId(gridFsId))));
        if (file == null) {
            throw new EncryptionException("Referenced GridFS file is missing", new IOException("GridFS file not found: " + gridFsId));
        }
        try {
            return gridFsTemplate.getResource(file).getInputStream();
        } catch (IOException e) {
            throw new EncryptionException("Failed to open GridFS resource", e);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
