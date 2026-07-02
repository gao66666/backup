package com.workspace.controller;

import com.workspace.service.AuditService;
import com.workspace.service.SpaceService;
import com.workspace.util.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/spaces")
public class SpaceController {

    private final SpaceService spaceService;
    private final AuditService auditService;
    private final AuthService authService;

    public SpaceController(SpaceService spaceService, AuditService auditService, AuthService authService) {
        this.spaceService = spaceService;
        this.auditService = auditService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        UUID ownerId = authService.getCurrentUserId();
        String name = (String) body.get("name");
        return ResponseEntity.ok(ApiResponse.ok(
                spaceService.create(name, ownerId)));
    }

    @GetMapping
    public ResponseEntity<?> getAll() {
        UUID userId = authService.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                spaceService.getAllByUserId(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        Map<String, Object> result = spaceService.getById(id);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        boolean updated = spaceService.update(id, name);
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(spaceService.getById(id)));
    }

    @GetMapping("/{id}/activity")
    public ResponseEntity<?> getActivity(@PathVariable UUID id,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) UUID userId) {
        java.util.List<java.util.Map<String, Object>> items;
        int total;
        if (userId != null) {
            items = auditService.findByUser(userId, id, page, size);
            total = auditService.countByUser(userId, id);
        } else {
            items = auditService.findBySpace(id, page, size);
            total = auditService.countBySpace(id);
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        boolean deleted = spaceService.delete(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("success", true, "id", id.toString())));
    }
}