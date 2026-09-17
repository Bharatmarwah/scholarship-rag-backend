package com.bharat.scholarship_rag_backend.memory.semantic;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SemanticMemoryRequest {

    private String conversationId;
    private String context;
    private float[] embeddings;

}
