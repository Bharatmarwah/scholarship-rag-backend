package com.bharat.scholarship_rag_backend.memory.semantic;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SemanticMemorySelector {

    private static final double MAX_DISTANCE = 0.30;
    private static final int MAX_MEMORIES = 5;

    public List<SemanticMemoryResponse> select(
            List<SemanticMemoryResponse> memories) {

        return memories.stream()
                .filter(memory ->
                        memory.getDistance() != null &&
                        memory.getDistance() <= MAX_DISTANCE)
                .limit(MAX_MEMORIES)
                .toList();
    }
}