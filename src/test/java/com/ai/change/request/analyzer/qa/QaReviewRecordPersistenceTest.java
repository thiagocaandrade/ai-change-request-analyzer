package com.ai.change.request.analyzer.qa;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.change.request.analyzer.domain.ChangeAnalysis;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.domain.ChangeRequestStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class QaReviewRecordPersistenceTest {

  @Autowired private ChangeRequestRepository changeRequestRepository;

  @Autowired private QaReviewRecordRepository qaReviewRecordRepository;

  @Test
  void persistsAndRecoversQaRecordWithFindingsByRequest() {
    ChangeRequest request = new ChangeRequest();
    request.setText("Alterar o desconto de clientes VIP de 10% para 15%.");
    request.setStatus(ChangeRequestStatus.COMPLETED);
    request.setTraceId("trace-qa-persist");
    changeRequestRepository.save(request);

    QaReviewRecord record =
        new QaReviewRecord(
            request,
            "CODE_REVIEW",
            "code-review-v1",
            "{\"findings\":[{\"component\":\"discount-service\"}]}",
            false,
            0,
            "trace-qa-persist",
            Instant.now());
    record.addFinding(
        new QaFinding(
            "discount-service", "teste de regressao ausente", "HIGH", "business-rules.md"));
    qaReviewRecordRepository.save(record);

    List<QaReviewRecord> records =
        qaReviewRecordRepository.findByChangeRequestIdOrderByCreatedAtAsc(request.getId());
    assertThat(records).hasSize(1);
    QaReviewRecord loaded = records.get(0);
    assertThat(loaded.getStage()).isEqualTo("CODE_REVIEW");
    assertThat(loaded.getPromptVersion()).isEqualTo("code-review-v1");
    assertThat(loaded.getResultJson()).contains("discount-service");
    assertThat(loaded.isDegraded()).isFalse();
    assertThat(loaded.getTraceId()).isEqualTo("trace-qa-persist");
    assertThat(loaded.getFindings()).hasSize(1);
    QaFinding finding = loaded.getFindings().get(0);
    assertThat(finding.getComponent()).isEqualTo("discount-service");
    assertThat(finding.getSeverity()).isEqualTo("HIGH");
    assertThat(finding.getSource()).isEqualTo("business-rules.md");
  }

  @Test
  void qaRecordCanBeLinkedToAnalysisAndDedupedByStageAndTrace() {
    ChangeRequest request = new ChangeRequest();
    request.setText("Alterar desconto VIP");
    request.setStatus(ChangeRequestStatus.COMPLETED);
    request.setTraceId("trace-qa-link");
    ChangeAnalysis analysis = new ChangeAnalysis();
    analysis.setChangeRequest(request);
    request.setAnalysis(analysis);
    changeRequestRepository.save(request);

    QaReviewRecord record =
        new QaReviewRecord(
            request,
            "TEST_GENERATION",
            "test-generation-v1",
            "{}",
            true,
            2,
            "trace-qa-link",
            Instant.now());
    record.setAnalysis(analysis);
    qaReviewRecordRepository.save(record);

    assertThat(
            qaReviewRecordRepository.existsByChangeRequestIdAndStageAndTraceId(
                request.getId(), "TEST_GENERATION", "trace-qa-link"))
        .isTrue();
    assertThat(
            qaReviewRecordRepository.existsByChangeRequestIdAndStageAndTraceId(
                request.getId(), "CODE_REVIEW", "trace-qa-link"))
        .isFalse();

    QaReviewRecord loaded =
        qaReviewRecordRepository.findByChangeRequestIdOrderByCreatedAtAsc(request.getId()).get(0);
    assertThat(loaded.getAnalysis()).isNotNull();
    assertThat(loaded.isDegraded()).isTrue();
    assertThat(loaded.getIterations()).isEqualTo(2);
  }
}
