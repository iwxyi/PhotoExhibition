package com.photoexhibition.repository;

import com.photoexhibition.entity.EmailCodePurpose;
import com.photoexhibition.entity.EmailVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    long countByEmailAndCreatedAtAfter(String email, LocalDateTime createdAt);

    long countByRequestIpAndCreatedAtAfter(String requestIp, LocalDateTime createdAt);

    Optional<EmailVerificationCode> findTopByEmailAndPurposeAndSuccessTrueAndUsedFalseOrderByCreatedAtDesc(String email, EmailCodePurpose purpose);

    @Modifying
    @Query("UPDATE EmailVerificationCode e SET e.used = true, e.usedAt = :usedAt, e.failureReason = :reason " +
        "WHERE e.email = :email AND e.purpose = :purpose AND e.success = true AND e.used = false")
    void invalidateActiveCodes(@Param("email") String email,
                               @Param("purpose") EmailCodePurpose purpose,
                               @Param("usedAt") LocalDateTime usedAt,
                               @Param("reason") String reason);
}
