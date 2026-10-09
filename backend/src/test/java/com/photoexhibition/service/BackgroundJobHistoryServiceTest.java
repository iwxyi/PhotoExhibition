package com.photoexhibition.service;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackgroundJobHistoryServiceTest {
    @Mock EntityManager entityManager;
    @Mock BackgroundJobService jobs;
    @Mock ScanTaskService scans;
    @Mock UserAccountRepository users;
    @Mock Query count;
    @Mock Query rows;
    @Mock Query summary;
    BackgroundJobHistoryService service;
    UserAccount viewer;

    @BeforeEach
    void setup() {
        service = new BackgroundJobHistoryService(jobs, scans, users);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        viewer = new UserAccount();
        viewer.setId(7L);
        viewer.setRole(UserRole.SUPER_ADMIN);
    }

    void queries(long total, List<Object[]> results) {
        when(entityManager.createNativeQuery(startsWith("SELECT COUNT"))).thenReturn(count);
        when(entityManager.createNativeQuery(startsWith("SELECT id, source"))).thenReturn(rows);
        when(count.getSingleResult()).thenReturn(total);
        when(rows.getResultList()).thenReturn(results);
    }

    @Test
    void allRecordsIncludesActiveWithoutCutoffAndScopesCurrentSuperAdmin() {
        queries(1L, List.of());
        when(entityManager.createNativeQuery(startsWith("SELECT status"))).thenReturn(summary);
        when(summary.getResultList()).thenReturn(List.of(new Object[]{"RUNNING", 12L}, new Object[]{"FAILED", 4L}));
        Map<String, Object> result = service.list(viewer, 7L, false, 0, 20, true, true, "failed");
        assertEquals(Map.of("active", 12L, "failed", 4L), result.get("statistics"));
        verify(entityManager, times(3)).createNativeQuery(contains("WHERE 1=1"));
        verify(entityManager, times(3)).createNativeQuery(contains("requested_by_user_id = :owner"));
        verify(entityManager).createNativeQuery(contains("history WHERE status IN ('FAILED','PARTIAL_SUCCESS','BLOCKED') ORDER BY"));
        verify(rows, never()).setParameter(eq("cutoff"), any());
        verify(rows).setParameter("owner", 7L);
    }

    @Test
    void rejectsUnknownFilterBeforeQueryingDatabase() {
        assertThrows(IllegalArgumentException.class, () -> service.list(viewer, null, false, 0, 20, true, false, "invalid"));
        verifyNoInteractions(entityManager);
    }

    @Test
    void paginatesCombinedHistoryWithoutLoadingItems() {
        queries(205L, List.of(new Object[]{3L, "scan"}, new Object[]{4L, "job"}));
        when(scans.getHistorySummary(viewer, 3L)).thenReturn(Map.of("id", 3L));
        when(jobs.getSummary(viewer, 4L)).thenReturn(Map.of("id", 4L));
        Map<String, Object> result = service.list(viewer, null, false, 2, 20);
        assertEquals(205L, result.get("total"));
        assertEquals(11, result.get("totalPages"));
        assertEquals(2, ((List<?>) result.get("items")).size());
        verify(rows).setFirstResult(40);
        verify(rows).setMaxResults(20);
        verify(jobs, never()).get(any(), anyLong());
    }

    @Test
    void scopesRegularAccountAndClampsPage() {
        viewer.setRole(UserRole.USER_ADMIN);
        queries(1L, List.of());
        Map<String, Object> result = service.list(viewer, null, false, 99, 500);
        assertEquals(0, result.get("page"));
        assertEquals(100, result.get("size"));
        verify(rows).setParameter("owner", 7L);
        verify(entityManager, times(2)).createNativeQuery(contains("requested_by_user_id = :owner"));
    }

    @Test
    void rejectsOtherAccountAndSystemScope() {
        viewer.setRole(UserRole.USER_ADMIN);
        assertThrows(SecurityException.class, () -> service.list(viewer, 8L, false, 0, 20));
        assertThrows(SecurityException.class, () -> service.list(viewer, null, true, 0, 20));
        verifyNoInteractions(entityManager);
    }

    @Test
    void systemScopeUsesNoOwnerParameter() {
        queries(0L, List.of());
        service.list(viewer, null, true, 0, 20);
        verify(rows, never()).setParameter(eq("owner"), any());
        verify(entityManager, times(2)).createNativeQuery(contains("COALESCE(user_id, requested_by_user_id) IS NULL"));
    }
}
