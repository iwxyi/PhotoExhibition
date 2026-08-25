package com.photoexhibition.repository;

import com.photoexhibition.entity.VipPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VipPlanRepository extends JpaRepository<VipPlan, Long> {
    Optional<VipPlan> findByCode(String code);

    List<VipPlan> findAllByOrderBySortOrderAscIdAsc();
}
