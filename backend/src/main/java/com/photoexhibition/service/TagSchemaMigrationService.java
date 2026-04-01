package com.photoexhibition.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TagSchemaMigrationService {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    @Order(-100)
    public void ensureTagUserScopedUniqueIndex() {
        try {
            List<Map<String, Object>> indexes = jdbcTemplate.queryForList("SHOW INDEX FROM tag");
            Map<String, List<Map<String, Object>>> grouped = indexes.stream()
                .collect(Collectors.groupingBy(row -> String.valueOf(row.get("Key_name"))));

            for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
                String keyName = entry.getKey();
                if ("PRIMARY".equalsIgnoreCase(keyName)) {
                    continue;
                }
                List<String> columns = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(row -> ((Number) row.get("Seq_in_index")).intValue()))
                    .map(row -> String.valueOf(row.get("Column_name")))
                    .collect(Collectors.toList());
                boolean unique = entry.getValue().stream()
                    .findFirst()
                    .map(row -> ((Number) row.get("Non_unique")).intValue() == 0)
                    .orElse(false);

                if (unique && columns.size() == 1 && "name".equalsIgnoreCase(columns.get(0))) {
                    jdbcTemplate.execute("ALTER TABLE tag DROP INDEX " + keyName);
                    log.info("已移除 tag 表旧的全局唯一索引: {}", keyName);
                }
            }

            boolean hasScopedUniqueIndex = grouped.entrySet().stream().anyMatch(entry -> {
                List<String> columns = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(row -> ((Number) row.get("Seq_in_index")).intValue()))
                    .map(row -> String.valueOf(row.get("Column_name")))
                    .collect(Collectors.toList());
                boolean unique = entry.getValue().stream()
                    .findFirst()
                    .map(row -> ((Number) row.get("Non_unique")).intValue() == 0)
                    .orElse(false);
                return unique && columns.size() == 2
                    && "user_id".equalsIgnoreCase(columns.get(0))
                    && "name".equalsIgnoreCase(columns.get(1));
            });

            if (!hasScopedUniqueIndex) {
                jdbcTemplate.execute("ALTER TABLE tag ADD UNIQUE INDEX uk_tag_user_name (user_id, name)");
                log.info("已为 tag 表创建按用户隔离的唯一索引 uk_tag_user_name(user_id, name)");
            }
        } catch (Exception e) {
            log.warn("调整 tag 表唯一索引失败，将继续使用当前结构: {}", e.getMessage());
        }
    }
}
