package com.bharat.scholarship_rag_backend.repository;

import com.bharat.scholarship_rag_backend.memory.semantic.SemanticMemoryResponse;
import com.bharat.scholarship_rag_backend.entity.SemanticMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SemanticMemoryRepo extends JpaRepository<SemanticMemory, UUID> {

    @Query(value = """
            SELECT
                m.context,
                m.embedding <=> CAST(:queryEmbedding AS vector) AS distance
            FROM memories m
            JOIN semantic_memories sm ON sm.id = m.semantic_memory_id
            WHERE sm.conversation_id = :conversationId
              AND m.embedding <=> CAST(:queryEmbedding AS vector) <= 0.30
            ORDER BY m.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<SemanticMemoryResponse> findSimilarMemories(
            @Param("conversationId") String conversationId,
            @Param("queryEmbedding") float[] queryEmbedding,
            @Param("topK") Integer topK
    );
}