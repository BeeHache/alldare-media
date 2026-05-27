package online.alldare.media.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import online.alldare.media.domain.FileMetadata;
import online.alldare.media.repository.FileMetadataRepository;
import org.springframework.security.access.AccessDeniedException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final FileMetadataRepository fileMetadataRepository;

    @Value("${alldare.s3.bucket}")
    private String bucketName;

    @Value("${alldare.cdn.base-url}")
    private String cdnBaseUrl;

    public String getDownloadUrl(String s3Key, UUID requestingUserId) {
        FileMetadata metadata = fileMetadataRepository.findByS3Key(s3Key)
                .orElseThrow(() -> new RuntimeException("File metadata not found for key: " + s3Key));

        if (metadata.isWorldRead()) {
            return String.format("%s/%s", cdnBaseUrl, s3Key);
        }

        // Check permissions for private content
        if (requestingUserId != null && requestingUserId.equals(metadata.getOwnerId()) && metadata.isOwnerRead()) {
            return generatePresignedDownloadUrl(s3Key);
        }

        // TODO: Implement group check if needed
        throw new AccessDeniedException("User does not have permission to access this file.");
    }

    private String generatePresignedDownloadUrl(String s3Key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    public String generatePresignedUploadUrl(String fileName, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return presignedRequest.url().toString();
    }

    public String generateFileName(UUID authorId, String originalExtension, boolean isPublic) {
        String prefix = isPublic ? "public" : "private";
        return String.format("%s/%s/%s%s", prefix, authorId, UUID.randomUUID(), originalExtension);
    }

    public void deleteFile(String fileName) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .build();
        s3Client.deleteObject(deleteObjectRequest);
    }

    public List<String> listFiles(String prefix) {
        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

        return s3Client.listObjectsV2(listObjectsV2Request).contents().stream()
                .map(S3Object::key)
                .collect(Collectors.toList());
    }
}
