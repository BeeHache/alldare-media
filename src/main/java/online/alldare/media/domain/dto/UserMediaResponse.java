package online.alldare.media.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record UserMediaResponse(
    UUID id,
    String s3Key,
    String contentType,
    boolean worldRead,
    String downloadUrl,
    Instant createdAt
) {}
