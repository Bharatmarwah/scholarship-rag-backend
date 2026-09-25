package com.bharat.scholarship_rag_backend.scheme;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemeDefinitionTest {

    private final SchemeRegistry registry = new SchemeRegistry();

    @Test
    void exactlyFiveSchemesAreConfigured() {
        assertEquals(5, registry.allSchemes().size());
        assertEquals(Set.of(
                        SchemeType.PM_USP_CSSS,
                        SchemeType.ISHAN_UDAY,
                        SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS,
                        SchemeType.TOP_CLASS_SC,
                        SchemeType.TOP_CLASS_PWD),
                Set.copyOf(registry.allSchemes()));
    }

    @Test
    void pmUspDiscoveryFields() {
        SchemeDefinition def = registry.definition(SchemeType.PM_USP_CSSS);
        assertEquals(Set.of(
                        StudentProfileField.EDUCATION_LEVEL,
                        StudentProfileField.COURSE,
                        StudentProfileField.CURRENT_YEAR,
                        StudentProfileField.ANNUAL_FAMILY_INCOME),
                def.getDiscoveryFields());
    }

    @Test
    void pmUspEssentialFields() {
        SchemeDefinition def = registry.definition(SchemeType.PM_USP_CSSS);
        assertEquals(Set.of(
                        StudentProfileField.COURSE,
                        StudentProfileField.CURRENT_YEAR,
                        StudentProfileField.CLASS12_PERCENTILE,
                        StudentProfileField.BOARD,
                        StudentProfileField.ANNUAL_FAMILY_INCOME,
                        StudentProfileField.REGULAR_MODE,
                        StudentProfileField.INSTITUTION_NAME),
                def.getEssentialFields());
    }

    @Test
    void pmUspConditionalFields() {
        SchemeDefinition def = registry.definition(SchemeType.PM_USP_CSSS);
        assertEquals(Set.of(
                        StudentProfileField.APPLICATION_TYPE,
                        StudentProfileField.RECEIVING_OTHER_SCHOLARSHIP),
                def.getConditionalFields());
    }

    @Test
    void ishanUdayDiscoveryAndEssential() {
        SchemeDefinition def = registry.definition(SchemeType.ISHAN_UDAY);
        assertEquals(Set.of(
                        StudentProfileField.EDUCATION_LEVEL,
                        StudentProfileField.CURRENT_YEAR,
                        StudentProfileField.DOMICILE_STATE,
                        StudentProfileField.ANNUAL_FAMILY_INCOME,
                        StudentProfileField.COURSE),
                def.getDiscoveryFields());
        assertEquals(Set.of(
                        StudentProfileField.EDUCATION_LEVEL,
                        StudentProfileField.COURSE,
                        StudentProfileField.CURRENT_YEAR,
                        StudentProfileField.DOMICILE_STATE,
                        StudentProfileField.ANNUAL_FAMILY_INCOME,
                        StudentProfileField.REGULAR_MODE,
                        StudentProfileField.COMPLETED_UG_DEGREE,
                        StudentProfileField.RECEIVING_OTHER_SCHOLARSHIP),
                def.getEssentialFields());
    }

    @Test
    void ishanUdayConditionalFields() {
        SchemeDefinition def = registry.definition(SchemeType.ISHAN_UDAY);
        assertEquals(Set.of(StudentProfileField.COURSE_TYPE), def.getConditionalFields());
    }

    @Test
    void yasasviUsesCurrentClassNotCurrentYear() {
        SchemeDefinition def = registry.definition(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS);
        assertEquals(Set.of(
                        StudentProfileField.CURRENT_CLASS,
                        StudentProfileField.SOCIAL_CATEGORY,
                        StudentProfileField.ANNUAL_FAMILY_INCOME),
                def.getDiscoveryFields());
        assertTrue(def.getEssentialFields().contains(StudentProfileField.CURRENT_CLASS));
        assertFalse(def.getEssentialFields().contains(StudentProfileField.CURRENT_YEAR));
    }

    @Test
    void yasasviConditionalAndSelection() {
        SchemeDefinition def = registry.definition(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS);
        assertEquals(Set.of(
                        StudentProfileField.INSTITUTION_TYPE,
                        StudentProfileField.PREVIOUS_CLASS_MARKS),
                def.getConditionalFields());
        assertEquals(Set.of(StudentProfileField.PREVIOUS_CLASS_MARKS), def.getSelectionFields());
    }

    @Test
    void topClassScChecksSocialCategoryAndInstitution() {
        SchemeDefinition def = registry.definition(SchemeType.TOP_CLASS_SC);
        assertTrue(def.getEssentialFields().contains(StudentProfileField.SOCIAL_CATEGORY));
        assertTrue(def.getEssentialFields().contains(StudentProfileField.INSTITUTION_NAME));
        assertTrue(def.getConditionalFields().contains(StudentProfileField.SIBLINGS_RECEIVING_BENEFIT));
        assertEquals(Set.of(StudentProfileField.ADMISSION_RANK), def.getSelectionFields());
    }

    @Test
    void topClassPwdChecksDisabilityFields() {
        SchemeDefinition def = registry.definition(SchemeType.TOP_CLASS_PWD);
        assertTrue(def.getEssentialFields().contains(StudentProfileField.HAS_DISABILITY));
        assertTrue(def.getEssentialFields().contains(StudentProfileField.DISABILITY_PERCENTAGE));
        assertTrue(def.getEssentialFields().contains(StudentProfileField.NATIONALITY));
        assertTrue(def.getConditionalFields().contains(StudentProfileField.HAS_VALID_DISABILITY_CERTIFICATE));
        assertTrue(def.getConditionalFields().contains(StudentProfileField.HAS_UDID_OR_UDID_ENROLLMENT));
    }

    @Test
    void noThreshholdEncodingInAnyDefinition() {
        // Thresholds like socialCategory == SC, income <= 450000, class in 9-12
        // do not exist. Definitions only carry required information fields.
        for (SchemeType type : registry.allSchemes()) {
            SchemeDefinition def = registry.definition(type);
            assertFalse(def.getEssentialFields().isEmpty());
            // Definitions must not contain numeric threshold text anywhere.
            String dump = def.toString();
            assertFalse(dump.contains(">="));
            assertFalse(dump.contains("<="));
            assertFalse(dump.contains("450000"));
            assertFalse(dump.contains("800000"));
        }
    }

    @Test
    void resolveKnownSchemeAliases() {
        assertEquals(SchemeType.PM_USP_CSSS, registry.resolve("PM-USP").orElseThrow());
        assertEquals(SchemeType.PM_USP_CSSS, registry.resolve("pm usp").orElseThrow());
        assertEquals(SchemeType.PM_USP_CSSS, registry.resolve("CSSS").orElseThrow());
        assertEquals(SchemeType.ISHAN_UDAY, registry.resolve("Ishan Uday").orElseThrow());
        assertEquals(SchemeType.PM_YASASVI_TOP_CLASS_SCHOOLS, registry.resolve("PM YASASVI").orElseThrow());
        assertEquals(SchemeType.TOP_CLASS_SC, registry.resolve("Top Class SC").orElseThrow());
        assertEquals(SchemeType.TOP_CLASS_PWD, registry.resolve("Top Class PWD").orElseThrow());
    }

    @Test
    void unsupportedSchemeIsNotSilentlyMapped() {
        assertTrue(registry.resolve("Some Random Scholarship").isEmpty());
        assertTrue(registry.resolve("PM-USP and Ishan Uday both").isEmpty());
        assertTrue(registry.resolve("").isEmpty());
        assertTrue(registry.resolve(null).isEmpty());
    }

    @Test
    void unknownSchemeThrowsOnDefinitionAccess() {
        assertThrows(IllegalArgumentException.class, () -> registry.definition(null));
    }

    @Test
    void discoveryOrderCoversEverySchemesDiscoveryFields() {
        Set<StudentProfileField> order = Set.copyOf(registry.discoveryOrder());
        for (SchemeType type : registry.allSchemes()) {
            SchemeDefinition def = registry.definition(type);
            assertTrue(order.containsAll(def.getDiscoveryFields()),
                    def.getScheme() + " discovery fields must be covered by the discovery order");
        }
    }

    @Test
    void discoveryOrderIsStableAndConversational() {
        assertEquals(StudentProfileField.EDUCATION_LEVEL, registry.discoveryOrder().get(0));
        // Deterministic across calls
        assertEquals(registry.discoveryOrder(), registry.discoveryOrder());
    }
}