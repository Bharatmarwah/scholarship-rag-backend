package com.bharat.scholarship_rag_backend.normalization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileValueNormalizerTest {

    private final ProfileValueNormalizer normalizer = new ProfileValueNormalizer();

    @Test
    void normalizesEveryCommonCategoryPhrasing() {
        assertEquals("GENERAL", normalizer.normalizeSocialCategory("General"));
        assertEquals("GENERAL", normalizer.normalizeSocialCategory("UR"));
        assertEquals("GENERAL", normalizer.normalizeSocialCategory("unreserved"));
        assertEquals("OBC", normalizer.normalizeSocialCategory("OBC"));
        assertEquals("OBC", normalizer.normalizeSocialCategory("OBC-NCL"));
        assertEquals("OBC", normalizer.normalizeSocialCategory("Other Backward Class"));
        assertEquals("OBC", normalizer.normalizeSocialCategory("backward caste"));
        assertEquals("EBC", normalizer.normalizeSocialCategory("EBC"));
        assertEquals("EBC", normalizer.normalizeSocialCategory("economically backward class"));
        assertEquals("DNT", normalizer.normalizeSocialCategory("DNT"));
        assertEquals("DNT", normalizer.normalizeSocialCategory("denotified and nomadic tribe"));
        assertEquals("SC", normalizer.normalizeSocialCategory("Scheduled Caste"));
        assertEquals("SC", normalizer.normalizeSocialCategory("sc"));
        assertEquals("ST", normalizer.normalizeSocialCategory("scheduled tribe"));
        assertEquals("EWS", normalizer.normalizeSocialCategory("economically weaker section"));
        assertEquals("MINORITY", normalizer.normalizeSocialCategory("minority"));
    }

    @Test
    void keepsUnrecognizedCategoryInsteadOfDroppingIt() {
        assertEquals("creamy layer", normalizer.normalizeSocialCategory("creamy layer"));
    }

    @Test
    void detectsCategoryInFreeTextAndIgnoresUnrelatedText() {
        assertEquals("OBC", normalizer.detectSocialCategory("I belong to OBC category").orElseThrow());
        assertEquals("EBC", normalizer.detectSocialCategory("we are an EBC family").orElseThrow());
        assertEquals("SC", normalizer.detectSocialCategory("I am a scheduled caste student").orElseThrow());
        assertTrue(normalizer.detectSocialCategory("I want to apply for a scholarship").isEmpty());
        assertTrue(normalizer.detectSocialCategory("what is ur income limit").isEmpty());
        assertTrue(normalizer.detectSocialCategory("give me general information").isEmpty());
    }

    @Test
    void normalizesEducationLevelToRoutingTokens() {
        assertEquals("CLASS_12", normalizer.normalizeEducationLevel("Class 12"));
        assertEquals("CLASS_12", normalizer.normalizeEducationLevel("12th"));
        assertEquals("CLASS_12", normalizer.normalizeEducationLevel("intermediate"));
        assertEquals("CLASS_11", normalizer.normalizeEducationLevel("11th standard"));
        assertEquals("CLASS_10", normalizer.normalizeEducationLevel("matric"));
        assertEquals("UG", normalizer.normalizeEducationLevel("B.Tech"));
        assertEquals("UG", normalizer.normalizeEducationLevel("undergraduate"));
        assertEquals("UG", normalizer.normalizeEducationLevel("college"));
        assertEquals("PG", normalizer.normalizeEducationLevel("postgraduate"));
        assertEquals("PG", normalizer.normalizeEducationLevel("Masters"));
    }

    @Test
    void normalizesCourseSpellingsToCanonicalForm() {
        assertEquals("B.Tech", normalizer.normalizeCourse("btech"));
        assertEquals("B.Tech", normalizer.normalizeCourse("B.Tech"));
        assertEquals("B.Tech", normalizer.normalizeCourse("B Tech"));
        assertEquals("M.Tech", normalizer.normalizeCourse("m.tech"));
        assertEquals("BSc", normalizer.normalizeCourse("bsc"));
        assertEquals("BCA", normalizer.normalizeCourse("BCA"));
        assertEquals("MBA", normalizer.normalizeCourse("mba"));
        assertEquals("MBBS", normalizer.normalizeCourse("mbbs"));
        assertEquals("B.Com", normalizer.normalizeCourse("bcom"));
        assertEquals("Computer Science", normalizer.normalizeCourse("Computer Science"));
    }

    @Test
    void detectsCourseFromShortReplyButNotFromLongSentences() {
        assertEquals("B.Tech", normalizer.detectCourse("btech").orElseThrow());
        assertEquals("B.Tech", normalizer.detectCourse("I am doing btech").orElseThrow());
        assertEquals("MBA", normalizer.detectCourse("mba").orElseThrow());
        assertTrue(normalizer.detectCourse("I want to apply for a scholarship").isEmpty());
        assertTrue(normalizer.detectCourse("what is your income limit").isEmpty());
        assertTrue(normalizer.detectCourse(
                "can you tell me which scholarships I am eligible for right now").isEmpty());
    }
}
