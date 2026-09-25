package com.bharat.scholarship_rag_backend.service;

import com.bharat.scholarship_rag_backend.dto.StudentProfileDto;
import com.bharat.scholarship_rag_backend.entity.StudentProfile;
import com.bharat.scholarship_rag_backend.repository.StudentProfileRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
@Slf4j
public class StudentProfileService {

    private final StudentProfileRepo studentProfileRepo;

    public StudentProfileService(StudentProfileRepo studentProfileRepo) {
        this.studentProfileRepo = studentProfileRepo;
    }

    @Transactional
    public StudentProfileDto getOrCreate(String conversationId) {
        StudentProfile profile = studentProfileRepo.findByConversationId(conversationId)
                .orElseGet(() -> {
                    StudentProfile created = new StudentProfile();
                    created.setConversationId(conversationId);
                    created.setCreatedAt(LocalDateTime.now());
                    created.setUpdatedAt(LocalDateTime.now());
                    return studentProfileRepo.save(created);
                });
        return toDto(profile);
    }

    /**
     * Applies the values extracted by the query composer onto the student
     * profile. A field is written only when the profile has no value yet or
     * the extracted value corrects the stored one. Fields that do not map to
     * the profile entity are logged and skipped.
     */
    @Transactional
    public StudentProfileDto applyExtracted(String conversationId, Map<String, Object> extractedValues) {
        if (extractedValues == null || extractedValues.isEmpty()) {
            return getOrCreate(conversationId);
        }

        StudentProfile profile = studentProfileRepo.findByConversationId(conversationId).orElse(null);
        boolean isNew = profile == null;

        if (isNew) {
            profile = new StudentProfile();
            profile.setConversationId(conversationId);
            profile.setCreatedAt(LocalDateTime.now());
        }

        boolean changed = false;
        for (Map.Entry<String, Object> entry : extractedValues.entrySet()) {
            changed |= applyValue(profile, entry.getKey(), entry.getValue());
        }

        if (isNew || changed) {
            profile.setUpdatedAt(LocalDateTime.now());
            studentProfileRepo.save(profile);
        }

        return toDto(profile);
    }

    private boolean applyValue(StudentProfile profile, String fieldName, Object raw) {
        if (fieldName == null || raw == null) {
            return false;
        }
        return switch (fieldName) {
            case "EDUCATION_LEVEL" -> applyString(raw, profile::getEducationLevel, profile::setEducationLevel);
            case "COURSE" -> applyString(raw, profile::getCourse, profile::setCourse);
            case "CURRENT_YEAR" -> applyInteger(raw, profile::getCurrentYear, profile::setCurrentYear);
            case "CURRENT_CLASS" -> applyInteger(raw, profile::getCurrentClass, profile::setCurrentClass);
            case "REGULAR_MODE" -> applyBoolean(raw, profile::getRegularMode, profile::setRegularMode);
            case "COMPLETED_UG_DEGREE" ->
                    applyBoolean(raw, profile::getCompletedUGDegree, profile::setCompletedUGDegree);
            case "CLASS12_PERCENTILE" ->
                    applyDouble(raw, profile::getClass12Percentile, profile::setClass12Percentile);
            case "BOARD" -> applyString(raw, profile::getBoard, profile::setBoard);
            case "PREVIOUS_CLASS_MARKS" ->
                    applyDouble(raw, profile::getPreviousClassMarks, profile::setPreviousClassMarks);
            case "ANNUAL_FAMILY_INCOME" ->
                    applyBigDecimal(raw, profile::getAnnualFamilyIncome, profile::setAnnualFamilyIncome);
            case "SOCIAL_CATEGORY" -> applyString(raw, profile::getSocialCategory, profile::setSocialCategory);
            case "DOMICILE_STATE" -> applyString(raw, profile::getDomicileState, profile::setDomicileState);
            case "INSTITUTION_NAME" -> applyString(raw, profile::getInstitutionName, profile::setInstitutionName);
            case "INSTITUTION_TYPE" -> applyString(raw, profile::getInstitutionType, profile::setInstitutionType);
            case "RECEIVING_OTHER_SCHOLARSHIP" ->
                    applyBoolean(raw, profile::getReceivingOtherScholarship,
                            profile::setReceivingOtherScholarship);
            case "HAS_DISABILITY" -> applyBoolean(raw, profile::getHasDisability, profile::setHasDisability);
            case "DISABILITY_PERCENTAGE" ->
                    applyInteger(raw, profile::getDisabilityPercentage, profile::setDisabilityPercentage);
            case "NATIONALITY" -> applyString(raw, profile::getNationality, profile::setNationality);
            case "COURSE_TYPE", "STUDY_LEVEL", "APPLICATION_TYPE", "ADMISSION_RANK",
                    "HAS_UDID_OR_UDID_ENROLLMENT" -> {
                log.debug("Skipping field {} (no profile column yet)", fieldName);
                yield false;
            }
            default -> {
                log.debug("Skipping unknown field {}", fieldName);
                yield false;
            }
        };
    }

