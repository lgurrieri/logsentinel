package com.logsentinel.application.ports.out;

import com.logsentinel.domain.model.RunbookChunk;

import java.util.List;

/**
 * Driven port (SPI) for retrieving the runbook chunks most relevant to a given raw
 * incident log. Pure Java interface — NO import of Spring, JPA or Spring AI here.
 * <p>
 * Implementations own the full resilience strategy required by LOG-US2-BE-02:
 * vectorizing the log with the active {@code EmbeddingModel} (Ollama by default,
 * OpenAI under the {@code openai} profile) and searching by cosine similarity,
 * falling back immediately to a traditional Full-Text search if that embedding call
 * fails (timeout/quota). Callers of this port (e.g. the future AgentOrchestrator, US3)
 * never see that fallback decision — they only ever get back an ordered list of
 * runbook chunks.
 */
public interface RunbookSearchPort {

    /**
     * Finds the Top K runbook chunks most relevant to the given raw incident log.
     * Top K is configured via {@code logsentinel.rag.top-k} (default 3), never
     * hardcoded by the implementation.
     *
     * @param rawLog the raw incident log snapshot to search against
     * @return the most relevant runbook chunks, ordered by relevance (never null)
     */
    List<RunbookChunk> findSimilarRunbooks(String rawLog);
}
