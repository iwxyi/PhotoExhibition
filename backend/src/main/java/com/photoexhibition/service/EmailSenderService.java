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
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class EmailSenderService {

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

    @Data
    @Builder
    public static class EmailSendResult {
        private boolean success;
        private String providerType;
        private String recipient;
        private String message;
    }
}
