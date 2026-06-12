package online.alldare.media.messaging;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.alldare.media.domain.FileMetadata;
import online.alldare.media.event.MediaUploadEvent;
import online.alldare.media.repository.FileMetadataRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class MediaEventConsumer {

    private final FileMetadataRepository fileMetadataRepository;
    private final ObjectMapper objectMapper;

    public void handleMessage(String message) {
        log.info("Received media upload event message: {}", message);
        try {
            MediaUploadEvent event = objectMapper.readValue(message, MediaUploadEvent.class);
            if (event == null || event.objectKey() == null) {
                log.warn("Null event or missing objectKey in event message");
                return;
            }

            String s3Key = event.objectKey();
            if (!s3Key.contains("/")) {
                log.warn("Invalid S3 key format (expected path): {}", s3Key);
                return;
            }

            String[] parts = s3Key.split("/");
            if (parts.length < 2) {
                log.warn("Invalid S3 key structure (missing prefix or ownerId): {}", s3Key);
                return;
            }

            String visibility = parts[0];
            boolean isPublic = "public".equalsIgnoreCase(visibility);

            UUID ownerId;
            try {
                ownerId = UUID.fromString(parts[1]);
            } catch (IllegalArgumentException e) {
                log.warn("Failed to parse owner UUID from key path '{}'", parts[1], e);
                return;
            }

            // Check if metadata already exists for this S3 key to avoid duplicates
            if (fileMetadataRepository.findByS3Key(s3Key).isPresent()) {
                log.info("File metadata already exists for S3 key: {}", s3Key);
                return;
            }

            FileMetadata metadata = new FileMetadata();
            metadata.setId(UUID.randomUUID());
            metadata.setOwnerId(ownerId);
            metadata.setS3Key(s3Key);
            metadata.setContentType(event.contentType());

            if (isPublic) {
                metadata.setOwnerRead(true);
                metadata.setGroupRead(true);
                metadata.setWorldRead(true);
            } else {
                metadata.setOwnerRead(true);
                metadata.setGroupRead(false);
                metadata.setWorldRead(false);
            }

            // Owner should have write permissions in either case
            metadata.setOwnerWrite(true);
            metadata.setGroupWrite(false);

            fileMetadataRepository.saveAndFlush(metadata);
            log.info("Successfully saved file metadata for key: {}", s3Key);

        } catch (Exception e) {
            log.error("Failed to process media event message: {}", message, e);
        }
    }
}
