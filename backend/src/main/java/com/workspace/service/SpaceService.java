package com.workspace.service;

import com.workspace.repository.SpaceRepository;
import org.jooq.Record;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class SpaceService {

    private final SpaceRepository spaceRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;

    public SpaceService(SpaceRepository spaceRepository, PermissionService permissionService, AuditService auditService) {
        this.spaceRepository = spaceRepository;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    public Map<String, Object> create(String name, UUID ownerId, UUID rootNodeId) {
        Record record = spaceRepository.insert(name, ownerId, rootNodeId);
        return record.intoMap();
    }

    public Map<String, Object> getById(UUID id) {
        return spaceRepository.findById(id)
                .map(Record::intoMap)
                .orElse(null);
    }

    public java.util.List<Map<String, Object>> getAll() {
        return spaceRepository.findAll().stream()
                .map(Record::intoMap)
                .toList();
    }

    public java.util.List<Map<String, Object>> getAllByUserId(UUID userId) {
        return spaceRepository.findAllByUserId(userId).stream()
                .map(Record::intoMap)
                .toList();
    }

    public boolean update(UUID id, String name) {
        permissionService.checkCanEdit(id);
        boolean updated = spaceRepository.update(id, name) > 0;
        if (updated) {
            auditService.log("space.update", "space", id,
                    "{\"spaceId\":\"" + id + "\"}");
        }
        return updated;
    }

    public boolean delete(UUID id) {
        permissionService.checkCanDeleteSpace(id);
        boolean deleted = spaceRepository.deleteById(id) > 0;
        if (deleted) {
            auditService.log("space.delete", "space", id,
                    "{\"spaceId\":\"" + id + "\"}");
        }
        return deleted;
    }
}
