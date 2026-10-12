package com.bharat.scholarship_rag_backend.eligibility;

import com.bharat.scholarship_rag_backend.dto.StudentProfileDto;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EligibilityEngineTest {

    private final EligibilityEngine engine =
            new EligibilityEngine(new SchemeEligibilityCatalog());

    private static BigDecimal lakh(String value) {
        return new BigDecimal(value);
    }

    @Test
    void pmUspCsssEligibleWhenIncomeYearAndModeFit() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("UG")
                .currentYear(1)
                .regularMode(true)
                .annualFamilyIncome(lakh("400000"))
                .course("B.Sc")
                .build();

        EligibilityAssessment assessment = engine.assess(profile, SchemeType.PM_USP_CSSS);

        assertEquals(EligibilityVerdict.ELIGIBLE, assessment.verdict());
    }

    @Test
    void pmUspCsssFailsWhenIncomeExceedsCeiling() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("UG")
                .currentYear(1)
                .regularMode(true)
                .annualFamilyIncome(lakh("500000"))
                .build();

        EligibilityAssessment assessment = engine.assess(profile, SchemeType.PM_USP_CSSS);

        assertEquals(EligibilityVerdict.NOT_ELIGIBLE, assessment.verdict());
        assertTrue(assessment.failedReasons().stream().anyMatch(r -> r.contains("Annual family income")));
    }

    @Test
    void ishanUdayEligibleForNorthEastDomicile() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("UG")
                .currentYear(1)
                .regularMode(true)
                .completedUGDegree(false)
                .domicileState("Assam")
                .annualFamilyIncome(lakh("300000"))
                .build();

        EligibilityAssessment assessment = engine.assess(profile, SchemeType.ISHAN_UDAY);

        assertEquals(EligibilityVerdict.ELIGIBLE, assessment.verdict());
    }

    @Test
    void ishanUdayFailsOutsideNorthEast() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("UG")
                .currentYear(1)
                .regularMode(true)
                .completedUGDegree(false)
                .domicileState("Karnataka")
                .annualFamilyIncome(lakh("300000"))
                .build();

        EligibilityAssessment assessment = engine.assess(profile, SchemeType.ISHAN_UDAY);

        assertEquals(EligibilityVerdict.NOT_ELIGIBLE, assessment.verdict());
    }

    @Test
    void ishanUdayIsPossibleWhenDomicileUnknown() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("UG")
                .currentYear(1)
                .regularMode(true)
                .completedUGDegree(false)
                .annualFamilyIncome(lakh("300000"))
                .build();

        EligibilityAssessment assessment = engine.assess(profile, SchemeType.ISHAN_UDAY);

        assertEquals(EligibilityVerdict.POSSIBLY_ELIGIBLE, assessment.verdict());
        assertTrue(assessment.missingDetails().contains("Domicile"));
    }

    @Test
    void yasasviMatchesObcSchoolClassInRange() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("class 10")
                .currentClass(10)
                .socialCategory("OBC")
                .annualFamilyIncome(lakh("200000"))
                .build();

        assertEquals(EligibilityVerdict.ELIGIBLE,
                engine.assess(profile, SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS).verdict());
    }

    @Test
    void yasasviFailsOutsideClassRange() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("class 8")
                .currentClass(8)
                .socialCategory("OBC")
                .annualFamilyIncome(lakh("200000"))
                .build();

        assertEquals(EligibilityVerdict.NOT_ELIGIBLE,
                engine.assess(profile, SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS).verdict());
    }

    @Test
    void yasasviFailsForGeneralCategory() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("class 10")
                .currentClass(10)
                .socialCategory("General")
                .annualFamilyIncome(lakh("200000"))
                .build();

        assertEquals(EligibilityVerdict.NOT_ELIGIBLE,
                engine.assess(profile, SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS).verdict());
    }

    @Test
    void topClassScAcceptsScheduledCasteSpelling() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("UG")
                .currentYear(1)
                .regularMode(true)
                .socialCategory("Scheduled Caste")
                .annualFamilyIncome(lakh("700000"))
                .build();

        assertEquals(EligibilityVerdict.ELIGIBLE,
                engine.assess(profile, SchemeType.TOP_CLASS_SC).verdict());
    }

    @Test
    void topClassPwdRequiresBenchmarkDisability() {
        StudentProfileDto good = StudentProfileDto.builder()
                .educationLevel("PG")
                .hasDisability(true)
                .disabilityPercentage(45)
                .annualFamilyIncome(lakh("700000"))
                .build();
        assertEquals(EligibilityVerdict.ELIGIBLE,
                engine.assess(good, SchemeType.TOP_CLASS_PWD).verdict());

        StudentProfileDto lowDisability = StudentProfileDto.builder()
                .educationLevel("PG")
                .hasDisability(true)
                .disabilityPercentage(30)
                .annualFamilyIncome(lakh("700000"))
                .build();
        assertEquals(EligibilityVerdict.NOT_ELIGIBLE,
                engine.assess(lowDisability, SchemeType.TOP_CLASS_PWD).verdict());

        StudentProfileDto noDisability = StudentProfileDto.builder()
                .educationLevel("PG")
                .hasDisability(false)
                .annualFamilyIncome(lakh("700000"))
                .build();
        assertEquals(EligibilityVerdict.NOT_ELIGIBLE,
                engine.assess(noDisability, SchemeType.TOP_CLASS_PWD).verdict());
    }

    @Test
    void discoveryReportRecommendsMatchingScheme() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("class 10")
                .currentClass(10)
                .socialCategory("OBC")
                .annualFamilyIncome(lakh("200000"))
                .build();

        EligibilityReport report = engine.assessAll(profile);

        assertTrue(report.recommended().contains(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS));
        assertTrue(report.hasAnyMatch());
    }
}
