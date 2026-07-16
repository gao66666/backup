package com.workspace.collaboration;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 空间级文件树事件。
 *
 * 数据库事务提交后发送到 /topic/spaces/{spaceId}/nodes。
 */
public record NodeTreeEvent(
        UUID eventId,
        String type,
        UUID spaceId,
        UUID nodeId,
        UUID actorUserId,
        OffsetDateTime occurredAt,
        Map<String, Object> node,
        Map<String, Object> oldParent,
        Map<String, Object> newParent
) {

    public static final String CREATED = "node.created";
    public static final String UPDATED = "node.updated";
    public static final String MOVED = "node.moved";
    public static final String DELETED = "node.deleted";

    public NodeTreeEvent {
        node = immutableCopy(node);
        oldParent = immutableCopy(oldParent);
        newParent = immutableCopy(newParent);
    }

    public static NodeTreeEvent created(
            UUID spaceId,
            UUID actorUserId,
            Map<String, Object> node
    ) {
        return event(CREATED, spaceId, actorUserId, node, null, null);
    }

    public static NodeTreeEvent updated(
            UUID spaceId,
            UUID actorUserId,
            Map<String, Object> node
    ) {
        return event(UPDATED, spaceId, actorUserId, node, null, null);
    }

    public static NodeTreeEvent moved(
            UUID spaceId,
            UUID actorUserId,
            Map<String, Object> node,
            Map<String, Object> oldParent,
            Map<String, Object> newParent
    ) {
        return event(MOVED, spaceId, actorUserId, node, oldParent, newParent);
    }

    public static NodeTreeEvent deleted(
            UUID spaceId,
            UUID actorUserId,
            Map<String, Object> node,
            Map<String, Object> oldParent
    ) {
        return event(DELETED, spaceId, actorUserId, node, oldParent, null);
    }

    private static NodeTreeEvent event(
            String type,
            UUID spaceId,
            UUID actorUserId,
            Map<String, Object> node,
            Map<String, Object> oldParent,
            Map<String, Object> newParent
    ) {
        Object rawNodeId = node == null ? null : node.get("id");
        if (!(rawNodeId instanceof UUID nodeId)) {
            throw new IllegalArgumentException("Node tree event requires a UUID node id");
        }

        return new NodeTreeEvent(
                UUID.randomUUID(),
                type,
                spaceId,
                nodeId,
                actorUserId,
                OffsetDateTime.now(),
                node,
                oldParent,
                newParent
        );
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