    private boolean applyString(Object raw, Supplier<String> getter, Consumer<String> setter) {
        String value = toText(raw);
        return setIfChanged(getter.get(), value, setter);
    }

    private boolean applyInteger(Object raw, Supplier<Integer> getter, Consumer<Integer> setter) {
        Integer value = toInteger(raw);
        return value != null && setIfChanged(getter.get(), value, setter);
    }

    private boolean applyBoolean(Object raw, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        Boolean value = toBoolean(raw);
        return value != null && setIfChanged(getter.get(), value, setter);
    }

    private boolean applyDouble(Object raw, Supplier<Double> getter, Consumer<Double> setter) {
        Double value = toDouble(raw);
        return value != null && setIfChanged(getter.get(), value, setter);
    }

    private boolean applyBigDecimal(Object raw, Supplier<BigDecimal> getter, Consumer<BigDecimal> setter) {
        BigDecimal value = toBigDecimal(raw);
        return value != null && setIfChanged(getter.get(), value, setter);
    }

    private <T> boolean setIfChanged(T current, T value, Consumer<T> setter) {
        if (value == null) {
            return false;
        }
        if (current == null || !current.equals(value)) {
            setter.accept(value);
            return true;
        }
        return false;
    }

    private String toText(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private Integer toInteger(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Boolean toBoolean(Object raw) {
        if (raw instanceof Boolean flag) {
            return flag;
        }
        if (raw instanceof String text) {
            return switch (text.trim().toLowerCase()) {
                case "true", "yes", "1" -> Boolean.TRUE;
                case "false", "no", "0" -> Boolean.FALSE;
                default -> null;
            };
        }
        return null;
    }

    private Double toDouble(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String text) {
            try {
                return Double.valueOf(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal toBigDecimal(Object raw) {
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        if (raw instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (raw instanceof String text) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private StudentProfileDto toDto(StudentProfile entity) {
        return StudentProfileDto.builder()
                .conversationId(entity.getConversationId())
                .educationLevel(entity.getEducationLevel())
                .course(entity.getCourse())
                .currentYear(entity.getCurrentYear())
                .currentClass(entity.getCurrentClass())
                .regularMode(entity.getRegularMode())
                .completedUGDegree(entity.getCompletedUGDegree())
                .class12Percentile(entity.getClass12Percentile())
                .board(entity.getBoard())
                .previousClassMarks(entity.getPreviousClassMarks())
                .annualFamilyIncome(entity.getAnnualFamilyIncome())
                .socialCategory(entity.getSocialCategory())
                .domicileState(entity.getDomicileState())
                .institutionName(entity.getInstitutionName())
                .institutionType(entity.getInstitutionType())
                .receivingOtherScholarship(entity.getReceivingOtherScholarship())
                .hasDisability(entity.getHasDisability())
                .disabilityPercentage(entity.getDisabilityPercentage())
                .nationality(entity.getNationality())
                .build();
    }
}