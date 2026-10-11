package com.bharat.scholarship_rag_backend.validator;

import com.bharat.scholarship_rag_backend.dto.StudentProfileDto;
import com.bharat.scholarship_rag_backend.scheme.ProfileFieldAccess;
import com.bharat.scholarship_rag_backend.scheme.SchemeDefinition;
import com.bharat.scholarship_rag_backend.scheme.SchemeRegistry;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import com.bharat.scholarship_rag_backend.scheme.StudentProfileField;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Deterministic validator. Decides only whether enough information is known:
 * READY or MISSING_INFORMATION. It never decides eligibility.
 *
* <p>Mode selection (single intent continuity):
     * <pre>
     * current turn names a scheme        → SPECIFIC_SCHEME (overrides pending)
     * current turn names many schemes    → MULTI_SCHEME (fields asked per scheme)
     * else one scheme is pending         → SPECIFIC_SCHEME (same intent continues)
     * else many schemes are pending      → MULTI_SCHEME (same comparison continues)
     * else                               → SCHOLARSHIP_DISCOVERY
     * </pre>
 *
 * <p>Discovery uses the cross-scheme screening order owned by the registry.
 * That order is NOT a set of fields essential for every scheme; each
 * {@link SchemeDefinition} decides which fields actually matter. Fields that
 * make no sense for the known education stage are skipped (a college student
 * is not asked for their school class, a school student not for their
 * college year).
 */
@Component
public class ValidationEngine {

    /**
     * How many missing fields are asked in a single multi-scheme turn. The
     * union of two schemes' essentials can reach ten fields, which cannot be
     * put to a student in one message.
     */
    private static final int MAX_FIELDS_PER_TURN = 3;

    private final SchemeRegistry schemeRegistry;
    private final ProfileFieldAccess profileFieldAccess;
    private final QuestionBuilder questionBuilder;
    private final PendingValidationState pendingState;

    public ValidationEngine(
            SchemeRegistry schemeRegistry,
            ProfileFieldAccess profileFieldAccess,
            QuestionBuilder questionBuilder,
            PendingValidationState pendingState) {
        this.schemeRegistry = schemeRegistry;
        this.profileFieldAccess = profileFieldAccess;
        this.questionBuilder = questionBuilder;
        this.pendingState = pendingState;
    }

    /**
     * Single-scheme entry point, kept for callers that resolve one scheme.
     */
    public ValidationResult validate(
            StudentProfileDto profile,
            String rawTargetScheme,
            String conversationId) {

        return validateAll(
                profile,
                rawTargetScheme == null ? List.<String>of() : List.of(rawTargetScheme),
                conversationId);
    }

    /**
     * Accepts every scheme referred to in the turn, so that a comparison
     * question is not reduced to a single scheme.
     *
     * <p>Named {@code validateAll} rather than overloaded on {@link #validate}
     * so that a call passing {@code null} stays unambiguous.
     *
     * <p>Mode selection:
     * <pre>
     * current turn names one scheme   → SPECIFIC_SCHEME (overrides pending)
     * current turn names many schemes → MULTI_SCHEME
     * else pending scheme exists      → SPECIFIC_SCHEME (same intent continues)
     * else                            → SCHOLARSHIP_DISCOVERY
     * </pre>
     */
    public ValidationResult validateAll(
            StudentProfileDto profile,
            List<String> rawTargetSchemes,
            String conversationId) {
        return validateForSchemes(profile, rawTargetSchemes, conversationId, false);
    }

    /**
     * As {@link #validateAll}, but when the student's own turn is an
     * informational question ("what is a social category?") the missing-field
     * questions are suppressed: the result is always READY so the question is
     * answered from the knowledge base. Pending validation is left untouched, so
     * profile collection resumes on the student's next real answer.
     */
    public ValidationResult validateAll(
            StudentProfileDto profile,
            List<String> rawTargetSchemes,
            String conversationId,
            boolean asQuestion) {
        return validateForSchemes(profile, rawTargetSchemes, conversationId, asQuestion);
    }

