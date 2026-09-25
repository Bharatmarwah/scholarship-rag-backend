package com.bharat.scholarship_rag_backend.composer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryComposerResult {

    private String combinedQuery;

    private String targetScheme;

    private Map<String, Object> extractedValues;

    public static QueryComposerResult fallback(String combinedQuery) {
        return new QueryComposerResult(combinedQuery, null, Map.of());
    }
}