package com.city.manhole.repo;

import com.city.manhole.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IncidentRepo extends JpaRepository<Incident, Long> {
    Optional<Incident> findByCode(String code);
    List<Incident> findByReporterIdOrderByCreatedAtDesc(Long reporterId);
    List<Incident> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<Incident> findByStatusInOrderByCreatedAtDesc(Collection<String> statuses);
    List<Incident> findByOwnerStatusInOrderByCreatedAtDesc(Collection<String> statuses);
    List<Incident> findAllByOrderByCreatedAtDesc();
    List<Incident> findByPreviousIncidentId(Long previousIncidentId);
    long countByCodeStartingWith(String prefix);
}
