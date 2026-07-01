package com.workspace.service;

import com.workspace.repository.NodeRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class NodeService {

    private final NodeRepository nodeRepository;
    private final AuditService auditService;

    public NodeService(NodeRepository nodeRepository, AuditService auditService) {
        this.nodeRepository = nodeRepository;
        this.auditService = auditService;
    }

    public Map<String, Object> create(UUID spaceId, UUID parentId, String type, String title,
                                     String content, String properties, String caption,
                                     Double sortOrder, UUID createdBy) {
        RoleContext.requireAtLeast(Role.EDITOR);
        Map<String, Object> result = nodeRepository.insert(spaceId, parentId, type, title,
                content, properties, caption, sortOrder, createdBy);
        auditService.log("node.create", "node", (UUID) result.get("id"),
                "{\"spaceId\":\"" + spaceId + "\",\"type\":\"" + type + "\"}");
        return result;
    }

    public Map<String, Object> getById(UUID spaceId, UUID id) {
        RoleContext.requireAtLeast(Role.VIEWER);
        return nodeRepository.findById(id);
    }

    public java.util.List<Map<String, Object>> getAll() {
        RoleContext.requireAtLeast(Role.VIEWER);
        return nodeRepository.findAll();
    }

    public java.util.List<Map<String, Object>> getBySpaceIdAndParentId(UUID spaceId, UUID parentId) {
        RoleContext.requireAtLeast(Role.VIEWER);
        return nodeRepository.findBySpaceIdAndParentId(spaceId, parentId);
    }

    public Map<String, Object> update(UUID spaceId, UUID id, String title, String content, String properties,
                                     String caption, Double sortOrder) {
        RoleContext.requireAtLeast(Role.EDITOR);
        int n = nodeRepository.update(id, title, content, properties, caption, sortOrder);
        if (n == 0) return null;
        Map<String, Object> fresh = nodeRepository.findById(id);
        auditService.log("node.update", "node", id,
                "{\"spaceId\":\"" + spaceId + "\"}");
        return fresh;
    }

    public boolean delete(UUID spaceId, UUID id) {
        RoleContext.requireAtLeast(Role.EDITOR);
        boolean deleted = nodeRepository.deleteById(id) > 0;
        if (deleted) {
            auditService.log("node.delete", "node", id,
                    "{\"spaceId\":\"" + spaceId + "\"}");
        }
        return deleted;
    }

    public boolean move(UUID spaceId, UUID nodeId, UUID newParentId, Double sortOrder) {
        RoleContext.requireAtLeast(Role.EDITOR);
        boolean moved = nodeRepository.updateParentAndSort(nodeId, newParentId, sortOrder) > 0;
        if (moved) {
            auditService.log("node.move", "node", nodeId,
                    "{\"spaceId\":\"" + spaceId + "\",\"newParentId\":\"" + newParentId + "\",\"sortOrder\":" + sortOrder + "}");
        }
        return moved;
    }

    /**
     * MOVE 后的连带查询:返回被移动节点 + 旧父 + 新父的最新状态(带现算 has_children)。
     * Controller 用这个构造 move 响应。
     */
    public Map<String, Object> getMoveResult(UUID nodeId, UUID oldParentId, UUID newParentId) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("movedNode", nodeRepository.findByIdWithHasChildren(nodeId));
        if (oldParentId != null) {
            result.put("oldParent", nodeRepository.findByIdWithHasChildren(oldParentId));
        }
        if (newParentId != null) {
            result.put("newParent", nodeRepository.findByIdWithHasChildren(newParentId));
        }
        return result;
    }

    /**
     * DELETE 后的连带查询:返回被删节点(标记 is_deleted=true)+ 旧父节点(带现算 has_children)。
     * Controller 用这个构造 delete 响应,让前端知道旧父的 has_children 是不是变了。
     */
    public Map<String, Object> getDeleteResult(UUID nodeId, UUID oldParentId) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("deletedNode", nodeRepository.findByIdWithHasChildren(nodeId));
        if (oldParentId != null) {
            result.put("oldParent", nodeRepository.findByIdWithHasChildren(oldParentId));
        }
        return result;
    }
}