package database.algorithms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "algorithm_catalog_algorithms")
public class AlgorithmCatalogAlgorithmEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String phase;
    @Column(nullable = false)
    private String name;
    @Column(name = "owner_user_id")
    private Long ownerUserId;
    @Column(name = "seed_key", unique = true)
    private String seedKey;
    @Column(name = "seed_order")
    private Integer seedOrder;
    @Column(name = "active_canonical_revision_id")
    private UUID activeCanonicalRevisionId;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AlgorithmCatalogAlgorithmEntity() {}

    public AlgorithmCatalogAlgorithmEntity(
            UUID id, String phase, String name, String seedKey, Integer seedOrder, OffsetDateTime createdAt
    ) {
        this.id = id;
        this.phase = phase;
        this.name = name;
        this.seedKey = seedKey;
        this.seedOrder = seedOrder;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getPhase() { return phase; }
    public String getName() { return name; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getSeedKey() { return seedKey; }
    public Integer getSeedOrder() { return seedOrder; }
    public UUID getActiveCanonicalRevisionId() { return activeCanonicalRevisionId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setActiveCanonicalRevisionId(UUID revisionId) { this.activeCanonicalRevisionId = revisionId; }
}
