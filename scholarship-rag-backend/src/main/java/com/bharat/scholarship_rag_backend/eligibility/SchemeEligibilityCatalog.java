package com.bharat.scholarship_rag_backend.eligibility;

import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The target candidate for each supported scheme, transcribed from the
 * official guideline/FAQ documents under
 * {@code src/main/resources/cleaned-markdown}. Keeping the rules here (rather
 * than in prompts or the RAG corpus) makes eligibility deterministic and
 * testable.
 */
@Component
public class SchemeEligibilityCatalog {

    /** North-Eastern Region states eligible for Ishan Uday. */
    private static final Set<String> NORTH_EAST_STATES = Set.of(
            "arunachal pradesh",
            "assam",
            "manipur",
            "meghalaya",
            "mizoram",
            "nagaland",
            "sikkim",
            "tripura");

    private static final Map<SchemeType, EligibilityCriteria> CRITERIA = buildCriteria();

    public EligibilityCriteria criteria(SchemeType scheme) {
        if (scheme == null) {
            throw new IllegalArgumentException("Unknown scheme: null");
        }
        EligibilityCriteria criteria = CRITERIA.get(scheme);
        if (criteria == null) {
            throw new IllegalArgumentException("No eligibility criteria for scheme: " + scheme);
        }
        return criteria;
    }

    public List<EligibilityCriteria> all() {
        return CRITERIA.values().stream().toList();
    }

    private static Map<SchemeType, EligibilityCriteria> buildCriteria() {
        Map<SchemeType, EligibilityCriteria> map = new LinkedHashMap<>();

        // PM-USP CSSS: family income up to 4.5 lakh, regular first-degree
        // graduation. Reservation is internal, not a student-facing condition.
        map.put(SchemeType.PM_USP_CSSS, new EligibilityCriteria(
                SchemeType.PM_USP_CSSS,
                Set.of(),
                new BigDecimal("450000"),
                Set.of(EducationStage.UNDERGRADUATE),
                null,
                null,
                Set.of(),
                false,
                null,
                true,
                true,
                false,
                "pm-usp-csss/faq.md:25,29; pm-usp-csss/guideline.md:33"));

        // Ishan Uday: North-Eastern domicile, parental income below 4.5 lakh,
        // first UG degree, regular/full-time only.
        map.put(SchemeType.ISHAN_UDAY, new EligibilityCriteria(
                SchemeType.ISHAN_UDAY,
                Set.of(),
                new BigDecimal("450000"),
                Set.of(EducationStage.UNDERGRADUATE),
                null,
                null,
                NORTH_EAST_STATES,
                false,
                null,
                true,
                true,
                true,
                "ishan-uday/guideline.md:41-55; ishan-uday/faq.md:7,29"));

        // PM-YASASVI Top Class Schools: OBC/EBC/DNT students in classes IX-XII,
        // household income under 2.5 lakh.
        map.put(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS, new EligibilityCriteria(
                SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS,
                Set.of("OBC", "EBC", "DNT"),
                new BigDecimal("250000"),
                Set.of(EducationStage.SCHOOL),
                9,
                12,
                Set.of(),
                false,
                null,
                false,
                false,
                false,
                "pm-yasasvi-top-class-schools/guideline.md:11"));

        // Top Class SC: SC students, family income up to 8 lakh, regular
        // first-year study in a notified institution.
        map.put(SchemeType.TOP_CLASS_SC, new EligibilityCriteria(
                SchemeType.TOP_CLASS_SC,
                Set.of("SC"),
                new BigDecimal("800000"),
                Set.of(EducationStage.UNDERGRADUATE, EducationStage.POSTGRADUATE),
                null,
                null,
                Set.of(),
                false,
                null,
                true,
                true,
                false,
                "top-class-sc/guideline.md:15,19"));

        // Top Class Education for Students with Disabilities: benchmark
        // disability (40%+), family income up to 8 lakh, PG-level study.
        map.put(SchemeType.TOP_CLASS_PWD, new EligibilityCriteria(
                SchemeType.TOP_CLASS_PWD,
                Set.of(),
                new BigDecimal("800000"),
                Set.of(EducationStage.POSTGRADUATE),
                null,
                null,
                Set.of(),
                true,
                40,
                false,
                false,
                false,
                "top-class-pwd/faq.md:33-39"));

        return Collections.unmodifiableMap(map);
    }
}
