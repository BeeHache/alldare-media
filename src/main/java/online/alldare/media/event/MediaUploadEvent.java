package online.alldare.media.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import java.util.UUID;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record MediaUploadEvent(
        @JsonProperty("postId") UUID postId,
        @JsonProperty("objectKey") String objectKey,
        @JsonProperty("contentType") String contentType,
        @JsonProperty("size") long size
) {
}
