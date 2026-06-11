package online.alldare.media.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import java.util.UUID;
import java.util.List;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record MediaUploadEvent(
        @JsonProperty("postId") UUID postId,
        @JsonProperty("objectKey") String objectKey,
        @JsonProperty("contentType") String contentType,
        @JsonProperty("size") Long size,
        @JsonProperty("Records") List<Record> records
) {
    public MediaUploadEvent(UUID postId, String objectKey, String contentType, Long size) {
        this(postId, objectKey, contentType, size, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Record(
        @JsonProperty("s3") S3 s3
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record S3(
        @JsonProperty("object") S3Object object
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record S3Object(
        @JsonProperty("key") String key,
        @JsonProperty("size") Long size,
        @JsonProperty("contentType") String contentType
    ) {}

    @Override
    public String objectKey() {
        String key = null;
        if (objectKey != null) {
            key = objectKey;
        } else if (records != null && !records.isEmpty() && records.get(0).s3() != null && records.get(0).s3().object() != null) {
            key = records.get(0).s3().object().key();
        }
        if (key != null) {
            try {
                return java.net.URLDecoder.decode(key, java.nio.charset.StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                return key;
            }
        }
        return null;
    }

    @Override
    public String contentType() {
        if (contentType != null) {
            return contentType;
        }
        if (records != null && !records.isEmpty() && records.get(0).s3() != null && records.get(0).s3().object() != null) {
            return records.get(0).s3().object().contentType();
        }
        return null;
    }

    @Override
    public Long size() {
        if (size != null) {
            return size;
        }
        if (records != null && !records.isEmpty() && records.get(0).s3() != null && records.get(0).s3().object() != null) {
            return records.get(0).s3().object().size();
        }
        return 0L;
    }
}
