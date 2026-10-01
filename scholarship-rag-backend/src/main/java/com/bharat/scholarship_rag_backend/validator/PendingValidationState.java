package com.bharat.scholarship_rag_backend.validator;

import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import com.bharat.scholarship_rag_backend.scheme.StudentProfileField;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-conversation memory of the currently active validation. This is the
 * same single-intent continuation of the user's original request, not a
 * multi-intent feature. It lets follow-up turns (e.g. "92%, and I'm at DU")
 * keep validating the scheme already in progress.
 */
@Component
public class PendingValidationState {

    private record Pending(List<SchemeType> targetSchemes, List<StudentProfileField> askedFields) {
    }

    private final Map<String, Pending> pendingByConversation = new ConcurrentHashMap<>();

    public void store(String conversationId, SchemeType targetScheme, List<StudentProfileField> askedFields) {
        store(conversationId, targetScheme == null ? List.of() : List.of(targetScheme), askedFields);
    }

    public void store(String conversationId, List<SchemeType> targetSchemes, List<StudentProfileField> askedFields) {
        if (conversationId == null || targetSchemes == null || targetSchemes.isEmpty()) {
            return;
        }
        pendingByConversation.put(conversationId, new Pending(List.copyOf(targetSchemes), askedFields));
    }

    public boolean hasPending(String conversationId) {
        return conversationId != null && pendingByConversation.containsKey(conversationId);
    }

    /**
     * The single scheme being validated, or null when the pending intent spans
     * several schemes and therefore has no single scheme to continue.
     */
    public SchemeType targetScheme(String conversationId) {
        List<SchemeType> schemes = targetSchemes(conversationId);
        return schemes.size() == 1 ? schemes.getFirst() : null;
    }

    public List<SchemeType> targetSchemes(String conversationId) {
        Pending pending = pendingByConversation.get(conversationId);
        return pending == null ? List.of() : pending.targetSchemes();
    }

    public List<StudentProfileField> askedFields(String conversationId) {
        Pending pending = pendingByConversation.get(conversationId);
        return pending == null ? List.of() : pending.askedFields();
    }

    public void clear(String conversationId) {
        if (conversationId != null) {
            pendingByConversation.remove(conversationId);
        }
    }
}