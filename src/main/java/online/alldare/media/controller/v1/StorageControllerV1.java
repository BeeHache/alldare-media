package online.alldare.media.controller.v1;

import lombok.extern.slf4j.Slf4j;
import online.alldare.media.domain.dto.StorageResponse;
import online.alldare.media.domain.dto.UserMediaResponse;
import online.alldare.media.service.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import online.alldare.media.domain.FileMetadata;
import online.alldare.common.dto.media.FileMetadataUpdateRequest;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/storage")
public class StorageControllerV1 {

    private final StorageService storageService;

    public StorageControllerV1(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/download-url")
    public ResponseEntity<Map<String, String>> getDownloadUrl(
            @RequestParam String s3Key,
            @AuthenticationPrincipal Jwt jwt) {
        UUID currentUserId = null;
        if (jwt != null) {
            String userIdClaim = jwt.getClaimAsString("userId");
            currentUserId = UUID.fromString(userIdClaim != null ? userIdClaim : jwt.getSubject());
        }
        String url = storageService.getDownloadUrl(s3Key, currentUserId);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/presigned-url")
    public StorageResponse getPresignedUrl(
            @RequestParam UUID authorId,
            @RequestParam String extension,
            @RequestParam(required = false) String contentType,
            @RequestParam(defaultValue = "false") boolean isPublic) {
        
        String fileName = storageService.generateFileName(authorId, extension, isPublic);
        String url = storageService.generatePresignedUploadUrl(fileName, contentType);
        try {
            return new StorageResponse(new URI(url).toURL(), fileName);
        } catch (URISyntaxException | MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @DeleteMapping("/{fileName}")
    public ResponseEntity<Void> deleteFile(@PathVariable String fileName, @AuthenticationPrincipal Jwt jwt) {
        String userIdClaim = jwt.getClaimAsString("userId");
        UUID currentUserId = UUID.fromString(userIdClaim != null ? userIdClaim : Objects.requireNonNull(jwt.getSubject()));
        
        // Simple permission check: if the file name starts with the user ID, they can delete it.
        // This is a common pattern for user-specific storage.
        if (!fileName.startsWith(currentUserId.toString())) {
            throw new AccessDeniedException("User does not have permission to delete this file.");
        }

        storageService.deleteFile(fileName);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/list")
    public ResponseEntity<List<String>> listFiles(@RequestParam(required = false) String prefix) {
        List<String> files = storageService.listFiles(prefix);
        return ResponseEntity.ok(files);
    }

    @PatchMapping("/metadata/{id}")
    public ResponseEntity<FileMetadata> updateFileMetadata(
            @PathVariable UUID id,
            @RequestBody FileMetadataUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        
        UUID currentUserId = null;
        if (jwt != null) {
            String userIdClaim = jwt.getClaimAsString("userId");
            currentUserId = UUID.fromString(userIdClaim != null ? userIdClaim : jwt.getSubject());
        }

        FileMetadata updated = storageService.updateFileMetadata(id, request, currentUserId);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/my-media")
    public ResponseEntity<List<UserMediaResponse>> getMyMedia(@AuthenticationPrincipal Jwt jwt) {
        UUID currentUserId = null;
        if (jwt != null) {
            String userIdClaim = jwt.getClaimAsString("userId");
            currentUserId = UUID.fromString(userIdClaim != null ? userIdClaim : jwt.getSubject());
        }
        return ResponseEntity.ok(storageService.getUserMedia(currentUserId));
    }
}
