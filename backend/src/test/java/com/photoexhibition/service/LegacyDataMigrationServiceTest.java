package com.photoexhibition.service;

import com.photoexhibition.repository.AdminUserRepository;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.CommentRepository;
import com.photoexhibition.repository.FaceRepository;
import com.photoexhibition.repository.FilterOptionRepository;
import com.photoexhibition.repository.PersonProfileRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.StorageProviderRepository;
import com.photoexhibition.repository.TagRepository;
import com.photoexhibition.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyDataMigrationServiceTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private AdminUserRepository adminUserRepository;
    @Mock private AlbumRepository albumRepository;
    @Mock private PhotoRepository photoRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private FaceRepository faceRepository;
    @Mock private FilterOptionRepository filterOptionRepository;
    @Mock private PersonProfileRepository personProfileRepository;
    @Mock private TagRepository tagRepository;
    @Mock private StorageProviderRepository storageProviderRepository;
    @Mock private FilterOptionService filterOptionService;
    @Mock private UserStorageService userStorageService;
    @Mock private UserPathService userPathService;
    @Mock private SystemConfigService systemConfigService;

    @Test
    void shouldRewriteLegacyPathToStorageReferenceWhenAvailable() {
        LegacyDataMigrationService service = new LegacyDataMigrationService(
            userAccountRepository,
            adminUserRepository,
            albumRepository,
            photoRepository,
            commentRepository,
            faceRepository,
            filterOptionRepository,
            personProfileRepository,
            tagRepository,
            storageProviderRepository,
            filterOptionService,
            userStorageService,
            userPathService,
            systemConfigService
        );

        String legacyPath = "/srv/app/data/photos/旅行/东京";
        Path ownedPath = Path.of("/srv/app/data/photos/7/旅行/东京");

        when(userPathService.toOwnedPhotoPath(legacyPath, 7L)).thenReturn(ownedPath);
        when(userPathService.tryBuildStoragePathReference(ownedPath.toString(), 7L))
            .thenReturn(Optional.of("storage://3/7/旅行/东京"));

        String rewritten = ReflectionTestUtils.invokeMethod(service, "rewriteLegacyPath", legacyPath, 7L);

        assertEquals("storage://3/7/旅行/东京", rewritten);
    }

    @Test
    void shouldFallbackToOwnedAbsolutePathWhenStorageReferenceUnavailable() {
        LegacyDataMigrationService service = new LegacyDataMigrationService(
            userAccountRepository,
            adminUserRepository,
            albumRepository,
            photoRepository,
            commentRepository,
            faceRepository,
            filterOptionRepository,
            personProfileRepository,
            tagRepository,
            storageProviderRepository,
            filterOptionService,
            userStorageService,
            userPathService,
            systemConfigService
        );

        String legacyPath = "/srv/app/data/photos/旅行/东京";
        Path ownedPath = Path.of("/srv/app/data/photos/7/旅行/东京");

        when(userPathService.toOwnedPhotoPath(legacyPath, 7L)).thenReturn(ownedPath);
        when(userPathService.tryBuildStoragePathReference(ownedPath.toString(), 7L))
            .thenReturn(Optional.empty());

        String rewritten = ReflectionTestUtils.invokeMethod(service, "rewriteLegacyPath", legacyPath, 7L);

        assertEquals(ownedPath.toString(), rewritten);
    }

    @Test
    void shouldIncludeAggregateCountsInMigrationSummaryWhenNoDataNeedsRewrite() {
        LegacyDataMigrationService service = new LegacyDataMigrationService(
            userAccountRepository,
            adminUserRepository,
            albumRepository,
            photoRepository,
            commentRepository,
            faceRepository,
            filterOptionRepository,
            personProfileRepository,
            tagRepository,
            storageProviderRepository,
            filterOptionService,
            userStorageService,
            userPathService,
            systemConfigService
        );

        com.photoexhibition.entity.UserAccount owner = new com.photoexhibition.entity.UserAccount();
        owner.setId(1L);
        owner.setUsername("admin");
        owner.setRole(com.photoexhibition.entity.UserRole.SUPER_ADMIN);
        owner.setStatus(com.photoexhibition.entity.UserStatus.ACTIVE);

        when(userAccountRepository.findFirstByRoleOrderByIdAsc(com.photoexhibition.entity.UserRole.SUPER_ADMIN)).thenReturn(Optional.of(owner));
        when(albumRepository.findByUserIdIsNull()).thenReturn(List.of());
        when(albumRepository.findByUserIdIsNotNull()).thenReturn(List.of());
        when(photoRepository.findByUserIdIsNull()).thenReturn(List.of());
        when(photoRepository.findByUserIdIsNotNull()).thenReturn(List.of());
        when(personProfileRepository.findByUserIdIsNull()).thenReturn(List.of());
        when(tagRepository.findByUserIdIsNull()).thenReturn(List.of());
        when(commentRepository.findByUserIdIsNull()).thenReturn(List.of());
        when(faceRepository.findByUserIdIsNull()).thenReturn(List.of());
        when(storageProviderRepository.findAllByOrderByPriorityAscIdAsc()).thenReturn(List.of());
        when(userAccountRepository.findAllIds()).thenReturn(List.of(1L));
        doNothing().when(filterOptionService).updateAllFilterOptions();
        when(userStorageService.refreshStorageUsage(1L)).thenReturn(0L);
        when(systemConfigService.isLegacyMigrationCompleted()).thenReturn(false);

        Map<String, Object> result = service.runMigration();

        assertEquals(true, result.get("success"));
        assertEquals(0, result.get("totalOwnershipMigrationCount"));
        assertEquals(0, result.get("totalPathRewriteCount"));
        assertTrue(result.containsKey("startedAt"));
        assertTrue(result.containsKey("finishedAt"));
        verify(systemConfigService).setLegacyMigrationCompleted(true);
    }

    @Test
    void shouldSkipStartupMigrationWhenCompletionMarkerExists() {
        LegacyDataMigrationService service = new LegacyDataMigrationService(
            userAccountRepository,
            adminUserRepository,
            albumRepository,
            photoRepository,
            commentRepository,
            faceRepository,
            filterOptionRepository,
            personProfileRepository,
            tagRepository,
            storageProviderRepository,
            filterOptionService,
            userStorageService,
            userPathService,
            systemConfigService
        );

        when(systemConfigService.isLegacyMigrationCompleted()).thenReturn(true);

        service.migrateLegacyOwnershipOnStartup();

        verify(systemConfigService).isLegacyMigrationCompleted();
    }

    @Test
    void shouldSkipRunMigrationWhenCompletionMarkerExists() {
        LegacyDataMigrationService service = new LegacyDataMigrationService(
            userAccountRepository,
            adminUserRepository,
            albumRepository,
            photoRepository,
            commentRepository,
            faceRepository,
            filterOptionRepository,
            personProfileRepository,
            tagRepository,
            storageProviderRepository,
            filterOptionService,
            userStorageService,
            userPathService,
            systemConfigService
        );

        when(systemConfigService.isLegacyMigrationCompleted()).thenReturn(true);

        Map<String, Object> result = service.runMigration();

        assertEquals(true, result.get("success"));
        assertEquals(true, result.get("skipped"));
        assertEquals("旧数据迁移已完成，本次跳过", result.get("message"));
        verify(systemConfigService, never()).setLegacyMigrationCompleted(true);
    }
}
