package online.alldare.media;

import online.alldare.common.dto.media.FileMetadataUpdateRequest;
import online.alldare.common.event.MetaDataUpdateEvent;
import online.alldare.media.domain.FileMetadata;
import online.alldare.media.repository.FileMetadataRepository;
import online.alldare.media.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
public class MediaMetadataUpdateIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private S3Presigner s3Presigner;

    @BeforeEach
    void cleanDb() {
        fileMetadataRepository.deleteAll();
    }

    @Test
    void shouldUpdateMetadataSuccessfullyPublishEventAndTriggerS3RelocationListener() throws Exception {
        // Given
        UUID fileId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String initialKey = "private/" + ownerId + "/image.png";
        
        FileMetadata metadata = new FileMetadata();
        metadata.setId(fileId);
        metadata.setOwnerId(ownerId);
        metadata.setS3Key(initialKey);
        metadata.setWorldRead(false);
        metadata.setOwnerRead(true);
        metadata.setOwnerWrite(true);
        
        fileMetadataRepository.save(metadata);

        FileMetadataUpdateRequest updateRequest = FileMetadataUpdateRequest.builder()
                .worldRead(true) // Should trigger move to public/
                .build();

        // When (Call REST API to update metadata)
        mockMvc.perform(patch("/api/v1/storage/metadata/" + fileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                        .with(jwt().jwt(j -> j.claim("userId", ownerId.toString()).subject("testuser"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.worldRead").value(true));

        // Then 1: Verify database values (worldRead is true, s3Key not yet updated synchronously)
        FileMetadata savedMeta = fileMetadataRepository.findById(fileId).orElseThrow();
        assertThat(savedMeta.isWorldRead()).isTrue();

        // Then 2: Verify Redis Stream has the MetaDataUpdateEvent
        List<ObjectRecord<String, String>> records = redisTemplate.opsForStream().read(
                String.class,
                StreamOffset.fromStart("stream:media")
        );
        assertThat(records).isNotEmpty();
        String jsonEvent = records.getFirst().getValue();
        MetaDataUpdateEvent event = objectMapper.readValue(jsonEvent, MetaDataUpdateEvent.class);
        assertThat(event.fileId()).isEqualTo(fileId);
        assertThat(event.worldRead()).isTrue();

        // Then 3: Verify the Stream Listener runs asynchronously, moves S3 object, and updates the database s3Key
        String expectedNewKey = "public/" + ownerId + "/image.png";
        
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            FileMetadata finalMeta = fileMetadataRepository.findById(fileId).orElseThrow();
            assertThat(finalMeta.getS3Key()).isEqualTo(expectedNewKey);
        });

        // Verify S3Client copy and delete methods were invoked
        verify(s3Client).copyObject(any(CopyObjectRequest.class));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void shouldDenyUpdateForNonOwner() throws Exception {
        // Given
        UUID fileId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        
        FileMetadata metadata = new FileMetadata();
        metadata.setId(fileId);
        metadata.setOwnerId(ownerId);
        metadata.setS3Key("private/" + ownerId + "/image.png");
        metadata.setWorldRead(false);
        metadata.setOwnerRead(true);
        metadata.setOwnerWrite(true);
        
        fileMetadataRepository.save(metadata);

        FileMetadataUpdateRequest updateRequest = FileMetadataUpdateRequest.builder()
                .worldRead(true)
                .build();

        // When & Then
        mockMvc.perform(patch("/api/v1/storage/metadata/" + fileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                        .with(jwt().jwt(j -> j.claim("userId", otherUserId.toString()).subject("malicioususer"))))
                .andExpect(status().isForbidden());
    }
}
