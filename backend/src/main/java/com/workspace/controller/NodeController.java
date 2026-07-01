package com.workspace.controller;

import com.workspace.service.NodeService;
import com.workspace.service.RoleContext;
import com.workspace.util.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/nodes")
public class NodeController {

    private final NodeService nodeService;
    private final AuthService authService;

    public NodeController(NodeService nodeService, AuthService authService) {
        this.nodeService = nodeService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        UUID spaceId = RoleContext.current().spaceId();
        UUID parentId = body.get("parentId") != null ? UUID.fromString((String) body.get("parentId")) : null;
        String type = (String) body.get("type");
        String title = (String) body.get("title");
        String content = (String) body.get("content");
        String properties = (String) body.get("properties");
        String caption = (String) body.get("caption");
        Double sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).doubleValue() : null;
        UUID createdBy = authService.getCurrentUserId();

        return ResponseEntity.ok(ApiResponse.ok(
                nodeService.create(spaceId, parentId, type, title,
                        content, properties, caption, sortOrder, createdBy)));
    }

    @GetMapping
    public ResponseEntity<?> getBySpaceIdAndParentId(
            @RequestParam(required = false) UUID parentId) {
        UUID spaceId = RoleContext.current().spaceId();
        return ResponseEntity.ok(ApiResponse.ok(
                nodeService.getBySpaceIdAndParentId(spaceId, parentId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        UUID spaceId = RoleContext.current().spaceId();
        Map<String, Object> result = nodeService.getById(spaceId, id);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UUID spaceId = RoleContext.current().spaceId();
        String title = (String) body.get("title");
        String content = (String) body.get("content");
        String properties = (String) body.get("properties");
        String caption = (String) body.get("caption");
        Double sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).doubleValue() : null;

        Map<String, Object> updated = nodeService.update(spaceId, id, title, content, properties, caption, sortOrder);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        UUID spaceId = RoleContext.current().spaceId();
        boolean deleted = nodeService.delete(spaceId, id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("success", true, "id", id.toString())));
    }

    @PatchMapping("/{id}/move")
    public ResponseEntity<?> move(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UUID spaceId = RoleContext.current().spaceId();
        UUID newParentId = body.get("newParentId") != null ? UUID.fromString((String) body.get("newParentId")) : null;
        Double sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).doubleValue() : null;

        // 先查旧 parent(因为 move 内部也要查一次,这里手动查以拿旧 parent_id)
        java.util.Map<String, Object> before = nodeService.getById(spaceId, id);
        if (before == null) return ResponseEntity.notFound().build();
        UUID oldParentId = (UUID) before.get("parent_id");

        boolean moved = nodeService.move(spaceId, id, newParentId, sortOrder);
        if (!moved) return ResponseEntity.notFound().build();

        // 返回 movedNode + oldParent + newParent,让前端一次性更新三处 has_children
        return ResponseEntity.ok(ApiResponse.ok(
                nodeService.getMoveResult(id, oldParentId, newParentId)));
    }
}