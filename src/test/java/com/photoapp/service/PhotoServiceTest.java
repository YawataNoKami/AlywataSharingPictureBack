package com.photoapp.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.photoapp.model.Photo;
import com.photoapp.repository.PhotoRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private GridFsTemplate gridFsTemplate;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private ThumbnailService thumbnailService;

    @Mock
    private ExifService exifService;

    private PhotoService photoService;

    @BeforeEach
    void setUp() {
        photoService = new PhotoService(photoRepository, gridFsTemplate, encryptionService, thumbnailService, exifService);
    }

    @Test
    void uploadPhoto_withContentMatchingExistingHash_isFlaggedAsDuplicateButStillStored() throws Exception {
        var fileBytes = "fake-jpeg-bytes".getBytes();
        var file = new MockMultipartFile("files", "photo.jpg", "image/jpeg", fileBytes);

        var existingPhoto = Photo.builder().id("existing-id").sha256Hash("irrelevant-for-mock").build();
        when(photoRepository.findBySha256Hash(any())).thenReturn(Optional.of(existingPhoto));

        when(exifService.extractTakenAt(any())).thenReturn(Optional.of(Instant.now()));
        when(thumbnailService.generateThumbnail(any())).thenReturn("thumb-bytes".getBytes());
        when(encryptionService.generateIv()).thenReturn(new byte[12]);
        when(encryptionService.encrypt(any(), any())).thenReturn("encrypted".getBytes());

        when(gridFsTemplate.store(any(), any(String.class), any(String.class)))
                .thenReturn(new ObjectId())
                .thenReturn(new ObjectId());

        when(photoRepository.save(any(Photo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = photoService.uploadPhoto(file, "user-1");

        assertThat(result.duplicate()).isTrue();
        assertThat(result.photoDto().duplicate()).isTrue();
        verify(photoRepository).save(any(Photo.class));
    }

    @Test
    void uploadPhoto_withNewContent_isNotFlaggedAsDuplicate() throws Exception {
        var fileBytes = "unique-jpeg-bytes".getBytes();
        var file = new MockMultipartFile("files", "photo2.jpg", "image/jpeg", fileBytes);

        when(photoRepository.findBySha256Hash(any())).thenReturn(Optional.empty());
        when(exifService.extractTakenAt(any())).thenReturn(Optional.empty());
        when(thumbnailService.generateThumbnail(any())).thenReturn("thumb-bytes".getBytes());
        when(encryptionService.generateIv()).thenReturn(new byte[12]);
        when(encryptionService.encrypt(any(), any())).thenReturn("encrypted".getBytes());

        when(gridFsTemplate.store(any(), any(String.class), any(String.class)))
                .thenReturn(new ObjectId())
                .thenReturn(new ObjectId());

        when(photoRepository.save(any(Photo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = photoService.uploadPhoto(file, "user-1");

        assertThat(result.duplicate()).isFalse();
        assertThat(result.photoDto().duplicate()).isFalse();
    }

    @Test
    void uploadPhoto_withUnsupportedMimeType_throwsException() {
        var file = new MockMultipartFile("files", "document.pdf", "application/pdf", "data".getBytes());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.photoapp.exception.UnsupportedFileTypeException.class,
                () -> photoService.uploadPhoto(file, "user-1"));

        verify(photoRepository, never()).save(any());
    }

    @Test
    void deletePhoto_removesGridFsFilesAndMetadataDocument() {
        var photo = Photo.builder()
                .id("photo-1")
                .gridFsFileId(new ObjectId().toHexString())
                .thumbnailGridFsFileId(new ObjectId().toHexString())
                .build();

        when(photoRepository.findById("photo-1")).thenReturn(Optional.of(photo));

        photoService.deletePhoto("photo-1");

        verify(gridFsTemplate, times(2)).delete(any());
        verify(photoRepository).deleteById("photo-1");
    }
}
