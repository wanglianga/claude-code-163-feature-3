package com.city.manhole.repo;

import com.city.manhole.model.ClaimMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimMaterialRepo extends JpaRepository<ClaimMaterial, Long> {
    List<ClaimMaterial> findByCaseIdOrderByCreatedAtAsc(Long caseId);
}
