package com.bharat.scholarship_rag_backend.validator;

import com.bharat.scholarship_rag_backend.scheme.StudentProfileField;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Deterministic conversion of missing fields into a natural-language question.
 * No LLM is involved, so it cannot invent requirements or rephrase facts.
 */
@Component
public class QuestionBuilder {

    private static final Map<StudentProfileField, String> FRAGMENTS = Map.ofEntries(
            Map.entry(StudentProfileField.EDUCATION_LEVEL,
                    "what level you are currently studying at (for example Class 12, undergraduate, or postgraduate)"),
            Map.entry(StudentProfileField.COURSE, "which course you are pursuing"),
            Map.entry(StudentProfileField.COURSE_TYPE, "which type of course you are in (full-time regular, or other)"),
            Map.entry(StudentProfileField.CURRENT_YEAR, "which year of your course you are currently in"),
            Map.entry(StudentProfileField.CURRENT_CLASS, "which class you are currently studying in"),
            Map.entry(StudentProfileField.REGULAR_MODE, "whether you are studying in regular full-time mode"),
            Map.entry(StudentProfileField.COMPLETED_UG_DEGREE, "whether you have already completed an undergraduate degree"),
            Map.entry(StudentProfileField.CLASS12_PERCENTILE, "your Class 12 percentile"),
            Map.entry(StudentProfileField.BOARD, "the board you completed Class 12 from"),
            Map.entry(StudentProfileField.PREVIOUS_CLASS_MARKS, "the marks you scored in your previous class"),
            Map.entry(StudentProfileField.ANNUAL_FAMILY_INCOME, "your family's annual income"),
            Map.entry(StudentProfileField.SOCIAL_CATEGORY, "your social category"),
            Map.entry(StudentProfileField.DOMICILE_STATE, "your state of domicile"),
            Map.entry(StudentProfileField.INSTITUTION_NAME, "the institution you are currently studying in"),
            Map.entry(StudentProfileField.INSTITUTION_TYPE, "the type of institution you are studying in"),
            Map.entry(StudentProfileField.RECEIVING_OTHER_SCHOLARSHIP,
                    "whether you currently receive any other scholarship or fee waiver"),
            Map.entry(StudentProfileField.HAS_DISABILITY, "whether you have a benchmark disability"),
            Map.entry(StudentProfileField.DISABILITY_PERCENTAGE, "your disability percentage"),
            Map.entry(StudentProfileField.NATIONALITY, "your nationality"),
            Map.entry(StudentProfileField.APPLICATION_TYPE, "whether you are a fresh applicant or a renewal"),
            Map.entry(StudentProfileField.ADMISSION_RANK, "your admission rank in the current course"),
            Map.entry(StudentProfileField.HAS_VALID_DISABILITY_CERTIFICATE,
                    "whether you hold a valid disability certificate"),
            Map.entry(StudentProfileField.HAS_UDID_OR_UDID_ENROLLMENT,
                    "whether you have a UDID or are enrolled for a UDID"),
            Map.entry(StudentProfileField.SIBLINGS_RECEIVING_BENEFIT,
                    "whether any of your siblings already receive this scholarship benefit")
    );

    public String buildQuestion(List<StudentProfileField> missingFields) {
        if (missingFields == null || missingFields.isEmpty()) {
            return null;
        }
        List<String> fragments = missingFields.stream()
                .map(field -> FRAGMENTS.getOrDefault(field,
                        "a detail the scheme needs (" + field + ")"))
                .collect(Collectors.toList());

        if (fragments.size() == 1) {
            return "Could you please tell me " + fragments.get(0) + "?";
        }
        if (fragments.size() == 2) {
            return "Could you please tell me " + fragments.get(0) + ", and " + fragments.get(1) + "?";
        }
        return "Could you please tell me "
                + String.join(", ", fragments.subList(0, fragments.size() - 1))
                + ", and " + fragments.get(fragments.size() - 1) + "?";
    }
}