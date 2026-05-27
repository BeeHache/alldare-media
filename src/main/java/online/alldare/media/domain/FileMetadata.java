package online.alldare.media.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Setter
@Getter
@Entity
public class FileMetadata {

    @Id
    private UUID id;
    private UUID ownerId;
    private UUID groupId;
    private boolean ownerRead;
    private boolean ownerWrite;
    private boolean groupRead;
    private boolean groupWrite;
    private boolean worldRead;
    private String s3Key;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

}