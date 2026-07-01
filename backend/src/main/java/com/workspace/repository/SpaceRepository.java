package com.workspace.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.*;

/**
 * spaces 表 Repository（jOOQ DSL）。
 */
@Repository
public class SpaceRepository {

    private final DSLContext db;

    public SpaceRepository(DSLContext db) {
        this.db = db;
    }

    public Record insert(String name, UUID ownerId, UUID rootNodeId) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        db.insertInto(table("spaces"),
                        field("id", UUID.class),
                        field("name", String.class),
                        field("owner_id", UUID.class),
                        field("root_node_id", UUID.class),
                        field("created_at", OffsetDateTime.class))
                .values(id, name, ownerId, rootNodeId, now)
                .execute();

        return findById(id).orElseThrow();
    }

    public Optional<Record> findById(UUID id) {
        return db.selectFrom(table("spaces"))
                .where(field("id", UUID.class).eq(id))
                .fetchOptional();
    }

    public java.util.List<Record> findAll() {
        return db.selectFrom(table("spaces"))
                .orderBy(field("created_at").desc())
                .fetch();
    }

    public int update(UUID id, String name) {
        return db.update(table("spaces"))
                .set(field("name", String.class), name)
                .where(field("id", UUID.class).eq(id))
                .execute();
    }

    public int deleteById(UUID id) {
        return db.deleteFrom(table("spaces"))
                .where(field("id", UUID.class).eq(id))
                .execute();
    }

    public java.util.List<Record> findAllByUserId(UUID userId) {
        return db.select(table("spaces").asterisk())
                .from(table("spaces"))
                .join(table("space_members")).on(field("space_members.space_id").eq(field("spaces.id")))
                .where(field("space_members.user_id", UUID.class).eq(userId))
                .fetch();
    }
}
