package database.algorithms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "algorithm_catalog_workflow_events")
public class AlgorithmCatalogWorkflowEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String action;
    @Column(name = "actor_user_id")
    private Long actorUserId;
    @Column(name = "revision_id", nullable = false)
    private UUID revisionId;
    @Column(name = "previous_status")
    private String previousStatus;
    @Column(name = "new_status")
    private String newStatus;
    private String reason;
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    protected AlgorithmCatalogWorkflowEventEntity() {}

    public AlgorithmCatalogWorkflowEventEntity(String action, UUID revisionId, String previousStatus,
                                               String newStatus, String reason, OffsetDateTime occurredAt) {
        this.action = action;
        this.revisionId = revisionId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public String getAction() { return action; }
    public Long getActorUserId() { return actorUserId; }
    public UUID getRevisionId() { return revisionId; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
}
