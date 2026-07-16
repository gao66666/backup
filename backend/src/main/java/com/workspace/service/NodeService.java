package com.workspace.service;

import com.workspace.collaboration.NodeTreeEvent;
import com.workspace.repository.NodeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NodeService {

    private final NodeRepository nodeRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public NodeService(
            NodeRepository nodeRepository,
            AuditService auditService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.nodeRepository = nodeRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Map<String, Object> create(UUID spaceId, UUID parentId, String type, String title,
                                     String content, String properties, String caption,
                                     Double sortOrder, UUID createdBy) {
        RoleContext.requireAtLeast(Role.EDITOR);
        Map<String, Object> inserted = nodeRepository.insert(spaceId, parentId, type, title,
                content, properties, caption, sortOrder, createdBy);
        Map<String, Object> result = nodeRepository.findByIdWithHasChildren(
                spaceId,
                (UUID) inserted.get("id")
        );
        auditService.log(spaceId, "node.create", "node", (UUID) result.get("id"),
                "{\"type\":\"" + type + "\"}");
        eventPublisher.publishEvent(NodeTreeEvent.created(spaceId, createdBy, result));
        return result;
    }

    public Map<String, Object> getById(UUID spaceId, UUID id) {
        RoleContext.requireAtLeast(Role.VIEWER);
        return nodeRepository.findById(spaceId, id);
    }

    public java.util.List<Map<String, Object>> getAll() {
        RoleContext.requireAtLeast(Role.VIEWER);
        return nodeRepository.findAll();
    }

    public java.util.List<Map<String, Object>> getBySpaceIdAndParentId(UUID spaceId, UUID parentId) {
        RoleContext.requireAtLeast(Role.VIEWER);
        return nodeRepository.findBySpaceIdAndParentId(spaceId, parentId);
    }

    @Transactional
    public Map<String, Object> update(
            UUID spaceId,
            UUID id,
            String title,
            String content,
            String properties,
            String caption,
            Double sortOrder,
            UUID updatedBy
    ) {
        RoleContext.requireAtLeast(Role.EDITOR);
        int n = nodeRepository.update(
                spaceId,
                id,
                title,
                content,
                properties,
                caption,
                sortOrder,
                updatedBy
        );
        if (n == 0) return null;
        Map<String, Object> fresh = nodeRepository.findByIdWithHasChildren(spaceId, id);
        auditService.log(spaceId, "node.update", "node", id,
                "{}");
        eventPublisher.publishEvent(NodeTreeEvent.updated(spaceId, updatedBy, fresh));
        return fresh;
    }

    @Transactional
    public Map<String, Object> delete(UUID spaceId, UUID id) {
        RoleContext.requireAtLeast(Role.EDITOR);
        Map<String, Object> before = nodeRepository.findById(spaceId, id);
        if (before == null) return null;

        UUID actorUserId = RoleContext.current().userId();
        UUID oldParentId = (UUID) before.get("parent_id");
        if (nodeRepository.deleteById(spaceId, id, actorUserId) == 0) {
            return null;
        }

        Map<String, Object> result = getDeleteResult(spaceId, id, oldParentId);
        auditService.log(spaceId, "node.delete", "node", id, "{}");
        eventPublisher.publishEvent(NodeTreeEvent.deleted(
                spaceId,
                actorUserId,
                nodeFrom(result, "deletedNode"),
                nodeFrom(result, "oldParent")
        ));
        return result;
    }

    @Transactional
    public Map<String, Object> move(
            UUID spaceId,
            UUID nodeId,
            UUID newParentId,
            Double sortOrder,
            UUID updatedBy
    ) {
        RoleContext.requireAtLeast(Role.EDITOR);
        Map<String, Object> before = nodeRepository.findById(spaceId, nodeId);
        if (before == null) return null;

        UUID oldParentId = (UUID) before.get("parent_id");
        int moved = nodeRepository.updateParentAndSort(
                spaceId,
                nodeId,
                newParentId,
                sortOrder,
                updatedBy
        );
        if (moved == 0) {
            return null;
        }

        Map<String, Object> result = getMoveResult(
                spaceId,
                nodeId,
                oldParentId,
                newParentId
        );
        auditService.log(spaceId, "node.move", "node", nodeId,
                "{\"spaceId\":\"" + spaceId + "\",\"newParentId\":\"" + newParentId + "\",\"sortOrder\":" + sortOrder + "}");
        eventPublisher.publishEvent(NodeTreeEvent.moved(
                spaceId,
                updatedBy,
                nodeFrom(result, "movedNode"),
                nodeFrom(result, "oldParent"),
                nodeFrom(result, "newParent")
        ));
        return result;
    }

    /**
     * MOVE 后的连带查询:返回被移动节点 + 旧父 + 新父的最新状态(带现算 has_children)。
     * Controller 用这个构造 move 响应。
     */
    private Map<String, Object> getMoveResult(
            UUID spaceId,
            UUID nodeId,
            UUID oldParentId,
            UUID newParentId
    ) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("movedNode", nodeRepository.findByIdWithHasChildren(spaceId, nodeId));
        if (oldParentId != null) {
            result.put(
                    "oldParent",
                    nodeRepository.findByIdWithHasChildren(spaceId, oldParentId)
            );
        }
        if (newParentId != null) {
            result.put(
                    "newParent",
                    nodeRepository.findByIdWithHasChildren(spaceId, newParentId)
            );
        }
        return result;
    }

    /**
     * DELETE 后的连带查询:返回被删节点(标记 is_deleted=true)+ 旧父节点(带现算 has_children)。
     * Controller 用这个构造 delete 响应,让前端知道旧父的 has_children 是不是变了。
     */
    private Map<String, Object> getDeleteResult(
            UUID spaceId,
            UUID nodeId,
            UUID oldParentId
    ) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put(
                "deletedNode",
                nodeRepository.findByIdWithHasChildren(spaceId, nodeId)
        );
        if (oldParentId != null) {
            result.put(
                    "oldParent",
                    nodeRepository.findByIdWithHasChildren(spaceId, oldParentId)
            );
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nodeFrom(
            Map<String, Object> result,
            String key
    ) {
        return (Map<String, Object>) result.get(key);
    }

    /**
     * 返回节点子树(嵌套 children JSON)。
     */
    public Map<String, Object> getTree(UUID nodeId, UUID spaceId) {
        RoleContext.requireAtLeast(Role.VIEWER);

        List<Map<String, Object>> flat = nodeRepository.findSubtree(nodeId, spaceId);
        if (flat.isEmpty()) return null;

        // 按 parent_id 分组
        Map<UUID, List<Map<String, Object>>> byParent = new java.util.LinkedHashMap<>();
        for (Map<String, Object> node : flat) {
            UUID parentId = (UUID) node.get("parent_id");
            byParent.computeIfAbsent(parentId, k -> new java.util.ArrayList<>()).add(node);
        }

        // 递归构建树节点
        return buildTreeNode(nodeId, byParent);
    }

    private Map<String, Object> buildTreeNode(UUID nodeId, Map<UUID, List<Map<String, Object>>> byParent) {
        // 从 byParent 中找自身(根节点的 parent_id 是它自己要匹配的方式略有不同)
        // 实际: findSubtree 返回所有节点,根节点在 flat 里,从 byParent 通过 null key 可以拿到根
        // 但更简单的方式:从 flat 重建一个 node lookup map
        for (var entry : byParent.entrySet()) {
            for (var node : entry.getValue()) {
                if (nodeId.equals(node.get("id"))) {
                    return nodeToTree(node, byParent);
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nodeToTree(Map<String, Object> node, Map<UUID, List<Map<String, Object>>> byParent) {
        Map<String, Object> tree = new java.util.LinkedHashMap<>();
        tree.put("id", node.get("id"));
        tree.put("title", node.get("title"));
        tree.put("type", node.get("type"));
        tree.put("updated_at", node.get("updated_at"));
        tree.put("updated_by", node.get("updated_by"));
        tree.put("caption", node.get("caption"));

        UUID nodeId = (UUID) node.get("id");
        List<Map<String, Object>> childList = byParent.getOrDefault(nodeId, java.util.Collections.emptyList());
        List<Map<String, Object>> children = new java.util.ArrayList<>();
        for (Map<String, Object> child : childList) {
            children.add(nodeToTree(child, byParent));
        }
        tree.put("children", children);

        return tree;
    }
}
