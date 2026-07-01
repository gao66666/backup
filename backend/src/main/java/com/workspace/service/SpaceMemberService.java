package com.workspace.service;

import com.workspace.repository.SpaceMemberRepository;
import org.jooq.Record;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class SpaceMemberService {

    private final SpaceMemberRepository spaceMemberRepository;
    private final AuditService auditService;

    public SpaceMemberService(SpaceMemberRepository spaceMemberRepository,
                             AuditService auditService) {
        this.spaceMemberRepository = spaceMemberRepository;
        this.auditService = auditService;
    }

    public Map<String, Object> create(UUID spaceId, UUID userId, String role) {
        RoleContext.requireAtLeast(Role.ADMIN);
        Record record = spaceMemberRepository.insert(spaceId, userId, role);
        Map<String, Object> result = record.intoMap();
        auditService.log("member.add", "member", (UUID) result.get("id"),
                "{\"spaceId\":\"" + spaceId + "\",\"userId\":\"" + userId + "\",\"role\":\"" + role + "\"}");
        return result;
    }

    public Map<String, Object> getById(UUID id) {
        return spaceMemberRepository.findById(id)
                .map(Record::intoMap)
                .orElse(null);
    }

    public java.util.List<Map<String, Object>> getAll() {
        return spaceMemberRepository.findAll().stream()
                .map(Record::intoMap)
                .toList();
    }

    public java.util.List<Map<String, Object>> getBySpaceId(UUID spaceId) {
        RoleContext.requireAtLeast(Role.VIEWER);
        return spaceMemberRepository.findBySpaceId(spaceId).stream()
                .map(Record::intoMap)
                .toList();
    }

    public boolean update(UUID id, String role) {
        RoleContext.requireAtLeast(Role.ADMIN);
        // 先查现有成员信息
        Record existing = spaceMemberRepository.findById(id).orElse(null);
        if (existing == null) return false;
        UUID spaceId = existing.get(field("space_id", UUID.class));
        UUID targetUserId = existing.get(field("user_id", UUID.class));
        boolean updated = spaceMemberRepository.update(id, role) > 0;
        if (updated) {
            auditService.log("member.update", "member", id,
                    "{\"spaceId\":\"" + spaceId + "\",\"userId\":\"" + targetUserId + "\",\"role\":\"" + role + "\"}");
        }
        return updated;
    }

    public boolean delete(UUID id) {
        RoleContext.requireAtLeast(Role.ADMIN);
        Record existing = spaceMemberRepository.findById(id).orElse(null);
        if (existing == null) return false;
        UUID spaceId = existing.get(field("space_id", UUID.class));
        UUID targetUserId = existing.get(field("user_id", UUID.class));
        boolean deleted = spaceMemberRepository.deleteById(id) > 0;
        if (deleted) {
            auditService.log("member.remove", "member", id,
                    "{\"spaceId\":\"" + spaceId + "\",\"userId\":\"" + targetUserId + "\"}");
        }
        return deleted;
    }

    public String getRole(UUID userId, UUID spaceId) {
        return spaceMemberRepository.findRole(userId, spaceId);
    }

    private static org.jooq.Field<UUID> field(String name, Class<UUID> type) {
        return org.jooq.impl.DSL.field(name, type);
    }
}