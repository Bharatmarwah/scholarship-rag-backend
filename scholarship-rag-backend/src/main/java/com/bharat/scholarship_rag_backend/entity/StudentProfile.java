package com.bharat.scholarship_rag_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String conversationId;

    // Education
    private String educationLevel;

    private String course;

    private Integer currentYear;

    private Boolean regularMode;

    private Boolean completedUGDegree;

    private Double class12Percentile;

    private String board;

    // Financial
    private BigDecimal annualFamilyIncome;

    // Social
    private String socialCategory;

    // Domicile
    private String domicileState;

    // Institution
    private String institutionName;

    private String institutionType;

    // Existing scholarship
    private Boolean receivingOtherScholarship;

    // Disability
    private Boolean hasDisability;

    private Integer disabilityPercentage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}