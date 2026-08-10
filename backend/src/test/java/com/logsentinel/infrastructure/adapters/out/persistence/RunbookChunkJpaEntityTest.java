package com.logsentinel.infrastructure.adapters.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link RunbookChunkJpaEntity#equals(Object)} / {@code hashCode()}
 * (LOG-US2-DB-01). Verifies the project convention (see
 * `.github/skills/verify-clean-arch/SKILL.md`, Check 4): entity equality is based
 * SOLELY on the {@code id} field, for compatibility with Hibernate proxies.
 * Pure JUnit — no Spring context needed.
 */
class RunbookChunkJpaEntityTest {

    @Test
    @DisplayName("should be equal to itself")
    void should_be_equal_to_itself() {
        var entity = new RunbookChunkJpaEntity("restart the pod", new float[768]);

        assertThat(entity).isEqualTo(entity);
    }

    @Test
    @DisplayName("should not be equal to null")
    void should_not_be_equal_to_null() {
        var entity = new RunbookChunkJpaEntity("restart the pod", new float[768]);

        assertThat(entity).isNotEqualTo(null);
    }

    @Test
    @DisplayName("should not be equal to an instance of a different type")
    void should_not_be_equal_to_different_type() {
        var entity = new RunbookChunkJpaEntity("restart the pod", new float[768]);

        assertThat(entity).isNotEqualTo("not a RunbookChunkJpaEntity");
    }

    @Test
    @DisplayName("should not be equal to another transient entity (id not yet assigned)")
    void should_not_be_equal_when_both_ids_are_null() {
        var first = new RunbookChunkJpaEntity("restart the pod", new float[768]);
        var second = new RunbookChunkJpaEntity("restart the pod", new float[768]);

        // Same content, but neither has a persisted id yet -> never equal (id-based identity)
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("hashCode should be consistent across instances of the same class")
    void hash_code_should_be_consistent_across_instances() {
        var first = new RunbookChunkJpaEntity("restart the pod", new float[768]);
        var second = new RunbookChunkJpaEntity("clear the cache", new float[768]);

        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
