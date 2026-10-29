package database.algorithms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "algorithm_catalog_revisions")
public class AlgorithmCatalogRevisionEntity {
    @Id
    private UUID id;
    @Column(name = "algorithm_id", nullable = false)
    private UUID algorithmId;
    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_json", nullable = false, columnDefinition = "jsonb")
    private String definitionJson;
    @Column(name = "author_user_id")
    private Long authorUserId;
    @Column(nullable = false)
    private String status;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected AlgorithmCatalogRevisionEntity() {}

    public AlgorithmCatalogRevisionEntity(UUID id, UUID algorithmId, int revisionNumber, String definitionJson,
                                          Long authorUserId, String status, OffsetDateTime createdAt) {
        this.id = id;
        this.algorithmId = algorithmId;
        this.revisionNumber = revisionNumber;
        this.definitionJson = definitionJson;
        this.authorUserId = authorUserId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getAlgorithmId() { return algorithmId; }
    public int getRevisionNumber() { return revisionNumber; }
    public String getDefinitionJson() { return definitionJson; }
    public Long getAuthorUserId() { return authorUserId; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
