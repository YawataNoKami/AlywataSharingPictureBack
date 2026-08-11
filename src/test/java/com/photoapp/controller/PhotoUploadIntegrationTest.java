package com.photoapp.controller;

import com.photoapp.dto.PhotoDto;
import com.photoapp.model.User;
import com.photoapp.repository.PhotoRepository;
import com.photoapp.repository.UserRepository;
import com.photoapp.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test exercising the full multipart upload flow
 * against a real MongoDB instance (via Testcontainers): encryption,
 * GridFS storage, metadata persistence, and duplicate detection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class PhotoUploadIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PhotoRepository photoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String jwtToken;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAll();
        userRepository.deleteAll();

        var user = User.builder()
                .username("integration-user")
                .passwordHash(passwordEncoder.encode("irrelevant"))
                .displayName("Integration User")
                .createdAt(Instant.now())
                .failedLoginAttempts(0)
                .build();
        var saved = userRepository.save(user);
        jwtToken = jwtService.generateToken(saved.getUsername(), saved.getId());
    }

    @AfterEach
    void tearDown() {
        photoRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void uploadEndpoint_withValidJpeg_storesEncryptedPhotoAndReturnsMetadata() {
        var body = buildMultipartBody("photo1.jpg", sampleJpegBytes());
        var headers = authHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        var response = restTemplate.postForEntity(
                "/api/photos/upload", new HttpEntity<>(body, headers), PhotoDto[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);

        var uploaded = response.getBody()[0];
        assertThat(uploaded.originalFilename()).isEqualTo("photo1.jpg");
        assertThat(uploaded.duplicate()).isFalse();

        var storedPhotos = photoRepository.findAll();
        assertThat(storedPhotos).hasSize(1);
        assertThat(storedPhotos.get(0).isEncrypted()).isTrue();
        assertThat(storedPhotos.get(0).getEncryptionIv()).isNotBlank();
    }

    @Test
    void uploadEndpoint_withDuplicateContent_flagsSecondUploadAsDuplicate() {
        var headers = authHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        var sameBytes = sampleJpegBytes();

        var firstResponse = restTemplate.postForEntity(
                "/api/photos/upload",
                new HttpEntity<>(buildMultipartBody("first.jpg", sameBytes), headers),
                PhotoDto[].class);
        assertThat(firstResponse.getBody()[0].duplicate()).isFalse();

        var secondResponse = restTemplate.postForEntity(
                "/api/photos/upload",
                new HttpEntity<>(buildMultipartBody("second.jpg", sameBytes), headers),
                PhotoDto[].class);
        assertThat(secondResponse.getBody()[0].duplicate()).isTrue();

        assertThat(photoRepository.findAll()).hasSize(2);
    }

    @Test
    void uploadEndpoint_withoutJwt_isRejected() {
        var body = buildMultipartBody("photo.jpg", sampleJpegBytes());
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        var response = restTemplate.postForEntity(
                "/api/photos/upload", new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private HttpHeaders authHeaders() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        return headers;
    }

    private MultiValueMap<String, Object> buildMultipartBody(String filename, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        var resource = new org.springframework.core.io.ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        body.add("files", resource);
        return body;
    }

    /**
     * Minimal valid JPEG byte sequence (SOI + EOI markers) sufficient for
     * Thumbnailator/ImageIO to decode as a 1x1 image for test purposes.
     */
    private byte[] sampleJpegBytes() {
        return java.util.Base64.getDecoder().decode(
                "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLFhUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFf/bAEMBCQkJDAsMFhAOFhUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFf/AABEIAAEAAQMBIgACEQEDEQH/xAAUAAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCdABmX/9k=");
    }
}