    private ValidationResult validateForSchemes(
            StudentProfileDto profile,
            List<String> rawTargetSchemes,
            String conversationId,
            boolean asQuestion) {

        List<SchemeType> namedSchemes =
                resolveNamedSchemes(rawTargetSchemes);

        // The schemes this turn is actually about: the ones named now, or the
        // ones already in progress when nothing new was named.
        List<SchemeType> activeSchemes;
        ValidationMode mode;
        SchemeType targetScheme = null;

        if (namedSchemes.size() > 1) {
            // A newly named comparison overrides any single scheme in progress.
            activeSchemes = namedSchemes;
            mode = ValidationMode.MULTI_SCHEME;
            pendingState.clear(conversationId);
        } else if (namedSchemes.size() == 1) {
            // A scheme explicitly named this turn takes priority and starts
            // (or overrides) this single intent.
            activeSchemes = namedSchemes;
            mode = ValidationMode.SPECIFIC_SCHEME;
            targetScheme = namedSchemes.getFirst();
            pendingState.clear(conversationId);
        } else if (pendingState.hasPending(conversationId)) {
            // No scheme named this turn, so the intent already in progress
            // continues. It may itself span several schemes.
            activeSchemes = pendingState.targetSchemes(conversationId);
            mode = activeSchemes.size() > 1
                    ? ValidationMode.MULTI_SCHEME
                    : ValidationMode.SPECIFIC_SCHEME;
            targetScheme = activeSchemes.size() == 1 ? activeSchemes.getFirst() : null;
        } else {
            activeSchemes = List.of();
            mode = ValidationMode.SCHOLARSHIP_DISCOVERY;
        }

        return switch (mode) {
            case SPECIFIC_SCHEME -> validateSpecific(profile, targetScheme, conversationId, asQuestion);
            case MULTI_SCHEME -> validateMultiScheme(profile, activeSchemes, conversationId, asQuestion);
            case SCHOLARSHIP_DISCOVERY -> validateDiscovery(profile, conversationId, asQuestion);
        };
    }

    /**
     * Resolves the raw references through the registry, which is the only
     * place free text becomes a {@link SchemeType}. Names that do not resolve
     * are dropped rather than guessed.
     */
    private List<SchemeType> resolveNamedSchemes(List<String> rawTargetSchemes) {
        if (rawTargetSchemes == null || rawTargetSchemes.isEmpty()) {
            return List.of();
        }
        List<SchemeType> resolved = new ArrayList<>();
        for (String raw : rawTargetSchemes) {
            for (SchemeType scheme : schemeRegistry.resolveAll(raw)) {
                if (!resolved.contains(scheme)) {
                    resolved.add(scheme);
                }
            }
        }
        return resolved;
    }

    /**
     * A comparison question is answered from the named schemes together, so the
     * fields needed by any of them are collected.
     *
     * <p>Unlike the single-scheme case there is no one checklist to complete:
     * each scheme has its own essentials, and the union of what is still
     * missing across all of them drives the question. Fields that cannot apply
     * given the known education stage are skipped, exactly as in discovery, so
     * a school student is never asked for a college year and vice versa.
     *
     * <p>Only the first {@link #MAX_FIELDS_PER_TURN} fields are asked each turn.
     * The union of two schemes' essentials can reach ten fields, which is an
     * unanswerable question; asking in batches converges over a few turns and
     * each turn re-checks the profile before asking again.
     */
    private ValidationResult validateMultiScheme(
            StudentProfileDto profile,
            List<SchemeType> namedSchemes,
            String conversationId,
            boolean asQuestion) {

        if (namedSchemes == null || namedSchemes.isEmpty()) {
            return validateDiscovery(profile, conversationId, asQuestion);
        }

        List<SchemeType> schemes = List.copyOf(namedSchemes);

        List<StudentProfileField> missing =
                orderMissingFields(profile, missingFor(profile, schemes));

        if (missing.isEmpty()) {
            pendingState.clear(conversationId);
            return ValidationResult.ready(ValidationMode.MULTI_SCHEME, null, schemes);
        }
        if (asQuestion) {
            return ValidationResult.ready(ValidationMode.MULTI_SCHEME, null, schemes);
        }

        List<StudentProfileField> asked = missing.subList(
                0, Math.min(missing.size(), MAX_FIELDS_PER_TURN));

        String question = turnQuestion(asked, conversationId);
        pendingState.store(conversationId, schemes, List.copyOf(asked));
        return ValidationResult.missing(
                ValidationMode.MULTI_SCHEME,
                null,
                schemes,
                asked,
                question,
                "These details are needed to compare " + schemeNames(schemes) + ".");
    }

