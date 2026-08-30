package com.ai.change.request.analyzer.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {

  List<RiskAssessment> findByLevel(RiskLevel level);
}
