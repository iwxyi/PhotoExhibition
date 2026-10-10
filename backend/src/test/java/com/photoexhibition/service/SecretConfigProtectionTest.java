package com.photoexhibition.service;

import com.photoexhibition.entity.SystemConfig;
import com.photoexhibition.repository.SystemConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import javax.persistence.EntityManager;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecretConfigProtectionTest {
    @TempDir Path directory;
    SecretConfigCodec codec() { return new SecretConfigCodec(directory.resolve("master.key").toString()); }

    @Test void encryptsWithRandomNonceAndSurvivesRestart() {
        String first = codec().encode("payment_private_key", "private-value");
        String second = codec().encode("payment_private_key", "private-value");
        assertNotEquals(first, second);
        assertFalse(first.contains("private-value"));
        assertEquals("private-value", codec().decode("payment_private_key", first));
        assertThrows(IllegalStateException.class, () -> codec().decode("email_password", first));
    }
    @Test void failsClosedForMissingWrongOrTamperedKey() throws Exception {
        String encoded = codec().encode("email_password", "secret");
        assertThrows(IllegalStateException.class, () -> codec().decode("email_password", encoded.substring(0, encoded.length() - 4) + "AAAA"));
        Files.delete(directory.resolve("master.key"));
        assertThrows(IllegalStateException.class, () -> codec().decode("email_password", encoded));
        assertFalse(Files.exists(directory.resolve("master.key")));
        codec().encode("email_password", "different-key");
        assertThrows(IllegalStateException.class, () -> codec().decode("email_password", encoded));
    }
    @Test void supportsLegacyAndLeavesPublicValuesUnchanged() {
        assertEquals("legacy", codec().decode("payment_private_key", "legacy"));
        assertEquals("public", codec().encode("payment_public_key", "public"));
        assertEquals("", codec().encode("email_password", ""));
    }
    @Test void migratesLegacyAndMasksAllPublicConfigResponses() {
        SystemConfigRepository repository = mock(SystemConfigRepository.class);
        SystemConfigService service = new SystemConfigService(repository);
        ReflectionTestUtils.setField(service, "secretConfigCodec", codec());
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));
        SystemConfig record = new SystemConfig();
        record.setConfigKey("payment_private_key"); record.setConfigValue("legacy-secret");
        when(repository.findAll()).thenReturn(Arrays.asList(record));
        when(repository.findByConfigKey(record.getConfigKey())).thenReturn(Optional.of(record));
        service.encryptLegacySecrets();
        assertTrue(record.getConfigValue().startsWith(SecretConfigCodec.PREFIX));
        assertEquals("legacy-secret", service.getConfigValue(record.getConfigKey(), ""));
        assertEquals("****", service.getAllConfigs().get(record.getConfigKey()));
        service.encryptLegacySecrets();
        verify(repository, times(1)).save(record);
        service.setConfigValue(record.getConfigKey(), "replacement", "private");
        assertEquals("replacement", service.getConfigValue(record.getConfigKey(), ""));
    }
    @Test void blankMaskedAndAbsentUpdatesPreserveAndClearIsExplicit() {
        SuperAdminService service = mock(SuperAdminService.class, CALLS_REAL_METHODS);
        Map<String, Object> request = new HashMap<>();
        request.put("paymentPrivateKey", ""); request.put("emailPassword", "****");
        Map<?, ?> clean = ReflectionTestUtils.invokeMethod(service, "prepareSecretUpdate", request);
        assertFalse(clean.containsKey("paymentPrivateKey")); assertFalse(clean.containsKey("emailPassword"));
        request.put("clearSecrets", Arrays.asList("paymentPrivateKey"));
        clean = ReflectionTestUtils.invokeMethod(service, "prepareSecretUpdate", request);
        assertEquals("", clean.get("paymentPrivateKey"));
        request.put("paymentPrivateKey", "new-key");
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(service, "prepareSecretUpdate", request));
        request.put("clearSecrets", Arrays.asList("paymentPublicKey"));
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(service, "prepareSecretUpdate", request));
    }
    @Test void operationLogsRedactNestedSecretFieldsOnWriteAndRead() {
        OperationLogService service = mock(OperationLogService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "objectMapper", new com.fasterxml.jackson.databind.ObjectMapper());
        Map<String, Object> detail = Collections.singletonMap("payload",
            Collections.singletonMap("paymentPrivateKey", "must-not-leak"));
        String written = ReflectionTestUtils.invokeMethod(service, "writeDetailJson", detail);
        assertFalse(written.contains("must-not-leak"));
        String legacy = "{\"payload\":{\"paymentPrivateKey\":\"must-not-leak\"}}";
        String displayed = ReflectionTestUtils.invokeMethod(service, "sanitizeDetailJson", legacy);
        assertFalse(displayed.contains("must-not-leak"));
        SuperAdminService admin = mock(SuperAdminService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(admin, "objectMapper", new com.fasterxml.jackson.databind.ObjectMapper());
        String adminDisplay = ReflectionTestUtils.invokeMethod(admin, "sanitizeDetailJson", legacy);
        assertFalse(adminDisplay.contains("must-not-leak"));
    }
    @Test void migratesHistoricalSettingsAuditWithoutRemovingOtherDetails() {
        com.photoexhibition.repository.OperationLogRepository repository = mock(com.photoexhibition.repository.OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository, new com.fasterxml.jackson.databind.ObjectMapper(), null);
        com.photoexhibition.entity.OperationLog record = new com.photoexhibition.entity.OperationLog();
        record.setDetailJson("{\"action\":\"updateSettings\",\"payload\":{\"paymentPrivateKey\":\"legacy-private\",\"paymentAppId\":\"public-id\"}}");
        when(repository.findByOperationTypeAndTargetType(eq(com.photoexhibition.entity.OperationType.CONFIG_UPDATE), eq("SYSTEM_SETTINGS"), any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(Arrays.asList(record)));
        service.redactLegacySettingsLogs();
        assertFalse(record.getDetailJson().contains("legacy-private"));
        assertTrue(record.getDetailJson().contains("public-id"));
        service.redactLegacySettingsLogs();
        verify(repository, times(1)).saveAll(any());
    }
}
