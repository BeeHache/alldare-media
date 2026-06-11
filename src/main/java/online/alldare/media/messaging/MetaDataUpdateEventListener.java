package online.alldare.media.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.alldare.common.event.MetaDataUpdateEvent;
import online.alldare.media.domain.FileMetadata;
import online.alldare.media.repository.FileMetadataRepository;
import online.alldare.media.service.StorageService;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class MetaDataUpdateEventListener implements StreamListener<String, ObjectRecord<String, String>> {

    private final StorageService storageService;
    private final FileMetadataRepository fileMetadataRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        String streamKey = record.getStream();
        RecordId recordId = record.getId();
        String jsonPayload = record.getValue();
        log.info("Received MetaDataUpdateEvent from stream {}: {}", streamKey, jsonPayload);

        try {
            MetaDataUpdateEvent event = objectMapper.readValue(jsonPayload, MetaDataUpdateEvent.class);
            
            // Resolve the current metadata from the database
            FileMetadata metadata = fileMetadataRepository.findById(event.fileId())
                    .orElseThrow(() -> new RuntimeException("File metadata not found for ID: " + event.fileId()));

            // Sync all fields from the event to avoid stale overwrites
            metadata.setWorldRead(event.worldRead());
            metadata.setOwnerRead(event.ownerRead());
            metadata.setOwnerWrite(event.ownerWrite());
            metadata.setGroupRead(event.groupRead());
            metadata.setGroupWrite(event.groupWrite());
            metadata.setGroupId(event.groupId());
            if (event.contentType() != null) {
                metadata.setContentType(event.contentType());
            }

            String currentKey = metadata.getS3Key();
            boolean isWorldRead = event.worldRead();
            
            String newKey = currentKey;
            
            if (isWorldRead && currentKey.startsWith("private/")) {
                newKey = currentKey.replaceFirst("^private/", "public/");
                log.info("Moving file in S3 from {} to {} (worldRead = true)", currentKey, newKey);
                storageService.moveObject(currentKey, newKey);
                metadata.setS3Key(newKey);
                fileMetadataRepository.save(metadata);
            } else if (!isWorldRead && currentKey.startsWith("public/")) {
                newKey = currentKey.replaceFirst("^public/", "private/");
                log.info("Moving file in S3 from {} to {} (worldRead = false)", currentKey, newKey);
                storageService.moveObject(currentKey, newKey);
                metadata.setS3Key(newKey);
                fileMetadataRepository.save(metadata);
            } else {
                log.debug("No S3 move required for S3 key '{}' with worldRead={}", currentKey, isWorldRead);
                fileMetadataRepository.save(metadata);
            }

            // Acknowledge stream message
            redisTemplate.opsForStream().acknowledge(streamKey, "alldare-media-group", recordId);
            log.info("Successfully processed and acknowledged MetaDataUpdateEvent for file: {}", event.fileId());

        } catch (Exception e) {
            log.error("Failed to process MetaDataUpdateEvent: {}", jsonPayload, e);
        }
    }
}
