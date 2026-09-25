package com.bharat.scholarship_rag_backend.repository;

import com.bharat.scholarship_rag_backend.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentProfileRepo extends JpaRepository<StudentProfile, UUID> {
    Optional<StudentProfile> findByConversationId(String conversationId);
}
