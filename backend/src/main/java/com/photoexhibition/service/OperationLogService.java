package com.photoexhibition.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.OperationLog;
import com.photoexhibition.entity.OperationType;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;
    private final ObjectMapper objectMapper;
    private final UserPathService userPathService;

    @Transactional
    public void log(UserAccount operator,
                    OperationType operationType,
                    String targetType,
                    Long targetId,
                    String targetPath,
                    Object detail,
                    String ipAddress) {
        try {
            OperationLog logRecord = new OperationLog();
            logRecord.setUserId(operator != null ? operator.getId() : null);
            logRecord.setOperatorUsername(operator != null ? operator.getUsername() : null);
            logRecord.setOperationType(operationType);
            logRecord.setTargetType(targetType);
            logRecord.setTargetId(targetId);
            logRecord.setTargetPath(targetPath);
            logRecord.setDetailJson(writeDetailJson(detail));
            logRecord.setIpAddress(ipAddress);
            operationLogRepository.save(logRecord);
        } catch (Exception e) {
            log.warn("记录操作日志失败: type={}, targetType={}, targetPath={}", operationType, targetType, targetPath, e);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRecentLogs(UserAccount currentUser) {
        List<OperationLog> logs;
        if (currentUser.getRole() == UserRole.SUPER_ADMIN) {
            logs = operationLogRepository.findAll(
                    PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
        } else {
            logs = operationLogRepository.findTop100ByUserIdOrderByCreatedAtDesc(currentUser.getId());
        }

        return logs.stream()
            .map(this::toMap)
            .collect(Collectors.toList());
    }

    private String writeDetailJson(Object detail) {
        if (detail == null) {
            return null;
        }
        if (detail instanceof String) {
            return (String) detail;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            return String.valueOf(detail);
        }
    }

    private Map<String, Object> toMap(OperationLog logRecord) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", logRecord.getId());
        resp.put("userId", logRecord.getUserId());
        resp.put("operatorUsername", logRecord.getOperatorUsername());
        resp.put("operationType", logRecord.getOperationType() != null ? logRecord.getOperationType().name() : null);
        resp.put("targetType", logRecord.getTargetType());
        resp.put("targetId", logRecord.getTargetId());
        resp.put("targetPath", sanitizeDisplayPath(logRecord.getTargetPath()));
        resp.put("detailJson", sanitizeDetailJson(logRecord.getDetailJson()));
        resp.put("ipAddress", logRecord.getIpAddress());
        resp.put("createdAt", logRecord.getCreatedAt());
        return resp;
    }

    private String sanitizeDisplayPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return rawPath;
        }
        String displayPath = userPathService.toDisplayPath(rawPath, true);
        if (!rawPath.equals(displayPath)) {
            return displayPath;
        }
        try {
            if (rawPath.startsWith("/") || rawPath.matches("^[A-Za-z]:\\\\.*")) {
                Path fileName = Path.of(rawPath).getFileName();
                return fileName != null ? fileName.toString() : rawPath;
            }
        } catch (Exception ignored) {
        }
        return rawPath;
    }

    private String sanitizeDetailJson(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return detailJson;
        }
        try {
            Object value = objectMapper.readValue(detailJson, new TypeReference<Object>() {});
            return objectMapper.writeValueAsString(sanitizeJsonValue(value));
        } catch (Exception ignored) {
            return sanitizePotentialPathText(detailJson);
        }
    }

    private Object sanitizeJsonValue(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> sanitized.put(String.valueOf(key), sanitizeJsonValue(nestedValue)));
            return sanitized;
        }
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            List<Object> sanitized = new ArrayList<>(list.size());
            list.forEach(item -> sanitized.add(sanitizeJsonValue(item)));
            return sanitized;
        }
        if (value instanceof String) {
            return sanitizePotentialPathText((String) value);
        }
        return value;
    }

    private String sanitizePotentialPathText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (looksLikePath(text)) {
            return sanitizeDisplayPath(text);
        }
        return text;
    }

    private boolean looksLikePath(String text) {
        return text.startsWith("storage://")
                || text.startsWith("/")
                || text.matches("^[A-Za-z]:\\\\.*");
    }
}
