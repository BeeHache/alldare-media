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
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @InjectMocks
    private StorageService storageService;

    @Captor
    private ArgumentCaptor<PutObjectPresignRequest> presignRequestCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageService, "bucketName", "test-bucket");
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
    void generateFileName_CreatesUniqueName() {
        UUID authorId = UUID.randomUUID();
        String extension = ".jpg";

        String fileName = storageService.generateFileName(authorId, extension);

        assertThat(fileName).startsWith(authorId.toString() + "/");
        assertThat(fileName).endsWith(extension);
    }
}
