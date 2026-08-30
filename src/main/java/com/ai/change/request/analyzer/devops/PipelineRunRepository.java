package com.ai.change.request.analyzer.devops;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio das execucoes de pipeline. */
public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {

  List<PipelineRun> findTop100ByOrderByCreatedAtAsc();
}
