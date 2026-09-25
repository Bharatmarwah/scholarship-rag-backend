package com.bharat.scholarship_rag_backend.scheme;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central catalog of the exactly five supported scholarship schemes and the
 * deterministic resolution of a raw (possibly LLM-produced) scheme name.
 *
 * <p>Definitions come from the supplied official scheme documents. No numeric
 * eligibility thresholds are stored here.
 */
@Component
public class SchemeRegistry {

    private static final Map<SchemeType, SchemeDefinition> DEFINITIONS = buildDefinitions();

    private static final Map<String, SchemeType> ALIASES = buildAliases();

    private static final List<SchemeType> ALL = List.of(
            SchemeType.PM_USP_CSSS,
            SchemeType.ISHAN_UDAY,
            SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS,
            SchemeType.TOP_CLASS_SC,
            SchemeType.TOP_CLASS_PWD
    );

    /**
     * Cross-scheme screening profile: the fields collected progressively
     * during discovery, in conversational order. This is NOT a set of fields
     * essential for every scheme. Each {@link SchemeDefinition} decides which
     * of these (and its own additional fields) actually matter. Year/class
     * are mutually exclusive by education stage and are handled by the
     * validation engine's applicability skip.
     */
    private static final List<StudentProfileField> DISCOVERY_ORDER = List.of(
            StudentProfileField.EDUCATION_LEVEL,
            StudentProfileField.CURRENT_YEAR,
            StudentProfileField.CURRENT_CLASS,
            StudentProfileField.SOCIAL_CATEGORY,
            StudentProfileField.ANNUAL_FAMILY_INCOME,
            StudentProfileField.COURSE,
            StudentProfileField.DOMICILE_STATE,
            StudentProfileField.HAS_DISABILITY
    );

    public List<StudentProfileField> discoveryOrder() {
        return DISCOVERY_ORDER;
    }

    public Optional<SchemeDefinition> find(SchemeType scheme) {
        return scheme == null ? Optional.empty() : Optional.ofNullable(DEFINITIONS.get(scheme));
    }

