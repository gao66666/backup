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

        return ResponseEntity.ok(nodeService.create(spaceId, parentId, type, title,
                content, properties, caption, sortOrder, createdBy));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getBySpaceIdAndParentId(
            @RequestParam(required = false) UUID parentId) {
        UUID spaceId = RoleContext.current().spaceId();
        return ResponseEntity.ok(nodeService.getBySpaceIdAndParentId(spaceId, parentId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        UUID spaceId = RoleContext.current().spaceId();
        Map<String, Object> result = nodeService.getById(spaceId, id);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UUID spaceId = RoleContext.current().spaceId();
        String title = (String) body.get("title");
        String content = (String) body.get("content");
        String properties = (String) body.get("properties");
        String caption = (String) body.get("caption");
        Double sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).doubleValue() : null;

        boolean updated = nodeService.update(spaceId, id, title, content, properties, caption, sortOrder);
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        UUID spaceId = RoleContext.current().spaceId();
        boolean deleted = nodeService.delete(spaceId, id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/move")
    public ResponseEntity<?> move(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UUID spaceId = RoleContext.current().spaceId();
        UUID newParentId = body.get("newParentId") != null ? UUID.fromString((String) body.get("newParentId")) : null;
        Double sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).doubleValue() : null;
        boolean moved = nodeService.move(spaceId, id, newParentId, sortOrder);
        if (!moved) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }
}