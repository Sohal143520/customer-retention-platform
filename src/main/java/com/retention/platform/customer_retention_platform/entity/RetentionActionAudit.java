package com.retention.platform.customer_retention_platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "retention_action_audit")
public class RetentionActionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_id", nullable = false)
    private Long actionId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(nullable = false, length = 500)
    private String details;

    @Column(nullable = false, length = 100)
    private String actor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RetentionActionAudit() {
    }

    public RetentionActionAudit(
            Long actionId,
            String eventType,
            String details,
            String actor,
            Instant createdAt) {
        this.actionId = actionId;
        this.eventType = eventType;
        this.details = details;
        this.actor = actor;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getActionId() { return actionId; }
    public String getEventType() { return eventType; }
    public String getDetails() { return details; }
    public String getActor() { return actor; }
    public Instant getCreatedAt() { return createdAt; }
}
