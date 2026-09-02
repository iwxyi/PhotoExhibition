package com.photoexhibition.repository;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    interface UserOverviewProjection {
        Long getId();
        UserStatus getStatus();
        Long getStorageQuotaBytes();
        Long getVipExtraQuotaBytes();
        Long getCurrentVipPlanId();
        LocalDateTime getVipExpireAt();
        Long getStorageUsedBytes();
    }

    interface UserIdentityProjection {
        Long getId();
        String getUsername();
        String getNickname();
        String getSlug();
    }

    Optional<UserAccount> findBySlug(String slug);

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByPhone(String phone);

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByUsernameOrPhone(String username, String phone);

    Optional<UserAccount> findFirstByRoleOrderByIdAsc(UserRole role);

    Optional<UserAccount> findFirstByOrderByIdAsc();

    List<UserAccount> findByRoleAndStatusAndMultiUserVisibleTrueOrderByCreatedAtAsc(UserRole role, UserStatus status);

    List<UserAccount> findByStatusAndMultiUserVisibleTrueOrderByCreatedAtAsc(UserStatus status);

    boolean existsBySlug(String slug);

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    long countByStatus(UserStatus status);

    @Query("SELECT u.id FROM UserAccount u ORDER BY u.id ASC")
    List<Long> findAllIds();

    @Query("SELECT u.id AS id, u.status AS status, u.storageQuotaBytes AS storageQuotaBytes, " +
        "u.vipExtraQuotaBytes AS vipExtraQuotaBytes, u.currentVipPlanId AS currentVipPlanId, " +
        "u.vipExpireAt AS vipExpireAt, u.storageUsedBytes AS storageUsedBytes " +
        "FROM UserAccount u")
    List<UserOverviewProjection> findAllOverviewSnapshots();

    @Query("SELECT u.id AS id, u.username AS username, u.nickname AS nickname, u.slug AS slug " +
        "FROM UserAccount u WHERE u.id IN :ids")
    List<UserIdentityProjection> findIdentityByIdIn(@Param("ids") Collection<Long> ids);

    @Query("SELECT u FROM UserAccount u " +
        "WHERE (:keyword IS NULL OR :keyword = '' " +
        "OR lower(u.username) LIKE lower(concat('%', :keyword, '%')) " +
        "OR lower(coalesce(u.nickname, '')) LIKE lower(concat('%', :keyword, '%')) " +
        "OR lower(coalesce(u.slug, '')) LIKE lower(concat('%', :keyword, '%')) " +
        "OR lower(coalesce(u.email, '')) LIKE lower(concat('%', :keyword, '%')) " +
        "OR coalesce(u.phone, '') LIKE concat('%', :keyword, '%'))")
    Page<UserAccount> search(@Param("keyword") String keyword, Pageable pageable);
}
