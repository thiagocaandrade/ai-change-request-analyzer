package com.ai.change.request.analyzer.qa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QaFindingRepository extends JpaRepository<QaFinding, UUID> {}
