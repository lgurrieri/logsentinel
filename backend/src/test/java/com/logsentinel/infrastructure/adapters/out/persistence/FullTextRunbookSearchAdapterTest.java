package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.domain.model.RunbookChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link FullTextRunbookSearchAdapter} (LOG-US2-BE-02). Pure Mockito —
 * no Spring context, no database. Verifies mapping to the domain model and that the
 * given Top K limit is forwarded as-is to the repository (never hardcoded here).
 */
@ExtendWith(MockitoExtension.class)
class FullTextRunbookSearchAdapterTest {

    @Mock
    private RunbookChunkJpaRepository repository;

    private FullTextRunbookSearchAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FullTextRunbookSearchAdapter(repository);
    }

    @Test
    @DisplayName("should map matched entities to domain runbook chunks")
    void should_map_entities_to_domain() {
        var entity = new RunbookChunkJpaEntity("clear the cache and restart the service", new float[768]);
        given(repository.findByFullText("cache restart", 3)).willReturn(List.of(entity));

        List<RunbookChunk> result = adapter.searchByFullText("cache restart", 3);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("clear the cache and restart the service");
    }

    @Test
    @DisplayName("should forward the given Top K limit to the repository")
    void should_forward_top_k_to_repository() {
        given(repository.findByFullText(eq("timeout"), eq(5))).willReturn(List.of());

        adapter.searchByFullText("timeout", 5);

        verify(repository, times(1)).findByFullText("timeout", 5);
    }

    @Test
    @DisplayName("should return an empty list, never null, when there are no matches")
    void should_return_empty_list_when_no_matches() {
        given(repository.findByFullText(eq("no matches at all"), eq(3))).willReturn(List.of());

        List<RunbookChunk> result = adapter.searchByFullText("no matches at all", 3);

        assertThat(result).isNotNull().isEmpty();
    }
}
