package com.workspace.service;

import com.workspace.repository.NodeRepository;
import com.workspace.repository.SpaceMemberRepository;
import com.workspace.repository.SpaceRepository;
import org.jooq.Record;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class SpaceService {

    private final SpaceRepository spaceRepository;
    private final NodeRepository nodeRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final AuditService auditService;

    public SpaceService(SpaceRepository spaceRepository, NodeRepository nodeRepository,
                        SpaceMemberRepository spaceMemberRepository, AuditService auditService) {
        this.spaceRepository = spaceRepository;
        this.nodeRepository = nodeRepository;
        this.spaceMemberRepository = spaceMemberRepository;
        this.auditService = auditService;
    }

    public Map<String, Object> create(String name, UUID ownerId) {
        // 预先生成两边 UUID: 先建 space(满足 nodes.space_id FK),再建 root node
        UUID spaceId = UUID.randomUUID();
        UUID rootNodeId = UUID.randomUUID();

        // 1. 创建空间(spaces.root_node_id 没有 FK 约束, 可先填)
        spaceRepository.insert(spaceId, name, ownerId, rootNodeId);

        // 2. 创建 root node(collection 类型)
        nodeRepository.insert(rootNodeId, spaceId, null, "collection", name,
                "{}", "{}", null, 0.0, ownerId);

        // 3. 将创建者加为 OWNER
        spaceMemberRepository.insert(spaceId, ownerId, "owner");

        // 4. 审计
        auditService.log(spaceId, "space.create", "space", spaceId,
                "{\"name\":\"" + name + "\"}");

        return spaceRepository.findById(spaceId).map(Record::intoMap).orElseThrow();
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
        RoleContext.requireAtLeast(Role.ADMIN);
        boolean updated = spaceRepository.update(id, name) > 0;
        if (updated) {
            auditService.log(id, "space.update", "space", id,
                    "{\"spaceId\":\"" + id + "\"}");
        }
        return updated;
    }

    public boolean delete(UUID id) {
        RoleContext.requireAtLeast(Role.OWNER);
        boolean deleted = spaceRepository.deleteById(id) > 0;
        if (deleted) {
            auditService.log(id, "space.delete", "space", id,
                    "{\"spaceId\":\"" + id + "\"}");
        }
        return deleted;
    }
}