    /**
     * Essential fields still absent from the profile, per named scheme. A field
     * that cannot apply at the known education stage is left out, so a college
     * student is never asked for their school class.
     */
    private List<List<StudentProfileField>> missingFor(
            StudentProfileDto profile,
            List<SchemeType> schemes) {

        List<List<StudentProfileField>> missingByScheme = new ArrayList<>();
        for (SchemeType scheme : schemes) {
            List<StudentProfileField> missing = schemeRegistry.definition(scheme)
                    .getEssentialFields().stream()
                    .filter(field -> isApplicable(profile, field))
                    .filter(field -> !profileFieldAccess.isPresent(profile, field))
                    .toList();
            missingByScheme.add(missing);
        }
        return missingByScheme;
    }

    /**
     * Flattens the per-scheme missing lists into the order questions should be
     * asked. Fields every named scheme needs come first, because one answer
     * then satisfies all of them; the rest follow the cross-scheme discovery
     * order and finally their scheme definition order, so the sequence is
     * stable across turns.
     */
    private List<StudentProfileField> orderMissingFields(
            StudentProfileDto profile,
            List<List<StudentProfileField>> missingByScheme) {

        LinkedHashSet<StudentProfileField> union = new LinkedHashSet<>();
        for (List<StudentProfileField> missing : missingByScheme) {
            union.addAll(missing);
        }
        if (union.size() <= MAX_FIELDS_PER_TURN) {
            return List.copyOf(union);
        }

        List<StudentProfileField> shared = union.stream()
                .filter(field -> missingByScheme.stream()
                        .allMatch(missing -> missing.contains(field)))
                .toList();

        LinkedHashSet<StudentProfileField> ordered = new LinkedHashSet<>(shared);
        // Only missing fields may be added here: the discovery order is a
        // ranking of candidates, not a list of fields to ask regardless.
        for (StudentProfileField field : schemeRegistry.discoveryOrder()) {
            if (union.contains(field)) {
                ordered.add(field);
            }
        }
        ordered.addAll(union);

        return List.copyOf(ordered);
    }

