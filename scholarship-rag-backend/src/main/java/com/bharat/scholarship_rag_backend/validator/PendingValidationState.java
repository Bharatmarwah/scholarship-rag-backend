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

    /**
     * Remembers the fields last asked in this conversation. The target scheme
     * list may be empty for discovery, where there is no scheme to continue but
     * the asked fields are still needed to detect a repeated question and to
     * resolve a plain answer back onto the right field.
     */
    public void store(String conversationId, List<SchemeType> targetSchemes, List<StudentProfileField> askedFields) {
        if (conversationId == null) {
            return;
        }
        List<SchemeType> schemes = targetSchemes == null ? List.of() : List.copyOf(targetSchemes);
        List<StudentProfileField> fields = askedFields == null ? List.of() : List.copyOf(askedFields);
        pendingByConversation.put(conversationId, new Pending(schemes, fields));
    }

    /**
     * True only when a specific or multi-scheme intent is in progress. A
     * discovery-only pending entry carries no scheme and must not be treated as
     * a continuation, so the discovery path keeps running.
     */
    public boolean hasPending(String conversationId) {
        if (conversationId == null) {
            return false;
        }
        Pending pending = pendingByConversation.get(conversationId);
        return pending != null && !pending.targetSchemes().isEmpty();
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