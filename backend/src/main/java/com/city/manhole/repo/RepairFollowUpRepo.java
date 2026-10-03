package com.city.manhole.repo;

import com.city.manhole.model.RepairFollowUp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RepairFollowUpRepo extends JpaRepository<RepairFollowUp, Long> {
    Optional<RepairFollowUp> findByCaseId(Long caseId);
    List<RepairFollowUp> findByIncidentIdInAndStatusIn(Collection<Long> incidentIds, Collection<String> statuses);
    List<RepairFollowUp> findAllByOrderByCreatedAtDesc();
}
