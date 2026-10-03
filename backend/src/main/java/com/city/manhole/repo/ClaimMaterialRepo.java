package com.city.manhole.repo;

import com.city.manhole.model.ClaimMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClaimMaterialRepo extends JpaRepository<ClaimMaterial, Long> {
    List<ClaimMaterial> findByCaseIdOrderById(Long caseId);
    Optional<ClaimMaterial> findByCaseIdAndMaterialType(Long caseId, String materialType);
}
