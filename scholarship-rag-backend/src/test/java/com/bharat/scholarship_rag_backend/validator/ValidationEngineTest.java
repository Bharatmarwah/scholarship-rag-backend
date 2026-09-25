package com.bharat.scholarship_rag_backend.validator;

import com.bharat.scholarship_rag_backend.dto.StudentProfileDto;
import com.bharat.scholarship_rag_backend.scheme.ProfileFieldAccess;
import com.bharat.scholarship_rag_backend.scheme.SchemeRegistry;
import com.bharat.scholarship_rag_backend.scheme.SchemeType;
import com.bharat.scholarship_rag_backend.scheme.StudentProfileField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationEngineTest {

    private ValidationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ValidationEngine(
                new SchemeRegistry(),
                new ProfileFieldAccess(),
                new QuestionBuilder(),
                new PendingValidationState());
    }

    private StudentProfileDto pmUspComplete() {
        return StudentProfileDto.builder()
                .course("BCA")
                .currentYear(1)
                .class12Percentile(92.0)
                .board("CBSE")
                .annualFamilyIncome(new BigDecimal("300000"))
                .regularMode(true)
                .institutionName("Delhi University")
                .build();
    }

    // 1. Specific PM-USP with missing fields
    @Test
    void specificPmUspWithMissingFields() {
        StudentProfileDto profile = pmUspComplete();
        profile.setClass12Percentile(null);
        profile.setInstitutionName(null);

        ValidationResult result = engine.validate(profile, "PM-USP", "c1");

        assertEquals(ValidationMode.SPECIFIC_SCHEME, result.getMode());
        assertEquals(ValidationStatus.MISSING_INFORMATION, result.getStatus());
        assertEquals(SchemeType.PM_USP_CSSS, result.getTargetScheme());
        assertEquals(List.of(StudentProfileField.CLASS12_PERCENTILE,
                StudentProfileField.INSTITUTION_NAME), result.getMissingFields());
        assertTrue(result.getQuestion().contains("Class 12 percentile"));
        assertTrue(result.getQuestion().contains("institution"));
    }

    // 2. Specific PM-USP with all essential fields
    @Test
    void specificPmUspAllEssentialPresent() {
        ValidationResult result = engine.validate(pmUspComplete(), "PM-USP", "c1");
        assertEquals(ValidationStatus.READY, result.getStatus());
        assertTrue(result.getMissingFields().isEmpty());
        assertNull(result.getQuestion());
        assertNull(result.getReason());
    }

    // 3. Specific Ishan Uday with missing domicile
    @Test
    void specificIshanUdayMissingDomicile() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("UG")
                .course("BCA")
                .currentYear(1)
                .annualFamilyIncome(new BigDecimal("300000"))
                .regularMode(true)
                .completedUGDegree(false)
                .receivingOtherScholarship(false)
                .build();

        ValidationResult result = engine.validate(profile, "Ishan Uday", "c1");

        assertEquals(ValidationStatus.MISSING_INFORMATION, result.getStatus());
        assertEquals(List.of(StudentProfileField.DOMICILE_STATE), result.getMissingFields());
    }

    // 4. Specific Ishan Uday with domicile present
    @Test
    void specificIshanUdayDomicilePresent() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("UG")
                .course("BCA")
                .currentYear(1)
                .domicileState("Assam")
                .annualFamilyIncome(new BigDecimal("300000"))
                .regularMode(true)
                .completedUGDegree(false)
                .receivingOtherScholarship(false)
                .build();

        ValidationResult result = engine.validate(profile, "Ishan Uday", "c1");
        assertEquals(ValidationStatus.READY, result.getStatus());
    }

    // 5. PM YASASVI uses CURRENT_CLASS
    @Test
    void yasasviUsesCurrentClass() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .currentClass(11)
                .socialCategory("OBC")
                .annualFamilyIncome(new BigDecimal("200000"))
                .institutionName("Top Class School")
                .build();

        ValidationResult result = engine.validate(profile, "PM YASASVI", "c1");
        assertEquals(ValidationStatus.READY, result.getStatus());
    }

    // 6. Top Class SC checks social category and institution
    @Test
    void topClassScChecksCategoryAndInstitution() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("UG")
                .course("Engineering")
                .currentYear(1)
                .annualFamilyIncome(new BigDecimal("500000"))
                .regularMode(true)
                .build();

        ValidationResult result = engine.validate(profile, "Top Class SC", "c1");
        assertEquals(ValidationStatus.MISSING_INFORMATION, result.getStatus());
        assertEquals(List.of(
                StudentProfileField.SOCIAL_CATEGORY,
                StudentProfileField.INSTITUTION_NAME), result.getMissingFields());
    }

    // 7. Top Class PwD checks disability-related fields
    @Test
    void topClassPwdChecksDisabilityFields() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .nationality("Indian")
                .educationLevel("PG")
                .course("M.Tech")
                .annualFamilyIncome(new BigDecimal("500000"))
                .regularMode(true)
                .institutionName("IIT Delhi")
                .build();

        ValidationResult result = engine.validate(profile, "Top Class PWD", "c1");
        assertEquals(ValidationStatus.MISSING_INFORMATION, result.getStatus());
        assertTrue(result.getMissingFields().contains(StudentProfileField.HAS_DISABILITY));
        assertTrue(result.getMissingFields().contains(StudentProfileField.DISABILITY_PERCENTAGE));
        // Not asked yet: these are conditional verification info only.
        assertFalse(result.getMissingFields().contains(StudentProfileField.HAS_VALID_DISABILITY_CERTIFICATE));
        assertFalse(result.getMissingFields().contains(StudentProfileField.HAS_UDID_OR_UDID_ENROLLMENT));
    }

    // 8. Discovery mode asks the smallest set, not all fields of all schemes
    @Test
    void discoveryDoesNotAskAllFields() {
        StudentProfileDto profile = StudentProfileDto.builder().build();

        ValidationResult result = engine.validate(profile, null, "c1");

        assertEquals(ValidationMode.SCHOLARSHIP_DISCOVERY, result.getMode());
        assertEquals(1, result.getMissingFields().size());
        assertEquals(StudentProfileField.EDUCATION_LEVEL, result.getMissingFields().get(0));
        // must not ask everything from every scheme, e.g. it must not list
        // both a year and percentile and institution and domicile in one go
        assertTrue(result.getMissingFields().size() < 5);
    }

    // 9. Already-known profile fields are never requested again
    @Test
    void knownFieldsNeverRequestedAgain() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .course("BCA")
                .currentYear(1)
                .class12Percentile(92.0)
                .annualFamilyIncome(new BigDecimal("300000"))
                .regularMode(true)
                .institutionName("Delhi University")
                .build();

        ValidationResult first = engine.validate(profile, "PM-USP", "c1");
        assertTrue(first.getMissingFields().contains(StudentProfileField.BOARD));

        profile.setBoard("CBSE");
        ValidationResult second = engine.validate(profile, null, "c1");
        assertEquals(ValidationStatus.READY, second.getStatus());
        assertTrue(second.getMissingFields().isEmpty());
    }

    // 10. null means missing
    @Test
    void nullMeansMissing() {
        StudentProfileDto profile = pmUspComplete();
        profile.setCourse(null);

        ValidationResult result = engine.validate(profile, "PM-USP", "c1");
        assertTrue(result.getMissingFields().contains(StudentProfileField.COURSE));
    }

    // 11. Boolean false means present
    @Test
    void falseBooleanMeansPresent() {
        StudentProfileDto profile = pmUspComplete();
        profile.setRegularMode(false);

        ValidationResult result = engine.validate(profile, "PM-USP", "c1");
        assertFalse(result.getMissingFields().contains(StudentProfileField.REGULAR_MODE));
    }

    // 12. zero numeric means present
    @Test
    void zeroNumericMeansPresent() {
        StudentProfileDto profile = pmUspComplete();
        profile.setClass12Percentile(0.0);
        profile.setAnnualFamilyIncome(BigDecimal.ZERO);

        ValidationResult result = engine.validate(profile, "PM-USP", "c1");
        assertFalse(result.getMissingFields().contains(StudentProfileField.CLASS12_PERCENTILE));
        assertFalse(result.getMissingFields().contains(StudentProfileField.ANNUAL_FAMILY_INCOME));
    }

    // 13. blank String means missing
    @Test
    void blankStringMeansMissing() {
        StudentProfileDto profile = pmUspComplete();
        profile.setInstitutionName("   ");

        ValidationResult result = engine.validate(profile, "PM-USP", "c1");
        assertTrue(result.getMissingFields().contains(StudentProfileField.INSTITUTION_NAME));
    }

    // 14. unsupported scheme is not silently mapped
    @Test
    void unsupportedSchemeNotMapped() {
        StudentProfileDto profile = pmUspComplete();

        ValidationResult result = engine.validate(profile, "Random Scholarship", "c1");

        assertNotEquals(SchemeType.PM_USP_CSSS, result.getTargetScheme());
        assertEquals(ValidationMode.SCHOLARSHIP_DISCOVERY, result.getMode());
    }

    // 15. specific scheme checks only target scheme
    @Test
    void specificChecksOnlyTargetScheme() {
        StudentProfileDto profile = pmUspComplete();
        // Only PM-USP essentials present; none of the other four schemes
        // would be READY, but we must not check them.
        ValidationResult result = engine.validate(profile, "PM-USP", "c1");
        assertEquals(SchemeType.PM_USP_CSSS, result.getTargetScheme());
        assertEquals(1, result.getTargetSchemes().size());
        assertEquals(SchemeType.PM_USP_CSSS, result.getTargetSchemes().get(0));
    }

    // 16. discovery considers multiple schemes
    @Test
    void discoveryConsidersMultipleSchemes() {
        StudentProfileDto profile = StudentProfileDto.builder().build();

        ValidationResult result = engine.validate(profile, null, "c1");

        assertEquals(ValidationMode.SCHOLARSHIP_DISCOVERY, result.getMode());
        assertEquals(5, result.getTargetSchemes().size());
    }

    // 17. malformed LLM output (unparseable scheme) does not cause READY
    @Test
    void malformedSchemeDoesNotCauseReady() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .currentClass(11)
                .socialCategory("OBC")
                .annualFamilyIncome(new BigDecimal("200000"))
                .build();

        ValidationResult result = engine.validate(profile, "###", "c1");
        // Not READY for any specific scheme; stays in MISSING_INFORMATION
        // discovery until the discovery layer is complete.
        assertEquals(ValidationStatus.MISSING_INFORMATION, result.getStatus());
    }

    // 18. validator never makes an eligibility decision
    @Test
    void validatorNeverDecidesEligibility() {
        StudentProfileDto empty = StudentProfileDto.builder().build();
        StudentProfileDto full = pmUspComplete();

        for (ValidationResult result : List.of(
                engine.validate(empty, "PM-USP", "c1"),
                engine.validate(full, "PM-USP", "c1"),
                engine.validate(empty, null, "c1"))) {
            assertTrue(result.getStatus() == ValidationStatus.READY
                    || result.getStatus() == ValidationStatus.MISSING_INFORMATION);
        }
    }

    // 19. fresh/renewal conditional fields are not requested unnecessarily
    @Test
    void renewalConditionalFieldsNotRequested() {
        StudentProfileDto profile = pmUspComplete();
        // APPLICATION_TYPE and RECEIVING_OTHER_SCHOLARSHIP are conditional
        // (fresh vs renewal) and must not block validation.
        ValidationResult result = engine.validate(profile, "PM-USP", "c1");
        assertEquals(ValidationStatus.READY, result.getStatus());
        assertEquals(0, result.getMissingFields().size());
    }

    // 20. CURRENT_CLASS is not confused with CURRENT_YEAR
    @Test
    void currentClassNotConfusedWithCurrentYear() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .currentYear(2)
                .socialCategory("OBC")
                .annualFamilyIncome(new BigDecimal("200000"))
                .institutionName("Top Class School")
                .build();

        ValidationResult result = engine.validate(profile, "PM YASASVI", "c1");

        // Providing year must NOT satisfy YASASVI's class requirement.
        assertTrue(result.getMissingFields().contains(StudentProfileField.CURRENT_CLASS));
    }

    // Continuation: pending scheme drives the next turn (same single intent)
    @Test
    void pendingSchemeContinuesSameIntent() {
        StudentProfileDto partial = pmUspComplete();
        partial.setClass12Percentile(null);
        partial.setInstitutionName(null);

        ValidationResult first = engine.validate(partial, "PM-USP", "c1");
        assertEquals(2, first.getMissingFields().size());

        StudentProfileDto answered = pmUspComplete();
        answered.setClass12Percentile(92.0);
        // institution still missing, and the user did NOT name a scheme this turn
        answered.setInstitutionName(null);

        ValidationResult second = engine.validate(answered, null, "c1");
        assertEquals(ValidationMode.SPECIFIC_SCHEME, second.getMode());
        assertEquals(SchemeType.PM_USP_CSSS, second.getTargetScheme());
        assertEquals(List.of(StudentProfileField.INSTITUTION_NAME), second.getMissingFields());

        StudentProfileDto done = pmUspComplete();
        ValidationResult third = engine.validate(done, null, "c1");
        assertEquals(ValidationStatus.READY, third.getStatus());
    }

    // New explicit scheme overrides pending scheme
    @Test
    void newSchemeOverridesPending() {
        StudentProfileDto partial = pmUspComplete();
        partial.setClass12Percentile(null);

        engine.validate(partial, "PM-USP", "c1");

        StudentProfileDto other = StudentProfileDto.builder()
                .course("BCA")
                .currentYear(1)
                .annualFamilyIncome(new BigDecimal("300000"))
                .regularMode(true)
                .completedUGDegree(false)
                .receivingOtherScholarship(false)
                .build();

        ValidationResult result = engine.validate(other, "Ishan Uday", "c1");
        assertEquals(SchemeType.ISHAN_UDAY, result.getTargetScheme());
        assertTrue(result.getMissingFields().contains(StudentProfileField.DOMICILE_STATE));
    }

    // Discovery advances one step at a time
    @Test
    void discoveryAdvancesStepwise() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("UG")
                .build();

        ValidationResult result = engine.validate(profile, null, "c1");
        assertEquals(1, result.getMissingFields().size());
        assertEquals(StudentProfileField.CURRENT_YEAR, result.getMissingFields().get(0));

        profile.setCurrentYear(1);
        ValidationResult next = engine.validate(profile, null, "c1");
        // CURRENT_CLASS is not applicable for a college student, so discovery
        // moves past it to the next screening attribute.
        assertEquals(StudentProfileField.SOCIAL_CATEGORY, next.getMissingFields().get(0));
    }

    // Applicability skip: a college student is never asked for CURRENT_CLASS
    @Test
    void collegeStudentNotAskedForCurrentClass() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("UG")
                .currentYear(1)
                .socialCategory("OBC")
                .annualFamilyIncome(new BigDecimal("300000"))
                .course("BCA")
                .build();

        for (int i = 0; i < 5; i++) {
            ValidationResult result = engine.validate(profile, null, "c1");
            if (result.getStatus() == ValidationStatus.READY) {
                return;
            }
            assertNotEquals(StudentProfileField.CURRENT_CLASS, result.getMissingFields().get(0));
            assertFalse(result.getMissingFields().contains(StudentProfileField.CURRENT_CLASS));
            String given = result.getMissingFields().get(0).toString().toLowerCase();
            switch (given) {
                case "domicile_state" -> profile.setDomicileState("Maharashtra");
                case "has_disability" -> profile.setHasDisability(false);
                default -> throw new AssertionError("unexpected discovery field " + given);
            }
        }
    }

    // Applicability skip: a school student is never asked for CURRENT_YEAR
    @Test
    void schoolStudentNotAskedForCurrentYear() {
        StudentProfileDto profile = StudentProfileDto.builder()
                .educationLevel("Class 12")
                .currentClass(12)
                .socialCategory("OBC")
                .annualFamilyIncome(new BigDecimal("300000"))
                .institutionName("Govt Senior Secondary School")
                .build();

        for (int i = 0; i < 5; i++) {
            ValidationResult result = engine.validate(profile, null, "c1");
            if (result.getStatus() == ValidationStatus.READY) {
                return;
            }
            assertNotEquals(StudentProfileField.CURRENT_YEAR, result.getMissingFields().get(0));
            assertFalse(result.getMissingFields().contains(StudentProfileField.CURRENT_YEAR));
            String given = result.getMissingFields().get(0).toString().toLowerCase();
            switch (given) {
                case "course" -> profile.setCourse("PCM");
                case "domicile_state" -> profile.setDomicileState("Delhi");
                case "has_disability" -> profile.setHasDisability(false);
                default -> throw new AssertionError("unexpected discovery field " + given);
            }
        }
    }
}