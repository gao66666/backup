package com.workspace.controller;

import com.workspace.service.PermissionService.ForbiddenException;
import com.workspace.service.SpaceService;
import com.workspace.util.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/spaces")
public class SpaceController {

    private final SpaceService spaceService;
    private final AuthService authService;

    public SpaceController(SpaceService spaceService, AuthService authService) {
        this.spaceService = spaceService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        UUID ownerId = authService.getCurrentUserId();
        String name = (String) body.get("name");
        return ResponseEntity.ok(spaceService.create(name, ownerId, null));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        UUID userId = authService.getCurrentUserId();
        return ResponseEntity.ok(spaceService.getAllByUserId(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        Map<String, Object> result = spaceService.getById(id);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        boolean updated = spaceService.update(id, name);
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        boolean deleted = spaceService.delete(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
    }
}
