package com.bharat.scholarship_rag_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChatRequest {

    @NotBlank
    private String conversationId;

    @NotBlank
    private String query;

    /*
     * If true, stream the LLM response.
     * If false, return the complete response.
     */
    private boolean stream = false;

}