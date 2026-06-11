package online.alldare.media.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import online.alldare.media.domain.FileMetadata;
import online.alldare.media.repository.FileMetadataRepository;
import org.springframework.security.access.AccessDeniedException;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URL;
import java.util.Optional;
import java.util.UUID;

import online.alldare.common.messaging.MessagePublisher;
import online.alldare.common.dto.media.FileMetadataUpdateRequest;
import online.alldare.common.event.MetaDataUpdateEvent;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private FileMetadataRepository fileMetadataRepository;

    @Mock
    private MessagePublisher messagePublisher;

    @InjectMocks
    private StorageService storageService;

    @Captor
    private ArgumentCaptor<PutObjectPresignRequest> presignRequestCaptor;

    @Captor
    private ArgumentCaptor<GetObjectPresignRequest> getPresignRequestCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(storageService, "cdnBaseUrl", "https://cdn.example.com");
    }

    @Test
    void getDownloadUrl_PublicFile_ReturnsCdnUrl() {
        String s3Key = "public/file.jpg";
        FileMetadata metadata = new FileMetadata();
        metadata.setS3Key(s3Key);
        metadata.setWorldRead(true);

        when(fileMetadataRepository.findByS3Key(s3Key)).thenReturn(Optional.of(metadata));

        String result = storageService.getDownloadUrl(s3Key, null);

        assertThat(result).isEqualTo("https://cdn.example.com/" + s3Key);
    }

    @Test
    void getDownloadUrl_PrivateFileOwner_ReturnsPresignedUrl() throws Exception {
        String s3Key = "private/file.jpg";
        UUID ownerId = UUID.randomUUID();
        FileMetadata metadata = new FileMetadata();
        metadata.setS3Key(s3Key);
        metadata.setOwnerId(ownerId);
        metadata.setOwnerRead(true);
        metadata.setWorldRead(false);

        URL expectedUrl = new URL("https://test-bucket.s3.amazonaws.com/" + s3Key + "?signed=true");
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(expectedUrl);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);
        when(fileMetadataRepository.findByS3Key(s3Key)).thenReturn(Optional.of(metadata));

        String result = storageService.getDownloadUrl(s3Key, ownerId);

        assertThat(result).isEqualTo(expectedUrl.toString());
        verify(s3Presigner).presignGetObject(getPresignRequestCaptor.capture());
        assertThat(getPresignRequestCaptor.getValue().getObjectRequest().key()).isEqualTo(s3Key);
    }

    @Test
    void getDownloadUrl_PrivateFileNonOwner_ThrowsAccessDenied() {
        String s3Key = "private/file.jpg";
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        FileMetadata metadata = new FileMetadata();
        metadata.setS3Key(s3Key);
        metadata.setOwnerId(ownerId);
        metadata.setOwnerRead(true);
        metadata.setWorldRead(false);

        when(fileMetadataRepository.findByS3Key(s3Key)).thenReturn(Optional.of(metadata));

        assertThatThrownBy(() -> storageService.getDownloadUrl(s3Key, otherUserId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void generatePresignedUploadUrl_GeneratesValidUrl() throws Exception {
        String fileName = "test-file.mp4";
        String contentType = "video/mp4";
        URL expectedUrl = new URL("https://test-bucket.s3.amazonaws.com/" + fileName);

        PresignedPutObjectRequest presignedPutObjectRequest = mock(PresignedPutObjectRequest.class);
        when(presignedPutObjectRequest.url()).thenReturn(expectedUrl);
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPutObjectRequest);

        String result = storageService.generatePresignedUploadUrl(fileName, contentType);

        verify(s3Presigner).presignPutObject(presignRequestCaptor.capture());
        PutObjectPresignRequest capturedRequest = presignRequestCaptor.getValue();

        assertThat(capturedRequest.putObjectRequest().bucket()).isEqualTo("test-bucket");
        assertThat(capturedRequest.putObjectRequest().key()).isEqualTo(fileName);
        assertThat(capturedRequest.putObjectRequest().contentType()).isEqualTo(contentType);
        assertThat(result).isEqualTo(expectedUrl.toString());
    }

    @Test
    void generateFileName_CreatesUniqueNameWithPublicPrefix() {
        UUID authorId = UUID.randomUUID();
        String extension = ".jpg";

        String fileName = storageService.generateFileName(authorId, extension, true);

        assertThat(fileName).startsWith("public/" + authorId.toString() + "/");
        assertThat(fileName).endsWith(extension);
    }

    @Test
    void generateFileName_CreatesUniqueNameWithPrivatePrefix() {
        UUID authorId = UUID.randomUUID();
        String extension = ".jpg";

        String fileName = storageService.generateFileName(authorId, extension, false);

        assertThat(fileName).startsWith("private/" + authorId.toString() + "/");
        assertThat(fileName).endsWith(extension);
    }

    @Test
    void updateFileMetadata_OwnerUpdate_SucceedsAndPublishesEvent() {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        FileMetadata metadata = new FileMetadata();
        metadata.setId(id);
        metadata.setOwnerId(ownerId);
        metadata.setS3Key("private/file.jpg");
        metadata.setWorldRead(false);

        FileMetadataUpdateRequest updateRequest = FileMetadataUpdateRequest.builder()
                .worldRead(true)
                .contentType("image/jpeg")
                .build();

        when(fileMetadataRepository.findById(id)).thenReturn(Optional.of(metadata));
        when(fileMetadataRepository.save(any(FileMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileMetadata result = storageService.updateFileMetadata(id, updateRequest, ownerId);

        assertThat(result.isWorldRead()).isTrue();
        assertThat(result.getContentType()).isEqualTo("image/jpeg");
        verify(fileMetadataRepository).save(metadata);
        verify(messagePublisher).publish(eq("stream:media"), any(MetaDataUpdateEvent.class));
    }

    @Test
    void updateFileMetadata_NonOwnerUpdate_ThrowsAccessDenied() {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        FileMetadata metadata = new FileMetadata();
        metadata.setId(id);
        metadata.setOwnerId(ownerId);

        FileMetadataUpdateRequest updateRequest = FileMetadataUpdateRequest.builder().worldRead(true).build();

        when(fileMetadataRepository.findById(id)).thenReturn(Optional.of(metadata));

        assertThatThrownBy(() -> storageService.updateFileMetadata(id, updateRequest, otherUserId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void moveObject_ExecutesCopyAndDeleteOnS3() {
        String sourceKey = "private/file.jpg";
        String destKey = "public/file.jpg";

        storageService.moveObject(sourceKey, destKey);

        verify(s3Client).copyObject(any(CopyObjectRequest.class));
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
}
