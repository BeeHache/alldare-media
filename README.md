# alldare-media

Media storage gatekeeper. Manages S3 interactions and enforces the "No-Proxy" strategy.

## Handshake Flow

```mermaid
sequenceDiagram
    participant User
    participant Gateway
    participant Media as alldare-media
    participant S3 as DigitalOcean Spaces / MinIO

    User->>Gateway: GET /api/v1/storage/presigned-url?isPublic=true
    Gateway->>Media: Forward Request
    Media->>Media: Generate [public|private] UUID FileName
    Media->>S3: Request Pre-signed PUT
    S3-->>Media: Return Signed URL
    Media-->>User: Return JSON {uploadUrl, fileName}
    
    Note over User,S3: "No Proxying" Implementation
    User->>S3: PUT [Bytes] (Direct Upload)
    S3-->>User: 200 OK
```

## Security: Prefix-Based Isolation
To prevent unauthorized access through the CDN, files are physically segregated:
*   **Public Path:** `public/{authorId}/{uuid}{ext}` -> Accessible via CDN.
*   **Private Path:** `private/{authorId}/{uuid}{ext}` -> BLOCKED by CDN. Only accessible via Pre-signed GET URLs.
