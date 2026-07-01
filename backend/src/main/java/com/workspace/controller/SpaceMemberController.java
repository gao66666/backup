package com.workspace.controller;

import com.workspace.service.RoleContext;
import com.workspace.service.SpaceMemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/space-members")
public class SpaceMemberController {

    private final SpaceMemberService spaceMemberService;

    public SpaceMemberController(SpaceMemberService spaceMemberService) {
        this.spaceMemberService = spaceMemberService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        UUID spaceId = RoleContext.current().spaceId();
        UUID userId = UUID.fromString((String) body.get("userId"));
        String role = (String) body.get("role");
        return ResponseEntity.ok(spaceMemberService.create(spaceId, userId, role));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getBySpaceId() {
        UUID spaceId = RoleContext.current().spaceId();
        return ResponseEntity.ok(spaceMemberService.getBySpaceId(spaceId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        Map<String, Object> result = spaceMemberService.getById(id);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String role = (String) body.get("role");
        boolean updated = spaceMemberService.update(id, role);
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        boolean deleted = spaceMemberService.delete(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }
}