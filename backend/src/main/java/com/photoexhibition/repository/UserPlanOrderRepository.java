package com.photoexhibition.repository;

import com.photoexhibition.entity.UserPlanOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserPlanOrderRepository extends JpaRepository<UserPlanOrder, Long> {
    Page<UserPlanOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<UserPlanOrder> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<UserPlanOrder> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    Page<UserPlanOrder> findByAutoRenewEnabledTrueOrderByCreatedAtDesc(Pageable pageable);

    Page<UserPlanOrder> findByUserIdAndAutoRenewEnabledTrueOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<UserPlanOrder> findByAutoRenewEnabledTrueAndNextRenewalAtLessThanEqualAndStatusInOrderByNextRenewalAtAscCreatedAtAsc(
        LocalDateTime nextRenewalAt,
        List<String> statuses
    );

    boolean existsByRenewalSourceOrderIdAndStatusIn(Long renewalSourceOrderId, List<String> statuses);

    Optional<UserPlanOrder> findFirstByRenewalSourceOrderIdAndStatusInOrderByCreatedAtDesc(Long renewalSourceOrderId, List<String> statuses);

    Optional<UserPlanOrder> findFirstByRenewalSourceOrderIdOrderByCreatedAtDesc(Long renewalSourceOrderId);

    Optional<UserPlanOrder> findByOrderNo(String orderNo);
}
