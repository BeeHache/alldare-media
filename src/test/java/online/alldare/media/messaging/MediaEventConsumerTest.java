package online.alldare.media.messaging;

import online.alldare.media.domain.FileMetadata;
import online.alldare.media.event.MediaUploadEvent;
import online.alldare.media.repository.FileMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaEventConsumerTest {

    @Mock
    private FileMetadataRepository fileMetadataRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MediaEventConsumer mediaEventConsumer;

    @Captor
    private ArgumentCaptor<FileMetadata> metadataCaptor;

    private UUID ownerId;
    private UUID postId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        postId = UUID.randomUUID();
    }

    @Test
    void handleMessage_WithValidPublicEvent_CreatesMetadata() throws Exception {
        // Given
        String s3Key = "public/" + ownerId + "/image.jpg";
        String contentType = "image/jpeg";
        MediaUploadEvent event = new MediaUploadEvent(postId, s3Key, contentType, 1024L);

        String message = "{}";
        when(objectMapper.readValue(message, MediaUploadEvent.class)).thenReturn(event);
        when(fileMetadataRepository.findByS3Key(s3Key)).thenReturn(Optional.empty());

        // When
        mediaEventConsumer.handleMessage(message);

        // Then
        verify(fileMetadataRepository).save(metadataCaptor.capture());
        FileMetadata savedMetadata = metadataCaptor.getValue();

        assertThat(savedMetadata.getId()).isNotNull();
        assertThat(savedMetadata.getOwnerId()).isEqualTo(ownerId);
        assertThat(savedMetadata.getS3Key()).isEqualTo(s3Key);
        assertThat(savedMetadata.getContentType()).isEqualTo(contentType);

        // Permissions
        assertThat(savedMetadata.isOwnerRead()).isTrue();
        assertThat(savedMetadata.isOwnerWrite()).isTrue();
        assertThat(savedMetadata.isGroupRead()).isTrue();
        assertThat(savedMetadata.isGroupWrite()).isFalse();
        assertThat(savedMetadata.isWorldRead()).isTrue();
    }

    @Test
    void handleMessage_WithValidPrivateEvent_CreatesMetadata() throws Exception {
        // Given
        String s3Key = "private/" + ownerId + "/video.mp4";
        String contentType = "video/mp4";
        MediaUploadEvent event = new MediaUploadEvent(postId, s3Key, contentType, 4096L);

        String message = "{}";
        when(objectMapper.readValue(message, MediaUploadEvent.class)).thenReturn(event);
        when(fileMetadataRepository.findByS3Key(s3Key)).thenReturn(Optional.empty());

        // When
        mediaEventConsumer.handleMessage(message);

        // Then
        verify(fileMetadataRepository).save(metadataCaptor.capture());
        FileMetadata savedMetadata = metadataCaptor.getValue();

        assertThat(savedMetadata.getId()).isNotNull();
        assertThat(savedMetadata.getOwnerId()).isEqualTo(ownerId);
        assertThat(savedMetadata.getS3Key()).isEqualTo(s3Key);
        assertThat(savedMetadata.getContentType()).isEqualTo(contentType);

        // Permissions
        assertThat(savedMetadata.isOwnerRead()).isTrue();
        assertThat(savedMetadata.isOwnerWrite()).isTrue();
        assertThat(savedMetadata.isGroupRead()).isFalse();
        assertThat(savedMetadata.isGroupWrite()).isFalse();
        assertThat(savedMetadata.isWorldRead()).isFalse();
    }

    @Test
    void handleMessage_WithInvalidKeyFormat_DoesNotCreateMetadata() throws Exception {
        // Given
        String s3Key = "invalid-key-no-slashes";
        MediaUploadEvent event = new MediaUploadEvent(postId, s3Key, "image/png", 512L);

        String message = "{}";
        when(objectMapper.readValue(message, MediaUploadEvent.class)).thenReturn(event);

        // When
        mediaEventConsumer.handleMessage(message);

        // Then
        verify(fileMetadataRepository, never()).save(any());
    }

    @Test
    void handleMessage_WithInvalidOwnerUuid_DoesNotCreateMetadata() throws Exception {
        // Given
        String s3Key = "public/not-a-uuid/image.png";
        MediaUploadEvent event = new MediaUploadEvent(postId, s3Key, "image/png", 512L);

        String message = "{}";
        when(objectMapper.readValue(message, MediaUploadEvent.class)).thenReturn(event);

        // When
        mediaEventConsumer.handleMessage(message);

        // Then
        verify(fileMetadataRepository, never()).save(any());
    }

    @Test
    void handleMessage_WithDuplicateS3Key_DoesNotCreateMetadata() throws Exception {
        // Given
        String s3Key = "public/" + ownerId + "/image.jpg";
        MediaUploadEvent event = new MediaUploadEvent(postId, s3Key, "image/jpeg", 1024L);

        String message = "{}";
        when(objectMapper.readValue(message, MediaUploadEvent.class)).thenReturn(event);
        when(fileMetadataRepository.findByS3Key(s3Key)).thenReturn(Optional.of(new FileMetadata()));

        // When
        mediaEventConsumer.handleMessage(message);

        // Then
        verify(fileMetadataRepository, never()).save(any());
    }
}
