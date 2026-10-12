package com.bharat.scholarship_rag_backend.eligibility;

import com.bharat.scholarship_rag_backend.scheme.SchemeType;

import java.math.BigDecimal;
import java.util.Set;

/**
 * The "target candidate" profile for one scheme: the concrete eligibility
 * rules transcribed from that scheme's official documents.
 *
 * <p>This is deliberately separate from
 * {@code com.bharat.scholarship_rag_backend.scheme.SchemeDefinition}, which is
 * field-only (which facts to collect). This record holds the actual thresholds
 * the validator is forbidden to encode.
 *
 * <p>An empty collection or a {@code null} numeric means "no constraint on
 * this axis", so a criterion that a scheme does not care about is simply not
 * evaluated.
 *
 * @param scheme                   the scheme these rules belong to
 * @param eligibleSocialCategories allowed categories; empty = any
 * @param incomeCeiling            max annual family income in rupees; null = none
 * @param stages                   targeted education stages; empty = any
 * @param minClass                 lowest eligible school class; null = n/a
 * @param maxClass                 highest eligible school class; null = n/a
 * @param eligibleDomicileStates   required domicile states/regions; empty = any
 * @param disabilityRequired       disability is a condition of eligibility
 * @param minDisabilityPercentage  minimum benchmark disability percentage; null = none
 * @param firstYearOnly            only first-year entrants are eligible
 * @param regularModeRequired      only regular/full-time study is eligible
 * @param priorDegreeDisallowed    a prior degree makes the candidate ineligible
 * @param source                   the document the rules were transcribed from
 */
public record EligibilityCriteria(
        SchemeType scheme,
        Set<String> eligibleSocialCategories,
        BigDecimal incomeCeiling,
        Set<EducationStage> stages,
        Integer minClass,
        Integer maxClass,
        Set<String> eligibleDomicileStates,
        boolean disabilityRequired,
        Integer minDisabilityPercentage,
        boolean firstYearOnly,
        boolean regularModeRequired,
        boolean priorDegreeDisallowed,
        String source) {
}
