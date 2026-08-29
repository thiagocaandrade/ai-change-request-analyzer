package com.ai.change.request.analyzer.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImpactFindingRepository extends JpaRepository<ImpactFinding, UUID> {}
