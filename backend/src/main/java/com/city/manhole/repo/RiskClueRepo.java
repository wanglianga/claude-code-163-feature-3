package com.city.manhole.repo;

import com.city.manhole.model.RiskClue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskClueRepo extends JpaRepository<RiskClue, Long> {
    Optional<RiskClue> findByCaseId(Long caseId);
    List<RiskClue> findAllByOrderByCreatedAtDesc();
}
