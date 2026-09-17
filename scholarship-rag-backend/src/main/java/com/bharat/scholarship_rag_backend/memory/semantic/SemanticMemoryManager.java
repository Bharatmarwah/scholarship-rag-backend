package com.bharat.scholarship_rag_backend.memory.semantic;

import com.bharat.scholarship_rag_backend.entity.SemanticMemory;
import com.bharat.scholarship_rag_backend.repository.SemanticMemoryRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class SemanticMemoryManager {

    @Value("${retrieval.top-k}")
    private Integer topK;

    private final SemanticMemoryRepo semanticMemoryRepo;

    public SemanticMemoryManager(SemanticMemoryRepo semanticMemoryRepo) {
        this.semanticMemoryRepo = semanticMemoryRepo;
    }

    @Transactional
    public void addSemanticMemory(SemanticMemoryRequest semanticMemoryRequest) {
        SemanticMemory memory = new SemanticMemory();
        memory.setConversationId(semanticMemoryRequest.getConversationId());
        memory.setContext(semanticMemoryRequest.getContext());
        memory.setEmbedding(semanticMemoryRequest.getEmbeddings());
        memory.setCreatedAt(LocalDateTime.now());

        semanticMemoryRepo.save(memory);
    }


    @Transactional
    public List<SemanticMemoryResponse> listAllSemanticMemories(String conversationId, float[] queryEmbedding) {
        return semanticMemoryRepo
                .findSimilarMemories(conversationId,queryEmbedding,topK);
    }


}
