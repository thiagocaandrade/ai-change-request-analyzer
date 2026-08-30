package com.ai.change.request.analyzer.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, UUID> {

  List<ChangeRequest> findByTextContainingIgnoreCase(String text);
}
