package com.bharat.scholarship_rag_backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "semantic_memories")
public class SemanticMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String conversationId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String context;

    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] embedding;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}