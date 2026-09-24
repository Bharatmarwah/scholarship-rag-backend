package com.bharat.scholarship_rag_backend.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntentResponse {
    private IntentType type;

    public static IntentResponse forType(IntentType type) {
        return new IntentResponse(type);
    }
}