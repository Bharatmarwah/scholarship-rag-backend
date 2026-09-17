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
                context,
                embedding <=> CAST(:queryEmbedding AS vector) AS distance
            FROM semantic_memories
            WHERE conversation_id = :conversationId
            ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<SemanticMemoryResponse> findSimilarMemories(
            @Param("conversationId") String conversationId,
            @Param("queryEmbedding") float[] queryEmbedding,
            @Param("topK") Integer topK
    );
}