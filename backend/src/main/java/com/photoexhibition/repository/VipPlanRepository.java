package com.photoexhibition.repository;

import com.photoexhibition.entity.VipPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VipPlanRepository extends JpaRepository<VipPlan, Long> {
    Optional<VipPlan> findByCode(String code);

    @Query("SELECT p.id FROM VipPlan p WHERE " +
        "lower(p.name) LIKE lower(concat('%', :keyword, '%')) " +
        "OR lower(p.code) LIKE lower(concat('%', :keyword, '%'))")
    List<Long> findIdsMatchingKeyword(@Param("keyword") String keyword);

    List<VipPlan> findAllByOrderBySortOrderAscIdAsc();
}
