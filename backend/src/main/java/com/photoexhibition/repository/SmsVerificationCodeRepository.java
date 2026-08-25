package com.photoexhibition.repository;

import com.photoexhibition.entity.SmsCodePurpose;
import com.photoexhibition.entity.SmsVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SmsVerificationCodeRepository extends JpaRepository<SmsVerificationCode, Long> {

    long countByPhoneAndCreatedAtAfter(String phone, LocalDateTime createdAt);

    long countByRequestIpAndCreatedAtAfter(String requestIp, LocalDateTime createdAt);

    Optional<SmsVerificationCode> findTopByPhoneAndPurposeAndSuccessTrueAndUsedFalseOrderByCreatedAtDesc(String phone, SmsCodePurpose purpose);

    @Modifying
    @Query("UPDATE SmsVerificationCode s SET s.used = true, s.usedAt = :usedAt, s.failureReason = :reason " +
           "WHERE s.phone = :phone AND s.purpose = :purpose AND s.success = true AND s.used = false")
    void invalidateActiveCodes(@Param("phone") String phone,
                               @Param("purpose") SmsCodePurpose purpose,
                               @Param("usedAt") LocalDateTime usedAt,
                               @Param("reason") String reason);

    List<SmsVerificationCode> findTop20ByPhoneOrderByCreatedAtDesc(String phone);
}
