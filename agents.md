# Agent Context: alldare-media

## 1. Project Purpose
The media management microservice for the Alldare platform. This service handles the complexity of interacting with S3-compatible object storage, specifically providing pre-signed URLs for client-side uploads and managing file metadata/lifecycle. It serves as the implementation of the platform's "no-proxy" media strategy.

## 2. Core Tech Stack
*   **Language/Framework:** Java 21 / Spring Boot 4.x
*   **Object Storage SDK:** AWS SDK for Java v2 (S3 & S3Presigner)
*   **Security:** Spring Security with OAuth2 Resource Server (JWT-based)
*   **Object Storage:** S3-Compatible (MinIO for dev, DigitalOcean Spaces for prod)

## 3. Data Strategy (The "Golden Rules")
*   **No Proxying:** This service MUST NOT proxy media bytes between the client and storage. 
    *   *Implementation:* Always use **Pre-signed URLs** for uploads. The service generates the S3 link; the client uploads directly to storage.
*   **File Naming:** Files are stored using a structured path: `{authorId}/{random-uuid}{extension}`.
    *   *Rationale:* Ensures isolation by user and prevents filename collisions.
*   **Access Control:** Deletion requests must be verified against the `authorId` prefix of the filename to ensure users can only delete their own content.

## 4. Key Architectural Patterns
### Pre-signed URL Pattern
The service acts as the "gatekeeper" for S3:
1.  **Request:** Client requests a pre-signed URL with `authorId`, `extension`, and optionally `contentType`.
2.  **Generation:** Service generates a unique filename (path) and a pre-signed `PUT` URL (default duration: 15 mins).
3.  **Response:** Client receives a `StorageResponse` containing the `uploadUrl` and the final `fileName`.

### Multi-cloud Compatibility
*   Uses the standard S3 protocol to remain compatible with MinIO (local dev) and DigitalOcean Spaces (production).
*   **Path Style Access:** Must be enabled (via `alldare.s3.path-style-access=true`) for compatibility with non-AWS providers.

## 5. Infrastructure Constants
*   **S3 Configuration:** Managed via `alldare.s3.*` properties in `application.yaml`.
*   **Port:** Default server port is `8081`.

## 6. Coding Standards
*   **Timestamps:** Use `java.time.Instant` for any time-related logic.
*   **Identification:** Use `java.util.UUID` for all identifiers (User IDs, File IDs).
*   **Architecture:** Strictly separate DTOs (e.g., `StorageResponse`) from internal logic.
*   **Security:** Use `@AuthenticationPrincipal Jwt jwt` to retrieve and verify the current user's ID for sensitive operations like deletion.
