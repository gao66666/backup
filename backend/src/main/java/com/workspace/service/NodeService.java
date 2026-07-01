package com.workspace.service;

import com.workspace.repository.NodeRepository;
import org.jooq.Record;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class NodeService {

    private final NodeRepository nodeRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public NodeService(NodeRepository nodeRepository, PermissionService permissionService, AuditService auditService) {
        this.nodeRepository = nodeRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    public Map<String, Object> create(UUID spaceId, UUID parentId, String type, String title,
                                     String content, String properties, String description,
                                     Double sortOrder, UUID createdBy) {
        permissionService.checkCanEdit(spaceId);
        Record record = nodeRepository.insert(spaceId, parentId, type, title,
                content, properties, description, sortOrder, createdBy);
        Map<String, Object> result = toMap(record);
        auditService.log("node.create", "node", (UUID) result.get("id"),
                "{\"spaceId\":\"" + spaceId + "\",\"type\":\"" + type + "\"}");
        return result;
    }

    public Map<String, Object> getById(UUID spaceId, UUID id) {
        permissionService.checkCanView(spaceId);
        return nodeRepository.findById(id)
                .map(this::toMap)
                .orElse(null);
    }

    public java.util.List<Map<String, Object>> getAll() {
        return nodeRepository.findAll().stream()
                .map(this::toMap)
                .toList();
    }

    public java.util.List<Map<String, Object>> getBySpaceIdAndParentId(UUID spaceId, UUID parentId) {
        permissionService.checkCanView(spaceId);
        return nodeRepository.findBySpaceIdAndParentId(spaceId, parentId).stream()
                .map(this::toMap)
                .toList();
    }

    public boolean update(UUID spaceId, UUID id, String title, String content, String properties,
                         String description, Double sortOrder) {
        permissionService.checkCanEdit(spaceId);
        boolean updated = nodeRepository.update(id, title, content, properties,
                description, sortOrder) > 0;
        if (updated) {
            auditService.log("node.update", "node", id,
                    "{\"spaceId\":\"" + spaceId + "\"}");
        }
        return updated;
    }

    public boolean delete(UUID spaceId, UUID id) {
        permissionService.checkCanEdit(spaceId);
        boolean deleted = nodeRepository.deleteById(id) > 0;
        if (deleted) {
            auditService.log("node.delete", "node", id,
                    "{\"spaceId\":\"" + spaceId + "\"}");
        }
        return deleted;
    }

    public boolean move(UUID spaceId, UUID nodeId, UUID newParentId, Double sortOrder) {
        permissionService.checkCanEdit(spaceId);
        boolean moved = nodeRepository.updateParentAndSort(nodeId, newParentId, sortOrder) > 0;
        if (moved) {
            auditService.log("node.move", "node", nodeId,
                    "{\"spaceId\":\"" + spaceId + "\",\"newParentId\":\"" + newParentId + "\",\"sortOrder\":" + sortOrder + "}");
        }
        return moved;
    }

    private Map<String, Object> toMap(Record record) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (var field : record.fields()) {
            Object value = record.getValue(field, Object.class);
            if (value != null && value.getClass().getSimpleName().contains("JSON")) {
                value = value.toString();
            }
            map.put(field.getName(), value);
        }
        return map;
    }
}
