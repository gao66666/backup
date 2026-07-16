package com.workspace.controller;

import com.workspace.service.AuditService;
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
    private final AuditService auditService;
    private final AuthService authService;

    public NodeController(NodeService nodeService, AuditService auditService, AuthService authService) {
        this.nodeService = nodeService;
        this.auditService = auditService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestParam UUID spaceId, @RequestBody Map<String, Object> body) {
        // spaceId 来自 query(对齐路由组 B 其他端点)
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

    @GetMapping("/{id}/tree")
    public ResponseEntity<?> getTree(@PathVariable UUID id, @RequestParam UUID spaceId) {
        Map<String, Object> result = nodeService.getTree(id, spaceId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<?> getHistory(@PathVariable UUID id,
                                         @RequestParam UUID spaceId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        java.util.List<java.util.Map<String, Object>> items = auditService.findByNode(id, page, size);
        int total = auditService.countByNode(id);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
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

        Map<String, Object> updated = nodeService.update(spaceId, id, title, content, properties, caption, sortOrder,
                authService.getCurrentUserId());
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        UUID spaceId = RoleContext.current().spaceId();
        Map<String, Object> result = nodeService.delete(spaceId, id);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PatchMapping("/{id}/move")
    public ResponseEntity<?> move(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        UUID spaceId = RoleContext.current().spaceId();
        UUID newParentId = body.get("newParentId") != null ? UUID.fromString((String) body.get("newParentId")) : null;
        Double sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).doubleValue() : null;

        Map<String, Object> result = nodeService.move(
                spaceId,
                id,
                newParentId,
                sortOrder,
                authService.getCurrentUserId()
        );
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
