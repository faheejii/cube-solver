package database.algorithms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "algorithm_catalog_state")
public class AlgorithmCatalogStateEntity {
    @Id
    @Column(name = "singleton_id")
    private short singletonId;
    @Column(name = "catalog_version", nullable = false)
    private long catalogVersion;
    @Column(name = "seed_version")
    private String seedVersion;
    @Column(name = "seed_checksum")
    private String seedChecksum;
    @Column(name = "initialized_at")
    private OffsetDateTime initializedAt;

    protected AlgorithmCatalogStateEntity() {}

    AlgorithmCatalogStateEntity(short singletonId) {
        this.singletonId = singletonId;
    }

    public short getSingletonId() { return singletonId; }
    public long getCatalogVersion() { return catalogVersion; }
    public String getSeedVersion() { return seedVersion; }
    public String getSeedChecksum() { return seedChecksum; }
    public OffsetDateTime getInitializedAt() { return initializedAt; }

    public void markSeedInitialized(String version, String checksum, OffsetDateTime at) {
        this.seedVersion = version;
        this.seedChecksum = checksum;
        this.initializedAt = at;
        this.catalogVersion++;
    }
}
