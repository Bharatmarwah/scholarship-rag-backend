package com.bharat.scholarship_rag_backend.validator;

import com.bharat.scholarship_rag_backend.normalization.ProfileValueNormalizer;
import com.bharat.scholarship_rag_backend.scheme.StudentProfileField;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingAnswerResolverTest {

    private final PendingAnswerResolver resolver =
            new PendingAnswerResolver(new ProfileValueNormalizer());

    private Map<String, Object> resolve(StudentProfileField field, String text) {
        return resolver.resolve(List.of(field), text, Set.of());
    }

    @Test
    void resolvesCourseFromPlainReply() {
        assertEquals("B.Tech", resolve(StudentProfileField.COURSE, "btech").get("COURSE"));
        assertEquals("B.Tech", resolve(StudentProfileField.COURSE, "I am doing btech").get("COURSE"));
        assertEquals("Computer Science", resolve(StudentProfileField.COURSE, "Computer Science").get("COURSE"));
    }

    @Test
    void resolvesEnumFields() {
        assertEquals("OBC", resolve(StudentProfileField.SOCIAL_CATEGORY, "obc").get("SOCIAL_CATEGORY"));
        assertEquals("creamy layer", resolve(StudentProfileField.SOCIAL_CATEGORY, "creamy layer").get("SOCIAL_CATEGORY"));
        assertEquals("UG", resolve(StudentProfileField.EDUCATION_LEVEL, "undergraduate").get("EDUCATION_LEVEL"));
        assertEquals("CLASS_12", resolve(StudentProfileField.EDUCATION_LEVEL, "12th").get("EDUCATION_LEVEL"));
    }

    @Test
    void resolvesNumbers() {
        assertEquals(3, resolve(StudentProfileField.CURRENT_YEAR, "3rd year").get("CURRENT_YEAR"));
        assertEquals(10, resolve(StudentProfileField.CURRENT_CLASS, "class 10").get("CURRENT_CLASS"));
        assertEquals(92.0, resolve(StudentProfileField.CLASS12_PERCENTILE, "92%").get("CLASS12_PERCENTILE"));
        assertEquals(40, resolve(StudentProfileField.DISABILITY_PERCENTAGE, "40 percent").get("DISABILITY_PERCENTAGE"));
    }

    @Test
    void resolvesIncomeWithUnits() {
        assertEquals(0, income("no income").compareTo(BigDecimal.ZERO));
        assertEquals(0, income("5 lakh").compareTo(new BigDecimal("500000")));
        assertEquals(0, income("2.5 crore").compareTo(new BigDecimal("25000000")));
        assertEquals(0, income("200000").compareTo(new BigDecimal("200000")));
        assertEquals(0, income("5L").compareTo(new BigDecimal("500000")));
    }

    private BigDecimal income(String text) {
        return (BigDecimal) resolve(StudentProfileField.ANNUAL_FAMILY_INCOME, text)
                .get("ANNUAL_FAMILY_INCOME");
    }

    @Test
    void resolvesBooleans() {
        assertEquals(Boolean.TRUE, resolve(StudentProfileField.HAS_DISABILITY, "yes").get("HAS_DISABILITY"));
        assertEquals(Boolean.FALSE, resolve(StudentProfileField.HAS_DISABILITY, "no").get("HAS_DISABILITY"));
        assertEquals(Boolean.TRUE, resolve(StudentProfileField.REGULAR_MODE, "I do").get("REGULAR_MODE"));
    }

    @Test
    void resolvesShortTextFields() {
        assertEquals("Delhi", resolve(StudentProfileField.DOMICILE_STATE, "Delhi").get("DOMICILE_STATE"));
        assertEquals("CBSE", resolve(StudentProfileField.BOARD, "CBSE").get("BOARD"));
    }

    @Test
    void refusesToTreatAQuestionOrRequestAsAnAnswer() {
        assertTrue(resolve(StudentProfileField.COURSE, "which scholarships can I apply for?").isEmpty());
        assertTrue(resolve(StudentProfileField.CURRENT_YEAR, "what scholarships am I eligible for").isEmpty());
        assertFalse(resolve(StudentProfileField.SOCIAL_CATEGORY, "obc").isEmpty());
    }

    @Test
    void doesNotOverrideAValueTheLlmAlreadyExtracted() {
        Map<String, Object> result = resolver.resolve(
                List.of(StudentProfileField.COURSE),
                "btech",
                Set.of("COURSE"));
        assertTrue(result.isEmpty());
    }

    @Test
    void doesNotGuessAmbiguousNumbersAcrossMultipleAskedFields() {
        Map<String, Object> result = resolver.resolve(
                List.of(StudentProfileField.CURRENT_YEAR, StudentProfileField.ANNUAL_FAMILY_INCOME),
                "3",
                Set.of());
        assertTrue(result.isEmpty());
    }
}
