package com.ai.change.request.analyzer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class ChangeRequestRepositoryTest {

  @Autowired private ChangeRequestRepository repository;

  @Test
  void savesAndLoadsChangeRequest() {
    ChangeRequest request = new ChangeRequest();
    request.setText("Alterar o desconto de clientes VIP de 10% para 15%.");
    request.setStatus(ChangeRequestStatus.PENDING);
    request.setTraceId("trace-123");
    repository.save(request);

    Optional<ChangeRequest> loaded = repository.findById(request.getId());
    assertThat(loaded).isPresent();
    assertThat(loaded.get().getText())
        .isEqualTo("Alterar o desconto de clientes VIP de 10% para 15%.");
    assertThat(loaded.get().getStatus()).isEqualTo(ChangeRequestStatus.PENDING);
    assertThat(loaded.get().getTraceId()).isEqualTo("trace-123");
    assertThat(loaded.get().getCreatedAt()).isNotNull();
  }
}
