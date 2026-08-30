package com.ai.change.request.analyzer.security;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAssessmentRepository extends JpaRepository<SecurityAssessment, UUID> {

  List<SecurityAssessment> findByChangeRequestId(UUID changeRequestId);
}
