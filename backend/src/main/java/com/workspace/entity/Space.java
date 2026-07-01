package com.workspace.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "spaces")
public class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "root_node_id", nullable = false, unique = true)
    private UUID rootNodeId;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public Space() {}

    public Space(String name, UUID ownerId, UUID rootNodeId) {
        this.name = name;
        this.ownerId = ownerId;
        this.rootNodeId = rootNodeId;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }

    public UUID getRootNodeId() { return rootNodeId; }
    public void setRootNodeId(UUID rootNodeId) { this.rootNodeId = rootNodeId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}