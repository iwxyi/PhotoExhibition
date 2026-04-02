package com.photoexhibition.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class EmailSenderService {

    private static final String DEFAULT_SYSTEM_NAME = "Photo Exhibition";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EmailConfigService emailConfigService;
    private final UserPathService userPathService;

    public EmailSendResult sendTestEmail(String toAddress) {
        EmailConfigService.EmailResolvedSettings settings = emailConfigService.getResolvedSettings();
        String recipient = toAddress == null || toAddress.trim().isEmpty() ? settings.getTestRecipient() : toAddress.trim();
        return sendEmail(recipient, "Photo Exhibition 邮件配置测试", buildTestEmailBody(settings), false);
    }

    public EmailSendResult sendEmail(String toAddress, String subject, String content, boolean html) {
        EmailConfigService.EmailResolvedSettings settings = emailConfigService.getResolvedSettings();
        if (!settings.isEnabled()) {
            throw new RuntimeException("邮件服务未启用");
        }
        validateSettings(settings);
        String recipient = toAddress == null || toAddress.trim().isEmpty() ? settings.getTestRecipient() : toAddress.trim();
        if (recipient == null || recipient.isBlank()) {
            throw new RuntimeException("收件人不能为空");
        }
        String normalizedSubject = subject == null || subject.trim().isEmpty() ? "Photo Exhibition 通知" : subject.trim();
        String normalizedContent = content == null || content.trim().isEmpty() ? "这是一封来自 Photo Exhibition 的邮件。" : content;

        try {
            JavaMailSenderImpl sender = buildSender(settings);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(recipient);
            helper.setSubject(normalizedSubject);
            helper.setText(normalizedContent, html);
            if (settings.getFromAddress() != null && !settings.getFromAddress().isBlank()) {
                if (settings.getFromName() != null && !settings.getFromName().isBlank()) {
                    helper.setFrom(settings.getFromAddress(), settings.getFromName());
                } else {
                    helper.setFrom(settings.getFromAddress());
                }
            }
            if (settings.getReplyTo() != null && !settings.getReplyTo().isBlank()) {
                helper.setReplyTo(settings.getReplyTo());
            }
            sender.send(message);
            return EmailSendResult.builder()
                .success(true)
                .providerType(settings.getProviderType().name())
                .recipient(recipient)
                .message("邮件发送成功")
                .build();
        } catch (Exception e) {
            throw new RuntimeException("邮件发送失败: " + userPathService.sanitizeVisibleText(e.getMessage()), e);
        }
    }

    public List<Map<String, Object>> listTemplates() {
        List<Map<String, Object>> templates = new ArrayList<>();
        for (EmailTemplateDefinition definition : buildTemplateDefinitions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", definition.getKey());
            item.put("name", definition.getName());
            item.put("description", definition.getDescription());
            item.put("html", definition.isHtml());
            item.put("fields", definition.getFields());
            item.put("sampleVariables", definition.getSampleVariables());
            templates.add(item);
        }
        return templates;
    }

    public Map<String, Object> previewTemplate(String templateKey, Map<String, Object> variables) {
        EmailTemplateDefinition definition = resolveTemplateDefinition(templateKey);
        Map<String, String> resolvedVariables = mergeTemplateVariables(definition, variables);
        RenderedEmail renderedEmail = definition.render(resolvedVariables);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("templateKey", definition.getKey());
        resp.put("templateName", definition.getName());
        resp.put("subject", renderedEmail.getSubject());
        resp.put("content", renderedEmail.getContent());
        resp.put("html", renderedEmail.isHtml());
        resp.put("variables", resolvedVariables);
        return resp;
    }

    public EmailSendResult sendTemplateEmail(String toAddress, String templateKey, Map<String, Object> variables) {
        Map<String, Object> preview = previewTemplate(templateKey, variables);
        return sendEmail(
            toAddress,
            String.valueOf(preview.get("subject")),
            String.valueOf(preview.get("content")),
            Boolean.TRUE.equals(preview.get("html"))
        );
    }

    private JavaMailSenderImpl buildSender(EmailConfigService.EmailResolvedSettings settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.getHost());
        sender.setPort(settings.getPort());
        sender.setUsername(settings.getUsername());
        sender.setPassword(settings.getPassword());
        sender.setProtocol(settings.getProtocol() == null || settings.getProtocol().isBlank() ? "smtp" : settings.getProtocol());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", sender.getProtocol());
        props.put("mail.smtp.auth", String.valueOf(settings.getUsername() != null && !settings.getUsername().isBlank()));
        props.put("mail.smtp.starttls.enable", String.valueOf(settings.isStarttlsEnabled()));
        props.put("mail.smtp.ssl.enable", String.valueOf(settings.isSslEnabled()));
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }

    private void validateSettings(EmailConfigService.EmailResolvedSettings settings) {
        if (settings.getHost() == null || settings.getHost().isBlank()) {
            throw new RuntimeException("邮件主机未配置");
        }
        if (settings.getPort() <= 0) {
            throw new RuntimeException("邮件端口未配置");
        }
        if (settings.getFromAddress() == null || settings.getFromAddress().isBlank()) {
            throw new RuntimeException("发件邮箱未配置");
        }
    }

    private String buildTestEmailBody(EmailConfigService.EmailResolvedSettings settings) {
        return "这是一封来自 Photo Exhibition 的测试邮件。\n\n"
            + "发送时间：" + LocalDateTime.now() + "\n"
            + "邮件服务：" + settings.getProviderType().name() + "\n"
            + "SMTP 主机：" + settings.getHost() + ":" + settings.getPort() + "\n"
            + "发件人：" + settings.getFromAddress() + "\n";
    }

    private EmailTemplateDefinition resolveTemplateDefinition(String templateKey) {
        String normalizedKey = templateKey == null ? "" : templateKey.trim().toUpperCase(Locale.ROOT);
        return buildTemplateDefinitions().stream()
            .filter(item -> item.getKey().equals(normalizedKey))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("邮件模板不存在: " + templateKey));
    }

    private Map<String, String> mergeTemplateVariables(EmailTemplateDefinition definition, Map<String, Object> variables) {
        Map<String, String> resolved = new LinkedHashMap<>();
        definition.getSampleVariables().forEach((key, value) -> resolved.put(key, String.valueOf(value == null ? "" : value)));
        resolved.put("systemName", DEFAULT_SYSTEM_NAME);
        resolved.put("generatedAt", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        if (variables != null) {
            variables.forEach((key, value) -> {
                if (key != null) {
                    resolved.put(key, value == null ? "" : String.valueOf(value).trim());
                }
            });
        }
        resolved.replaceAll((key, value) -> value == null ? "" : value);
        return resolved;
    }

    private List<EmailTemplateDefinition> buildTemplateDefinitions() {
        return List.of(
            EmailTemplateDefinition.builder()
                .key("WELCOME")
                .name("欢迎邮件")
                .description("用于新用户注册、创建账号或开通服务后的欢迎通知。")
                .html(true)
                .fields(List.of(
                    field("recipientName", "收件人称呼", "例如 张三"),
                    field("noticeSummary", "欢迎语", "感谢加入 Photo Exhibition"),
                    field("actionUrl", "引导链接", "https://example.com/login"),
                    field("actionText", "按钮文案", "立即开始"),
                    field("signName", "签名", "Photo Exhibition 团队")
                ))
                .sampleVariables(Map.of(
                    "recipientName", "摄影师朋友",
                    "noticeSummary", "感谢加入 Photo Exhibition，系统已经为你准备好基础空间与管理后台。",
                    "actionUrl", "https://example.com/login",
                    "actionText", "立即开始",
                    "signName", "Photo Exhibition 团队"
                ))
                .renderer(variables -> renderHtmlEmail(
                    "欢迎加入 " + valueOf(variables, "systemName"),
                    valueOf(variables, "recipientName") + "，你好！",
                    valueOf(variables, "noticeSummary"),
                    valueOf(variables, "actionUrl"),
                    valueOf(variables, "actionText"),
                    "发送时间：" + valueOf(variables, "generatedAt") + " · 签名：" + valueOf(variables, "signName")
                ))
                .build(),
            EmailTemplateDefinition.builder()
                .key("EMAIL_VERIFICATION")
                .name("邮箱验证码")
                .description("用于邮箱绑定、邮箱验证等短时效通知。")
                .html(false)
                .fields(List.of(
                    field("recipientName", "收件人称呼", "例如 张三"),
                    field("code", "验证码", "例如 123456"),
                    field("expireMinutes", "有效期（分钟）", "例如 10"),
                    field("actionLabel", "业务动作", "例如 邮箱验证")
                ))
                .sampleVariables(Map.of(
                    "recipientName", "摄影师朋友",
                    "code", "123456",
                    "expireMinutes", "10",
                    "actionLabel", "邮箱验证"
                ))
                .renderer(variables -> renderTextEmail(
                    valueOf(variables, "systemName") + " " + valueOf(variables, "actionLabel"),
                    valueOf(variables, "recipientName") + "，你好！\n\n"
                        + "你正在进行 " + valueOf(variables, "systemName") + " 的" + valueOf(variables, "actionLabel") + "操作。\n"
                        + "验证码：" + valueOf(variables, "code") + "\n"
                        + "有效期：" + valueOf(variables, "expireMinutes") + " 分钟\n\n"
                        + "若非本人操作，请忽略本邮件。"
                ))
                .build(),
            EmailTemplateDefinition.builder()
                .key("PASSWORD_RESET")
                .name("密码重置")
                .description("用于密码找回、重置密码等安全流程。")
                .html(false)
                .fields(List.of(
                    field("recipientName", "收件人称呼", "例如 张三"),
                    field("code", "验证码", "例如 654321"),
                    field("expireMinutes", "有效期（分钟）", "例如 15"),
                    field("actionLabel", "业务动作", "例如 重置密码")
                ))
                .sampleVariables(Map.of(
                    "recipientName", "摄影师朋友",
                    "code", "654321",
                    "expireMinutes", "15",
                    "actionLabel", "重置密码"
                ))
                .renderer(variables -> renderTextEmail(
                    valueOf(variables, "systemName") + " " + valueOf(variables, "actionLabel"),
                    valueOf(variables, "recipientName") + "，你好！\n\n"
                        + "你发起了 " + valueOf(variables, "systemName") + " 的" + valueOf(variables, "actionLabel") + "流程。\n"
                        + "验证码：" + valueOf(variables, "code") + "\n"
                        + "有效期：" + valueOf(variables, "expireMinutes") + " 分钟\n\n"
                        + "如非本人操作，请尽快检查账号安全。"
                ))
                .build(),
            EmailTemplateDefinition.builder()
                .key("SYSTEM_NOTICE")
                .name("系统通知")
                .description("用于发送版本发布、功能变更、公告等通用通知。")
                .html(true)
                .fields(List.of(
                    field("recipientName", "收件人称呼", "例如 张三"),
                    field("noticeTitle", "通知标题", "例如 新版本上线"),
                    field("noticeSummary", "通知摘要", "一句话概述本次通知"),
                    field("noticeContent", "通知正文", "可填写多行说明"),
                    field("actionUrl", "详情链接", "https://example.com/notice"),
                    field("actionText", "按钮文案", "查看详情")
                ))
                .sampleVariables(Map.of(
                    "recipientName", "摄影师朋友",
                    "noticeTitle", "新版本上线",
                    "noticeSummary", "后台管理与 AI 模型管理能力已升级。",
                    "noticeContent", "你现在可以在超管后台集中查看模型状态、执行重建任务，并继续维护用户与存储配置。",
                    "actionUrl", "https://example.com/notice",
                    "actionText", "查看详情"
                ))
                .renderer(variables -> renderHtmlEmail(
                    valueOf(variables, "noticeTitle"),
                    valueOf(variables, "recipientName") + "，你好！",
                    valueOf(variables, "noticeSummary"),
                    valueOf(variables, "actionUrl"),
                    valueOf(variables, "actionText"),
                    valueOf(variables, "noticeContent")
                ))
                .build(),
            EmailTemplateDefinition.builder()
                .key("MAINTENANCE_NOTICE")
                .name("维护通知")
                .description("用于发送维护窗口、停机升级、恢复提醒等运维通知。")
                .html(true)
                .fields(List.of(
                    field("recipientName", "收件人称呼", "例如 张三"),
                    field("noticeTitle", "维护标题", "例如 系统维护通知"),
                    field("maintenanceWindow", "维护时间窗", "例如 2026-04-03 01:00 - 03:00"),
                    field("noticeContent", "影响说明", "说明维护影响范围"),
                    field("actionUrl", "状态页链接", "https://example.com/status"),
                    field("actionText", "按钮文案", "查看状态页")
                ))
                .sampleVariables(Map.of(
                    "recipientName", "摄影师朋友",
                    "noticeTitle", "系统维护通知",
                    "maintenanceWindow", "2026-04-03 01:00 - 03:00",
                    "noticeContent", "维护期间上传、AI 分析与后台配置保存可能短暂受影响，请提前安排测试时间。",
                    "actionUrl", "https://example.com/status",
                    "actionText", "查看状态页"
                ))
                .renderer(variables -> renderHtmlEmail(
                    valueOf(variables, "noticeTitle"),
                    valueOf(variables, "recipientName") + "，你好！",
                    "计划维护时间：" + valueOf(variables, "maintenanceWindow"),
                    valueOf(variables, "actionUrl"),
                    valueOf(variables, "actionText"),
                    valueOf(variables, "noticeContent")
                ))
                .build()
        );
    }

    private TemplateField field(String key, String label, String placeholder) {
        return TemplateField.builder()
            .key(key)
            .label(label)
            .placeholder(placeholder)
            .build();
    }

    private RenderedEmail renderTextEmail(String subject, String content) {
        return new RenderedEmail(subject, content, false);
    }

    private RenderedEmail renderHtmlEmail(String subject,
                                          String greeting,
                                          String summary,
                                          String actionUrl,
                                          String actionText,
                                          String footer) {
        StringBuilder content = new StringBuilder();
        content.append("<div style=\"font-family:Arial,sans-serif;line-height:1.7;color:#111827;\">")
            .append("<h2 style=\"margin-bottom:12px;\">").append(escapeHtml(subject)).append("</h2>")
            .append("<p>").append(escapeHtml(greeting)).append("</p>")
            .append("<p>").append(escapeHtml(summary)).append("</p>");
        if (!isBlank(actionUrl) && !isBlank(actionText)) {
            content.append("<p style=\"margin:20px 0;\">")
                .append("<a href=\"").append(escapeAttribute(actionUrl)).append("\" style=\"display:inline-block;padding:10px 16px;background:#2563eb;color:#ffffff;text-decoration:none;border-radius:8px;\">")
                .append(escapeHtml(actionText))
                .append("</a></p>");
        }
        if (!isBlank(footer)) {
            content.append("<p style=\"color:#6b7280;font-size:12px;white-space:pre-wrap;\">")
                .append(escapeHtml(footer))
                .append("</p>");
        }
        content.append("</div>");
        return new RenderedEmail(subject, content.toString(), true);
    }

    private static String valueOf(Map<String, String> variables, String key) {
        return Objects.toString(variables.get(key), "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("\n", "<br/>");
    }

    private String escapeAttribute(String value) {
        return escapeHtml(value).replace("`", "&#96;");
    }

    @Data
    @Builder
    public static class EmailSendResult {
        private boolean success;
        private String providerType;
        private String recipient;
        private String message;
    }

    @Data
    @Builder
    private static class EmailTemplateDefinition {
        private String key;
        private String name;
        private String description;
        private boolean html;
        private List<TemplateField> fields;
        private Map<String, Object> sampleVariables;
        private TemplateRenderer renderer;

        private RenderedEmail render(Map<String, String> variables) {
            return renderer.render(variables);
        }
    }

    @Data
    @Builder
    public static class TemplateField {
        private String key;
        private String label;
        private String placeholder;
    }

    @Data
    private static class RenderedEmail {
        private final String subject;
        private final String content;
        private final boolean html;
    }

    @FunctionalInterface
    private interface TemplateRenderer {
        RenderedEmail render(Map<String, String> variables);
    }
}