    public SchemeDefinition definition(SchemeType scheme) {
        if (scheme == null) {
            throw new IllegalArgumentException("Unknown scheme: null");
        }
        SchemeDefinition definition = DEFINITIONS.get(scheme);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown scheme: " + scheme);
        }
        return definition;
    }

    public List<SchemeType> allSchemes() {
        return ALL;
    }

    /**
     * Normalizes the raw scheme reference and maps it to a known scheme.
     * Returns empty for unknown or unsupported references; it never guesses.
     */
    public Optional<SchemeType> resolve(String rawScheme) {
        if (rawScheme == null || rawScheme.isBlank()) {
            return Optional.empty();
        }
        SchemeType matched = ALIASES.get(normalize(rawScheme));
        return matched == null ? Optional.empty() : Optional.of(matched);
    }

    private static String normalize(String raw) {
        return raw.toLowerCase()
                .replaceAll("[^a-z0-9]+", "")
                .trim();
    }

    /**
     * Order-preserving set so that essential/discovery field ordering is
     * stable and validator missingFields lists are deterministic.
     */
    private static Set<StudentProfileField> ordered(StudentProfileField... fields) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(fields)));
    }

    private static Map<String, SchemeType> buildAliases() {
        return Map.ofEntries(
                Map.entry("pmusp", SchemeType.PM_USP_CSSS),
                Map.entry("pmuspcsss", SchemeType.PM_USP_CSSS),
                Map.entry("postmatric", SchemeType.PM_USP_CSSS),
                Map.entry("postmatricscholarship", SchemeType.PM_USP_CSSS),
                Map.entry("csss", SchemeType.PM_USP_CSSS),
                Map.entry("ishanuday", SchemeType.ISHAN_UDAY),
                Map.entry("ishan", SchemeType.ISHAN_UDAY),
                Map.entry("pmyasasvi", SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS),
                Map.entry("yasasvi", SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS),
                Map.entry("topclassschools", SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS),
                Map.entry("topclasssc", SchemeType.TOP_CLASS_SC),
                Map.entry("topclasseducationforstudents", SchemeType.TOP_CLASS_SC),
                Map.entry("topclasspwd", SchemeType.TOP_CLASS_PWD),
                Map.entry("topclassdisabilities", SchemeType.TOP_CLASS_PWD),
                Map.entry("topclasstudentswithdisabilities", SchemeType.TOP_CLASS_PWD)
        );
    }

    private static Map<SchemeType, SchemeDefinition> buildDefinitions() {
        return Map.ofEntries(
                Map.entry(SchemeType.PM_USP_CSSS, new SchemeDefinition(
                        SchemeType.PM_USP_CSSS,
                        ordered(StudentProfileField.EDUCATION_LEVEL,
                                StudentProfileField.COURSE,
                                StudentProfileField.CURRENT_YEAR,
                                StudentProfileField.ANNUAL_FAMILY_INCOME),
                        ordered(StudentProfileField.COURSE,
                                StudentProfileField.CURRENT_YEAR,
                                StudentProfileField.CLASS12_PERCENTILE,
                                StudentProfileField.BOARD,
                                StudentProfileField.ANNUAL_FAMILY_INCOME,
                                StudentProfileField.REGULAR_MODE,
                                StudentProfileField.INSTITUTION_NAME),
                        ordered(StudentProfileField.APPLICATION_TYPE,
                                StudentProfileField.RECEIVING_OTHER_SCHOLARSHIP),
                        ordered()
                )),
                Map.entry(SchemeType.ISHAN_UDAY, new SchemeDefinition(
                        SchemeType.ISHAN_UDAY,
                        ordered(StudentProfileField.EDUCATION_LEVEL,
                                StudentProfileField.CURRENT_YEAR,
                                StudentProfileField.DOMICILE_STATE,
                                StudentProfileField.ANNUAL_FAMILY_INCOME,
                                StudentProfileField.COURSE),
                        ordered(StudentProfileField.EDUCATION_LEVEL,
                                StudentProfileField.COURSE,
                                StudentProfileField.CURRENT_YEAR,
                                StudentProfileField.DOMICILE_STATE,
                                StudentProfileField.ANNUAL_FAMILY_INCOME,
                                StudentProfileField.REGULAR_MODE,
                                StudentProfileField.COMPLETED_UG_DEGREE,
                                StudentProfileField.RECEIVING_OTHER_SCHOLARSHIP),
                        ordered(StudentProfileField.COURSE_TYPE),
                        ordered()
                )),
                Map.entry(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS, new SchemeDefinition(
                        SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS,
                        ordered(StudentProfileField.CURRENT_CLASS,
                                StudentProfileField.SOCIAL_CATEGORY,
                                StudentProfileField.ANNUAL_FAMILY_INCOME),
                        ordered(StudentProfileField.CURRENT_CLASS,
                                StudentProfileField.SOCIAL_CATEGORY,
                                StudentProfileField.ANNUAL_FAMILY_INCOME,
                                StudentProfileField.INSTITUTION_NAME),
                        ordered(StudentProfileField.INSTITUTION_TYPE,
                                StudentProfileField.PREVIOUS_CLASS_MARKS),
                        ordered(StudentProfileField.PREVIOUS_CLASS_MARKS)
                )),
                Map.entry(SchemeType.TOP_CLASS_SC, new SchemeDefinition(
                        SchemeType.TOP_CLASS_SC,
                        ordered(StudentProfileField.EDUCATION_LEVEL,
                                StudentProfileField.CURRENT_YEAR,
                                StudentProfileField.SOCIAL_CATEGORY,
                                StudentProfileField.ANNUAL_FAMILY_INCOME),
                        ordered(StudentProfileField.EDUCATION_LEVEL,
                                StudentProfileField.COURSE,
                                StudentProfileField.CURRENT_YEAR,
                                StudentProfileField.SOCIAL_CATEGORY,
                                StudentProfileField.ANNUAL_FAMILY_INCOME,
                                StudentProfileField.REGULAR_MODE,
                                StudentProfileField.INSTITUTION_NAME),
                        ordered(StudentProfileField.APPLICATION_TYPE,
                                StudentProfileField.RECEIVING_OTHER_SCHOLARSHIP,
                                StudentProfileField.SIBLINGS_RECEIVING_BENEFIT),
                        ordered(StudentProfileField.ADMISSION_RANK)
                )),
                Map.entry(SchemeType.TOP_CLASS_PWD, new SchemeDefinition(
                        SchemeType.TOP_CLASS_PWD,
                        ordered(StudentProfileField.EDUCATION_LEVEL,
                                StudentProfileField.COURSE,
                                StudentProfileField.ANNUAL_FAMILY_INCOME,
                                StudentProfileField.HAS_DISABILITY),
                        ordered(StudentProfileField.NATIONALITY,
                                StudentProfileField.EDUCATION_LEVEL,
                                StudentProfileField.COURSE,
                                StudentProfileField.ANNUAL_FAMILY_INCOME,
                                StudentProfileField.HAS_DISABILITY,
                                StudentProfileField.DISABILITY_PERCENTAGE,
                                StudentProfileField.REGULAR_MODE,
                                StudentProfileField.INSTITUTION_NAME),
                        ordered(StudentProfileField.HAS_VALID_DISABILITY_CERTIFICATE,
                                StudentProfileField.HAS_UDID_OR_UDID_ENROLLMENT,
                                StudentProfileField.COURSE_TYPE,
                                StudentProfileField.RECEIVING_OTHER_SCHOLARSHIP),
                        ordered(StudentProfileField.DISABILITY_PERCENTAGE)
                ))
        );
    }

    public List<String> normalizedAliasKeys() {
        return ALIASES.keySet().stream().sorted().collect(Collectors.toList());
    }
}