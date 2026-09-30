package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BackgroundJobHistoryService {
    @PersistenceContext
    private EntityManager entityManager;
    private final BackgroundJobService backgroundJobService;
    private final ScanTaskService scanTaskService;
    private final UserAccountRepository userAccountRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> list(UserAccount viewer, Long ownerUserId, boolean systemOnly, int page, int size) {
        return list(viewer, ownerUserId, systemOnly, page, size, false, false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(UserAccount viewer, Long ownerUserId, boolean systemOnly, int page, int size, boolean allRecords, boolean currentAccount) {
        if (viewer == null) throw new SecurityException("未授权");
        if (systemOnly && ownerUserId != null) throw new IllegalArgumentException("系统任务与账号筛选不能同时指定");
        if (viewer.getRole() != UserRole.SUPER_ADMIN) {
            if (systemOnly || (ownerUserId != null && !Objects.equals(ownerUserId, viewer.getId()))) {
                throw new SecurityException("无权查看其他账号任务");
            }
            ownerUserId = viewer.getId();
        }
        int pageSize = Math.max(1, Math.min(100, size));
        String backgroundScope = systemOnly ? " AND owner_user_id IS NULL" : ownerUserId == null ? "" : " AND owner_user_id = :owner";
        String scanScope = systemOnly ? " AND COALESCE(user_id, requested_by_user_id) IS NULL"
            : ownerUserId == null ? "" : viewer.getRole() == UserRole.SUPER_ADMIN && !currentAccount
            ? " AND COALESCE(user_id, requested_by_user_id) = :owner" : " AND requested_by_user_id = :owner";
        // Paginate the union in the database, not the bounded monitoring lists.
        String history = "SELECT id, 'job' AS source, created_at FROM background_job WHERE "
            + (allRecords ? "1=1" : "(status IN ('SUCCEEDED','SKIPPED','CANCELED','IGNORED','RETRIED') OR (status IN ('FAILED','PARTIAL_SUCCESS') AND updated_at < :cutoff))")
            + backgroundScope + " UNION ALL SELECT id, 'scan' AS source, created_at FROM scan_task WHERE "
            + (allRecords ? "1=1" : "(status IN ('COMPLETED','SKIPPED','CANCELED','IGNORED') OR (status = 'FAILED' AND updated_at < :cutoff))") + scanScope;
        LocalDateTime cutoff = allRecords ? null : LocalDateTime.now().minusHours(24);
        Query count = bind(entityManager.createNativeQuery("SELECT COUNT(*) FROM (" + history + ") history"), ownerUserId, cutoff);
        long total = ((Number) count.getSingleResult()).longValue();
        int totalPages = (int) Math.max(1, (total + pageSize - 1) / pageSize);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        Query query = bind(entityManager.createNativeQuery("SELECT id, source FROM (" + history
            + ") history ORDER BY created_at DESC, source ASC, id DESC"), ownerUserId, cutoff);
        query.setFirstResult(currentPage * pageSize);
        query.setMaxResults(pageSize);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object value : query.getResultList()) {
            Object[] row = (Object[]) value;
            Long id = ((Number) row[0]).longValue();
            boolean scan = "scan".equals(row[1]);
            Map<String, Object> item = new LinkedHashMap<>(scan
                ? scanTaskService.getHistorySummary(viewer, id) : backgroundJobService.getSummary(viewer, id));
            item.put("scan", scan);
            items.add(item);
        }
        java.util.Set<Long> ownerIds = new java.util.HashSet<>();
        for (Map<String, Object> item : items) {
            Object owner = item.get("ownerUserId");
            if (owner == null) owner = item.get("userId");
            if (owner == null) owner = item.get("requestedByUserId");
            if (owner instanceof Number) ownerIds.add(((Number) owner).longValue());
        }
        if (!ownerIds.isEmpty()) {
            Map<Long, String> labels = new LinkedHashMap<>();
            userAccountRepository.findIdentityByIdIn(ownerIds).forEach(identity -> labels.put(identity.getId(),
                identity.getNickname() == null || identity.getNickname().isBlank() ? identity.getUsername() : identity.getNickname()));
            for (Map<String, Object> item : items) {
                Object owner = item.get("ownerUserId");
                if (owner == null) owner = item.get("userId");
                if (owner == null) owner = item.get("requestedByUserId");
                if (owner == null) item.put("ownerLabel", "系统任务");
                else if (owner instanceof Number && labels.containsKey(((Number) owner).longValue())) {
                    item.put("ownerLabel", labels.get(((Number) owner).longValue()));
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", currentPage);
        result.put("size", pageSize);
        result.put("totalPages", totalPages);
        return result;
    }

    private Query bind(Query query, Long ownerUserId, LocalDateTime cutoff) {
        if (cutoff != null) query.setParameter("cutoff", cutoff);
        if (ownerUserId != null) query.setParameter("owner", ownerUserId);
        return query;
    }
}
