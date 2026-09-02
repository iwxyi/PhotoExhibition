package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserStatus;
import com.photoexhibition.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublicUserScopeService {

    private final UserAccountRepository userAccountRepository;
    private final SystemConfigService systemConfigService;

    public Long resolveUserId(String userSlug) {
        if (userSlug == null || userSlug.trim().isEmpty()) {
            if (systemConfigService.isMultiUserEnabled()) {
                throw new RuntimeException("多用户模式下必须指定用户标识");
            }
            return null;
        }
        UserAccount user = userAccountRepository.findBySlug(userSlug.trim())
            .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new RuntimeException("用户当前不可公开访问");
        }
        if (systemConfigService.isMultiUserEnabled() && Boolean.FALSE.equals(user.getMultiUserVisible())) {
            throw new RuntimeException("用户未公开");
        }
        return user.getId();
    }
}
