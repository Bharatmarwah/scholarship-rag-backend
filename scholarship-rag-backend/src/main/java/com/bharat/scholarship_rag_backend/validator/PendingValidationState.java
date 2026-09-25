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

    private record Pending(SchemeType targetScheme, List<StudentProfileField> askedFields) {
    }

    private final Map<String, Pending> pendingByConversation = new ConcurrentHashMap<>();

    public void store(String conversationId, SchemeType targetScheme, List<StudentProfileField> askedFields) {
        if (conversationId == null || targetScheme == null) {
            return;
        }
        pendingByConversation.put(conversationId, new Pending(targetScheme, askedFields));
    }

    public boolean hasPending(String conversationId) {
        return conversationId != null && pendingByConversation.containsKey(conversationId);
    }

    public SchemeType targetScheme(String conversationId) {
        Pending pending = pendingByConversation.get(conversationId);
        return pending == null ? null : pending.targetScheme();
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