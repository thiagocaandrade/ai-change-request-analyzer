package com.ai.change.request.analyzer.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ai.change.request.analyzer.domain.ChangeAnalysis;
import com.ai.change.request.analyzer.domain.ChangeRequest;
import com.ai.change.request.analyzer.domain.ChangeRequestRepository;
import com.ai.change.request.analyzer.domain.ChangeRequestStatus;
import com.ai.change.request.analyzer.domain.ImpactFinding;
import com.ai.change.request.analyzer.domain.ImpactFindingRepository;
import com.ai.change.request.analyzer.domain.RiskAssessment;
import com.ai.change.request.analyzer.domain.RiskAssessmentRepository;
import com.ai.change.request.analyzer.domain.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AnalysisMemoryServiceTest {

  @Autowired private ChangeRequestRepository changeRequestRepository;

  @Autowired private ImpactFindingRepository impactFindingRepository;

  @Autowired private RiskAssessmentRepository riskAssessmentRepository;

  @Autowired private AnalysisMemoryService memoryService;

  @BeforeEach
  void setUp() {
    riskAssessmentRepository.deleteAll();
    impactFindingRepository.deleteAll();
    changeRequestRepository.deleteAll();

    ChangeRequest request = new ChangeRequest();
    request.setText("Alterar o desconto de clientes VIP de 10% para 15%");
    request.setStatus(ChangeRequestStatus.COMPLETED);
    request.setTraceId("trace-memory-1");
    changeRequestRepository.save(request);

    ChangeAnalysis analysis = new ChangeAnalysis();
    analysis.setChangeRequest(request);
    analysis.addFinding(
        new ImpactFinding("discount-service", "Desconto VIP alterado para 15%", "HIGH"));
    analysis.setRiskAssessment(new RiskAssessment(RiskLevel.HIGH, 0.95, "regra financeira"));
    request.setAnalysis(analysis);
    changeRequestRepository.save(request);
  }

  @Test
  void searchByTermsReturnsPreviousRequestWithIdAndSummary() {
    AnalysisMemoryService.HistorySearchResult result =
        memoryService.searchByTerms("Alterar desconto");

    assertThat(result.degraded()).isFalse();
    assertThat(result.hits()).hasSizeGreaterThanOrEqualTo(1);
    assertThat(result.hits().get(0).requestId()).isNotBlank();
    assertThat(result.hits().get(0).summary()).contains("desconto");
  }

  @Test
  void searchByComponentReturnsRelatedAnalyses() {
    AnalysisMemoryService.HistorySearchResult result =
        memoryService.searchByComponent("discount-service");

    assertThat(result.degraded()).isFalse();
    assertThat(result.hits()).isNotEmpty();
    assertThat(result.hits().get(0).summary()).contains("desconto");
  }

  @Test
  void searchByBusinessRuleReturnsRelatedAnalyses() {
    AnalysisMemoryService.HistorySearchResult result = memoryService.searchByBusinessRule("VIP");

    assertThat(result.degraded()).isFalse();
    assertThat(result.hits()).isNotEmpty();
  }

  @Test
  void searchByClassificationReturnsMatchingAnalyses() {
    AnalysisMemoryService.HistorySearchResult result = memoryService.searchByClassification("HIGH");

    assertThat(result.degraded()).isFalse();
    assertThat(result.hits()).isNotEmpty();
    assertThat(result.hits().get(0).requestId()).isNotBlank();
  }

  @Test
  void noResultReturnsEmptyList() {
    AnalysisMemoryService.HistorySearchResult result =
        memoryService.searchByTerms("termo-que-nao-existe-xyz");

    assertThat(result.degraded()).isFalse();
    assertThat(result.hits()).isEmpty();
  }

  @Test
  void invalidClassificationReturnsEmptyListWithoutError() {
    AnalysisMemoryService.HistorySearchResult result =
        memoryService.searchByClassification("INEXISTENTE");

    assertThat(result.hits()).isEmpty();
  }

  @Test
  void failureReturnsEmptyMarkedResult() {
    ChangeRequestRepository failingRepository = mock(ChangeRequestRepository.class);
    when(failingRepository.findByTextContainingIgnoreCase("x"))
        .thenThrow(new IllegalStateException("db fora"));
    AnalysisMemoryService service =
        new AnalysisMemoryService(
            failingRepository, impactFindingRepository, riskAssessmentRepository);

    AnalysisMemoryService.HistorySearchResult result = service.searchByTerms("x");

    assertThat(result.hits()).isEmpty();
    assertThat(result.degraded()).isTrue();
  }
}
