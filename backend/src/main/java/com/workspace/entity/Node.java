package com.workspace.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "nodes")
public class Node {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "title", nullable = false)
    private String title = "Untitled";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private String properties = "{}";

    @Column(name = "caption")
    private String caption;

    @Column(name = "sort_order")
    private Double sortOrder = 0.0;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public Node() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSpaceId() { return spaceId; }
    public void setSpaceId(UUID spaceId) { this.spaceId = spaceId; }

    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getProperties() { return properties; }
    public void setProperties(String properties) { this.properties = properties; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public Double getSortOrder() { return sortOrder; }
    public void setSortOrder(Double sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}