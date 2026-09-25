package com.bharat.scholarship_rag_backend.scheme;

/**
 * Full student profile taxonomy. Values used by the scheme definitions,
 * the query composer, and the profile enrichment step.
 *
 * <p>{@code STUDY_LEVEL}, {@code COURSE_TYPE}, {@code APPLICATION_TYPE},
 * {@code ADMISSION_RANK} and {@code HAS_UDID_OR_UDID_ENROLLMENT} are
 * scheme-relevant fields that do not map to the profile entity yet.
 */
public enum StudentProfileField {

    // Education
    EDUCATION_LEVEL,
    COURSE,
    COURSE_TYPE,
    CURRENT_YEAR,
    CURRENT_CLASS,
    REGULAR_MODE,
    COMPLETED_UG_DEGREE,
    STUDY_LEVEL,
    CLASS12_PERCENTILE,
    BOARD,
    PREVIOUS_CLASS_MARKS,
    APPLICATION_TYPE,
    ADMISSION_RANK,

    // Financial
    ANNUAL_FAMILY_INCOME,

    // Social
    SOCIAL_CATEGORY,
    DOMICILE_STATE,

    // Institution
    INSTITUTION_NAME,
    INSTITUTION_TYPE,

    // Existing scholarship
    RECEIVING_OTHER_SCHOLARSHIP,

    // Disability
    HAS_DISABILITY,
    DISABILITY_PERCENTAGE,
    HAS_VALID_DISABILITY_CERTIFICATE,
    HAS_UDID_OR_UDID_ENROLLMENT,

    NATIONALITY,
    SIBLINGS_RECEIVING_BENEFIT
}