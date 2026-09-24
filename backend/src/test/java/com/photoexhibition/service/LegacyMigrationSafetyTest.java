package com.photoexhibition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.AdminUser;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class LegacyMigrationSafetyTest {

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

    @InjectMocks private LegacyDataMigrationService migrationService;

    @TempDir Path tempDir;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void singleUserModeKeepsExistingPhotoDirectoryLayout() {
        when(systemConfigService.isMultiUserEnabled()).thenReturn(false);

        Map<String, Integer> result = ReflectionTestUtils.invokeMethod(
            migrationService,
            "migrateLegacyFileStructure",
            new UserAccount()
        );

        assertNotNull(result);
        assertEquals(0, result.get("movedTopLevelEntryCount"));
        assertEquals(0, result.get("rewrittenAlbumPathCount"));
        verify(systemConfigService).isMultiUserEnabled();
        verifyNoInteractions(userPathService);
    }

    @Test
    void movedFileIsRestoredWhenDatabaseTransactionRollsBack() throws Exception {
        Path source = Files.writeString(tempDir.resolve("source.txt"), "photo");
        Path target = tempDir.resolve("nested/target.txt");
        Files.createDirectories(target.getParent());
        TransactionSynchronizationManager.initSynchronization();

        ReflectionTestUtils.invokeMethod(migrationService, "moveWithRollback", source, target);
        assertFalse(Files.exists(source));
        assertTrue(Files.exists(target));

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        assertTrue(Files.exists(source));
        assertFalse(Files.exists(target));
    }

    @Test
    void migrationRunsBeforeAsynchronousInitialScan() throws Exception {
        Method migration = LegacyDataMigrationService.class
            .getMethod("migrateLegacyOwnershipOnStartup");
        Method scan = PhotoScanService.class
            .getMethod("initializeScanAfterMigration");

        assertEquals(Ordered.HIGHEST_PRECEDENCE, migration.getAnnotation(Order.class).value());
        assertEquals(Ordered.LOWEST_PRECEDENCE, scan.getAnnotation(Order.class).value());
        assertNotNull(scan.getAnnotation(Async.class));
    }

    @Test
    void legacyAdminKeepsItsIdInTheNewAccountTable() {
        AdminUser legacyAdmin = new AdminUser();
        legacyAdmin.setId(7L);
        legacyAdmin.setUsername("admin");
        legacyAdmin.setPassword("encoded");
        legacyAdmin.setEnabled(true);
        when(userAccountRepository.findFirstByRoleOrderByIdAsc(any())).thenReturn(Optional.empty());
        when(userAccountRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(legacyAdmin));
        when(userAccountRepository.existsById(7L)).thenReturn(false);
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount migrated = ReflectionTestUtils.invokeMethod(migrationService, "ensureOwnerAccount");

        assertNotNull(migrated);
        assertEquals(7L, migrated.getId());
    }
}
