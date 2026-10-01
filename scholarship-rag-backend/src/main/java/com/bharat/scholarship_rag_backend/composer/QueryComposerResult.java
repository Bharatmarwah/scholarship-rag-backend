package com.bharat.scholarship_rag_backend.composer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryComposerResult {

    private String combinedQuery;

    /**
     * Every scheme referred to in the inputs, as free-text names exactly as
     * the model returned them. A comparison question carries more than one.
     * Resolution to {@code SchemeType} happens only in the scheme registry,
     * never from these strings directly.
     */
    private List<String> targetSchemes;

    private Map<String, Object> extractedValues;

    public static QueryComposerResult fallback(String combinedQuery) {
        return new QueryComposerResult(combinedQuery, List.of(), Map.of());
    }
}