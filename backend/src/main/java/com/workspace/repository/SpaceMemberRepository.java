package com.workspace.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.jooq.impl.DSL.*;

/**
 * space_members 表 Repository（jOOQ DSL）。
 */
@Repository
public class SpaceMemberRepository {

    private final DSLContext db;

    public SpaceMemberRepository(DSLContext db) {
        this.db = db;
    }

    public Record insert(UUID spaceId, UUID userId, String role) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        db.insertInto(table("space_members"),
                        field("id", UUID.class),
                        field("space_id", UUID.class),
                        field("user_id", UUID.class),
                        field("role", String.class),
                        field("joined_at", OffsetDateTime.class))
                .values(id, spaceId, userId, role, now)
                .execute();

        return findById(id).orElseThrow();
    }

    public Optional<Record> findById(UUID id) {
        return db.selectFrom(table("space_members"))
                .where(field("id", UUID.class).eq(id))
                .fetchOptional();
    }

    public java.util.List<Record> findAll() {
        return db.selectFrom(table("space_members"))
                .orderBy(field("joined_at").desc())
                .fetch();
    }

    public int update(UUID id, String role) {
        return db.update(table("space_members"))
                .set(field("role", String.class), role)
                .where(field("id", UUID.class).eq(id))
                .execute();
    }

    public int deleteById(UUID id) {
        return db.deleteFrom(table("space_members"))
                .where(field("id", UUID.class).eq(id))
                .execute();
    }

    public java.util.List<Record> findBySpaceId(UUID spaceId) {
        return db.selectFrom(table("space_members"))
                .where(field("space_id", UUID.class).eq(spaceId))
                .orderBy(field("joined_at").asc())
                .fetch();
    }

    public String findRole(UUID userId, UUID spaceId) {
        return db.select(field("role"))
                .from(table("space_members"))
                .where(field("user_id", UUID.class).eq(userId))
                .and(field("space_id", UUID.class).eq(spaceId))
                .fetchOne(field("role"), String.class);
    }
}
