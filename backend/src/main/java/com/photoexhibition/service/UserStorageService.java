package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.entity.VipPlan;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.UserAccountRepository;
import com.photoexhibition.repository.VipPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserStorageService {

    private final UserAccountRepository userAccountRepository;
    private final PhotoRepository photoRepository;
    private final VipPlanRepository vipPlanRepository;

    public void ensureQuotaAvailable(UserAccount user, long incomingBytes) {
        if (user == null || incomingBytes <= 0) {
            return;
        }
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }

        long used = user.getStorageUsedBytes() == null ? 0L : user.getStorageUsedBytes();
        long quota = getEffectiveQuotaBytes(user);
        if (used + incomingBytes > quota) {
            throw new IllegalArgumentException("空间不足，当前上传将超出用户空间限额");
        }
    }

    @Transactional
    public long adjustStorageUsage(Long userId, long deltaBytes) {
        if (userId == null || deltaBytes == 0) {
            return 0L;
        }
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        long current = user.getStorageUsedBytes() == null ? 0L : user.getStorageUsedBytes();
        long next = Math.max(0L, current + deltaBytes);
        user.setStorageUsedBytes(next);
        userAccountRepository.save(user);
        return next;
    }

    @Transactional
    public long refreshStorageUsage(Long userId) {
        if (userId == null) {
            return 0L;
        }
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        long used = photoRepository.sumFileSizeByUserId(userId);
        user.setStorageUsedBytes(used);
        userAccountRepository.save(user);
        return used;
    }

    public long getEffectiveQuotaBytes(UserAccount user) {
        if (user == null) {
            return 0L;
        }
        long baseQuota = user.getStorageQuotaBytes() == null ? 0L : user.getStorageQuotaBytes();
        long vipExtraQuota = user.getVipExtraQuotaBytes() == null ? 0L : user.getVipExtraQuotaBytes();
        long planQuota = resolvePlanQuotaBytes(user);
        return Math.max(0L, baseQuota + vipExtraQuota + planQuota);
    }

    public long resolvePlanQuotaBytes(UserAccount user) {
        if (user == null || user.getCurrentVipPlanId() == null) {
            return 0L;
        }
        if (user.getVipExpireAt() != null && user.getVipExpireAt().isBefore(LocalDateTime.now())) {
            return 0L;
        }
        VipPlan plan = vipPlanRepository.findById(user.getCurrentVipPlanId()).orElse(null);
        if (plan == null || !Boolean.TRUE.equals(plan.getEnabled())) {
            return 0L;
        }
        return plan.getExtraQuotaBytes() == null ? 0L : Math.max(0L, plan.getExtraQuotaBytes());
    }
}