    private String schemeNames(List<SchemeType> schemes) {
        return schemes.stream()
                .map(SchemeType::name)
                .map(name -> name.replace('_', ' '))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private ValidationResult validateSpecific(
            StudentProfileDto profile,
            SchemeType targetScheme,
            String conversationId,
            boolean asQuestion) {

        if (targetScheme == null) {
            // No single scheme is in progress, so there is no checklist to
            // complete. Falling through avoids asking questions of an unknown
            // scheme definition.
            return validateDiscovery(profile, conversationId, asQuestion);
        }

        SchemeDefinition definition = schemeRegistry.definition(targetScheme);
        List<StudentProfileField> missing = definition.getEssentialFields().stream()
                .filter(field -> !profileFieldAccess.isPresent(profile, field))
                .toList();

        if (missing.isEmpty()) {
            pendingState.clear(conversationId);
            return ValidationResult.ready(
                    ValidationMode.SPECIFIC_SCHEME,
                    targetScheme,
                    List.of(targetScheme));
        }
        if (asQuestion) {
            return ValidationResult.ready(
                    ValidationMode.SPECIFIC_SCHEME,
                    targetScheme,
                    List.of(targetScheme));
        }

        String question = turnQuestion(missing, conversationId);
        pendingState.store(conversationId, targetScheme, missing);
        return ValidationResult.missing(
                ValidationMode.SPECIFIC_SCHEME,
                targetScheme,
                List.of(targetScheme),
                missing,
                question,
                "These fields are required before " + targetScheme + " can be evaluated.");
    }

    private ValidationResult validateDiscovery(
            StudentProfileDto profile,
            String conversationId,
            boolean asQuestion) {

        List<SchemeType> allSchemes = schemeRegistry.allSchemes();
        Optional<StudentProfileField> next = schemeRegistry.discoveryOrder().stream()
                .filter(field -> isApplicable(profile, field))
                .filter(field -> !profileFieldAccess.isPresent(profile, field))
                .findFirst();

        if (next.isEmpty()) {
            pendingState.clear(conversationId);
            return ValidationResult.ready(
                    ValidationMode.SCHOLARSHIP_DISCOVERY,
                    null,
                    allSchemes);
        }
        if (asQuestion) {
            return ValidationResult.ready(
                    ValidationMode.SCHOLARSHIP_DISCOVERY,
                    null,
                    allSchemes);
        }

        List<StudentProfileField> missing = List.of(next.get());
        String question = turnQuestion(missing, conversationId);
        // Discovery has no scheme to continue, but the asked field is recorded
        // so a repeated question is rephrased and a plain reply can be resolved
        // back onto the field that was asked.
        pendingState.store(conversationId, List.of(), missing);
        return ValidationResult.missing(
                ValidationMode.SCHOLARSHIP_DISCOVERY,
                null,
                allSchemes,
                missing,
                question,
                "This information helps determine which scholarships may suit you.");
    }

    /**
     * If the same fields were already asked in the previous turn and the value
     * still was not captured, rephrase the question so the student knows to
     * reply with a plain value instead of seeing the identical question again.
     */
    private String turnQuestion(List<StudentProfileField> asked, String conversationId) {
        List<StudentProfileField> lastAsked = pendingState.askedFields(conversationId);
        if (!lastAsked.isEmpty() && lastAsked.equals(asked)) {
            return questionBuilder.buildRephrase(asked);
        }
        return questionBuilder.buildQuestion(asked);
    }

    /**
     * Structural applicability, not eligibility. A field that cannot apply
     * given the known education stage is skipped during discovery.
     */
    private boolean isApplicable(StudentProfileDto profile, StudentProfileField field) {
        String educationLevel = profile.getEducationLevel();
        if (field == StudentProfileField.CURRENT_CLASS && isCollegeLevel(educationLevel)) {
            return false;
        }
        if (field == StudentProfileField.CURRENT_YEAR && isSchoolLevel(educationLevel)) {
            return false;
        }
        return true;
    }

    static boolean isCollegeLevel(String educationLevel) {
        if (educationLevel == null || educationLevel.isBlank()) {
            return false;
        }
        String level = educationLevel.toLowerCase(Locale.ROOT);
        return level.contains("ug")
                || level.contains("pg")
                || level.contains("undergraduate")
                || level.contains("postgraduate")
                || level.contains("graduate")
                || level.contains("bachelor")
                || level.contains("master")
                || level.contains("b.tech")
                || level.contains("m.tech")
                || level.contains("btech")
                || level.contains("mtech")
                || level.contains("bba")
                || level.contains("bca")
                || level.contains("bsc")
                || level.contains("msc")
                || level.contains("bcom")
                || level.contains("mcom")
                || level.contains("degree")
                || level.contains("diploma")
                || level.contains("polytechnic")
                || level.contains("college")
                || level.contains("university")
                || level.contains("doctorate")
                || level.contains("phd")
                || level.startsWith("b.")
                || level.startsWith("m.");
    }

    static boolean isSchoolLevel(String educationLevel) {
        if (educationLevel == null || educationLevel.isBlank()) {
            return false;
        }
        String level = educationLevel.toLowerCase(Locale.ROOT);
        return level.contains("class")
                || level.contains("9th")
                || level.contains("10th")
                || level.contains("11th")
                || level.contains("12th")
                || level.contains("secondary")
                || level.contains("school")
                || level.contains("ssc")
                || level.contains("hsc")
                || level.contains("matric")
                || level.contains("std")
                || level.contains("standard")
                || level.contains("intermediate")
                || level.contains("plus two")
                || level.contains("plus2")
                || level.contains("puc")
                || level.contains("+2")
                || level.contains("+1");
    }
}