package com.ai.change.request.analyzer.qa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QaReviewRecordRepository extends JpaRepository<QaReviewRecord, UUID> {

  List<QaReviewRecord> findByChangeRequestIdOrderByCreatedAtAsc(UUID changeRequestId);

  boolean existsByChangeRequestIdAndStageAndTraceId(UUID changeRequestId, String stage, String traceId);
}
