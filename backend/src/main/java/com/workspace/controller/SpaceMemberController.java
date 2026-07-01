package com.workspace.controller;

import com.workspace.service.AuditService;
import com.workspace.service.PermissionService;
import com.workspace.service.PermissionService.ForbiddenException;
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
    private final PermissionService permissionService;
    private final AuditService auditService;

    public SpaceMemberController(SpaceMemberService spaceMemberService, PermissionService permissionService,
                                AuditService auditService) {
        this.spaceMemberService = spaceMemberService;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        UUID spaceId = UUID.fromString((String) body.get("spaceId"));
        permissionService.checkCanManageMember(spaceId);

        UUID userId = UUID.fromString((String) body.get("userId"));
        String role = (String) body.get("role");
        Map<String, Object> result = spaceMemberService.create(spaceId, userId, role);

        auditService.log("member.add", "member", null,
                "{\"spaceId\":\"" + spaceId + "\",\"userId\":\"" + userId + "\",\"role\":\"" + role + "\"}");
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getBySpaceId(@RequestParam UUID spaceId) {
        permissionService.checkCanView(spaceId);
        return ResponseEntity.ok(spaceMemberService.getBySpaceId(spaceId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id) {
        Map<String, Object> result = spaceMemberService.getById(id);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        // 校验用户是否在该空间
        UUID spaceId = UUID.fromString(result.get("space_id").toString());
        permissionService.checkCanView(spaceId);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        // 先查出现在角色
        Map<String, Object> existing = spaceMemberService.getById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        UUID spaceId = UUID.fromString(existing.get("space_id").toString());
        UUID targetUserId = UUID.fromString(existing.get("user_id").toString());
        permissionService.checkCanManageMember(spaceId, targetUserId);

        String role = (String) body.get("role");
        boolean updated = spaceMemberService.update(id, role);
        if (!updated) {
            return ResponseEntity.notFound().build();
        }

        auditService.log("member.update", "member", id,
                "{\"spaceId\":\"" + spaceId + "\",\"userId\":\"" + targetUserId + "\",\"role\":\"" + role + "\"}");
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        Map<String, Object> existing = spaceMemberService.getById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        UUID spaceId = UUID.fromString(existing.get("space_id").toString());
        UUID targetUserId = UUID.fromString(existing.get("user_id").toString());
        permissionService.checkCanManageMember(spaceId, targetUserId);

        boolean deleted = spaceMemberService.delete(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }

        auditService.log("member.remove", "member", id,
                "{\"spaceId\":\"" + spaceId + "\",\"userId\":\"" + targetUserId + "\"}");
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
    }
}
