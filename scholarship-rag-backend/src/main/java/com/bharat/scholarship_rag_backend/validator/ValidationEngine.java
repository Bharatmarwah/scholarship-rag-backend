package com.bharat.scholarship_rag_backend.validator;

import com.bharat.scholarship_rag_backend.dto.StudentProfileDto;
import com.bharat.scholarship_rag_backend.scheme.ProfileFieldAccess;
import com.bharat.scholarship_rag_backend.scheme.SchemeDefinition;
import com.bharat.scholarship_rag_backend.scheme.SchemeRegistry;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import com.bharat.scholarship_rag_backend.scheme.StudentProfileField;
import org.springframework.stereotype.Component;

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
 * else pending scheme exists         → SPECIFIC_SCHEME (same intent continues)
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

    public ValidationResult validate(
            StudentProfileDto profile,
            String rawTargetScheme,
            String conversationId) {

        Optional<SchemeType> currentScheme = schemeRegistry.resolve(rawTargetScheme);

        ValidationMode mode;
        SchemeType targetScheme;

        if (currentScheme.isPresent()) {
            // A scheme explicitly named this turn takes priority and starts
            // (or overrides) this single intent.
            mode = ValidationMode.SPECIFIC_SCHEME;
            targetScheme = currentScheme.get();
            pendingState.clear(conversationId);
        } else if (pendingState.hasPending(conversationId)) {
            // No scheme named this turn but a specific scheme is in progress
            // for the same single intent (e.g. the user answered a question).
            mode = ValidationMode.SPECIFIC_SCHEME;
            targetScheme = pendingState.targetScheme(conversationId);
        } else {
            mode = ValidationMode.SCHOLARSHIP_DISCOVERY;
            targetScheme = null;
        }

        return switch (mode) {
            case SPECIFIC_SCHEME -> validateSpecific(profile, targetScheme, conversationId);
            case SCHOLARSHIP_DISCOVERY -> validateDiscovery(profile, conversationId);
        };
    }

    private ValidationResult validateSpecific(
            StudentProfileDto profile,
            SchemeType targetScheme,
            String conversationId) {

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

        pendingState.store(conversationId, targetScheme, missing);
        return ValidationResult.missing(
                ValidationMode.SPECIFIC_SCHEME,
                targetScheme,
                List.of(targetScheme),
                missing,
                questionBuilder.buildQuestion(missing),
                "These fields are required before " + targetScheme + " can be evaluated.");
    }

    private ValidationResult validateDiscovery(
            StudentProfileDto profile,
            String conversationId) {

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

        List<StudentProfileField> missing = List.of(next.get());
        return ValidationResult.missing(
                ValidationMode.SCHOLARSHIP_DISCOVERY,
                null,
                allSchemes,
                missing,
                questionBuilder.buildQuestion(missing),
                "This information helps determine which scholarships may suit you.");
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
                || level.contains("bba")
                || level.contains("bca")
                || level.contains("degree")
                || level.contains("diploma")
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
                || level.contains("std");
    }
}