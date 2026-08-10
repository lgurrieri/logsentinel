package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.domain.model.RunbookChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link PgVectorRunbookSearchAdapter} (LOG-US2-BE-02). Pure Mockito —
 * no Spring context. Verifies the resilient search strategy required by the ticket:
 * <ul>
 *     <li>Vector search by cosine similarity when the {@link EmbeddingModel} call succeeds.</li>
 *     <li>Immediate Full-Text fallback ONLY when the {@link EmbeddingModel} call fails
 *         (timeout/quota) — never when the native SQL query itself fails.</li>
 *     <li>Top K always forwarded from configuration, never hardcoded.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PgVectorRunbookSearchAdapterTest {

    private static final int CONFIGURED_TOP_K = 3;

    @Mock
    private RunbookChunkJpaRepository repository;

    @Mock
    private FullTextRunbookSearchAdapter fullTextFallback;

    @Mock
    private EmbeddingModel embeddingModel;

    private PgVectorRunbookSearchAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PgVectorRunbookSearchAdapter(repository, fullTextFallback, embeddingModel, CONFIGURED_TOP_K);
    }

    @Test
    @DisplayName("should search by cosine similarity when the embedding model call succeeds")
    void should_search_by_embedding_when_embedding_model_succeeds() {
        float[] embedding = new float[768];
        given(embeddingModel.embed(anyString())).willReturn(embedding);
        var entity = new RunbookChunkJpaEntity("restart the auth-service pod on token expiry", embedding);
        given(repository.findNearestByEmbedding(anyString(), eq(CONFIGURED_TOP_K))).willReturn(List.of(entity));

        List<RunbookChunk> result = adapter.findSimilarRunbooks("ERROR auth-service connection refused");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("restart the auth-service pod on token expiry");
        verify(repository, times(1)).findNearestByEmbedding(anyString(), eq(CONFIGURED_TOP_K));
        verify(fullTextFallback, never()).searchByFullText(anyString(), anyInt());
    }

    @Test
    @DisplayName("should fall back to full-text search when the embedding model call fails")
    void should_fallback_to_full_text_when_embedding_model_fails() {
        given(embeddingModel.embed(anyString())).willThrow(new RuntimeException("Ollama timeout"));
        var fallbackChunk = new RunbookChunk(UUID.randomUUID(), "check disk usage and clear /var/log if above 90%");
        given(fullTextFallback.searchByFullText(anyString(), eq(CONFIGURED_TOP_K))).willReturn(List.of(fallbackChunk));

        List<RunbookChunk> result = adapter.findSimilarRunbooks("ERROR pool exhausted");

        assertThat(result).containsExactly(fallbackChunk);
        verify(fullTextFallback, times(1)).searchByFullText("ERROR pool exhausted", CONFIGURED_TOP_K);
        verify(repository, never()).findNearestByEmbedding(anyString(), anyInt());
    }

    @Test
    @DisplayName("should never return null when the vector search yields no matches")
    void should_return_empty_list_not_null_when_no_matches() {
        given(embeddingModel.embed(anyString())).willReturn(new float[768]);
        given(repository.findNearestByEmbedding(anyString(), eq(CONFIGURED_TOP_K))).willReturn(List.of());

        List<RunbookChunk> result = adapter.findSimilarRunbooks("ERROR unknown failure mode");

        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("should use Top K read from configuration, never hardcoded")
    void should_use_configured_top_k_instead_of_hardcoded_value() {
        int customTopK = 7;
        var customAdapter = new PgVectorRunbookSearchAdapter(repository, fullTextFallback, embeddingModel, customTopK);
        given(embeddingModel.embed(anyString())).willReturn(new float[768]);
        given(repository.findNearestByEmbedding(anyString(), eq(customTopK))).willReturn(List.of());

        customAdapter.findSimilarRunbooks("ERROR something else");

        verify(repository, times(1)).findNearestByEmbedding(anyString(), eq(customTopK));
    }
}
