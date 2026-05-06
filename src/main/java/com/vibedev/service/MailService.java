package com.vibedev.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public MailService(
            @Value("${app.mail.from}") String from,
            JavaMailSender mailSender) {
        this.from = from;
        this.mailSender = mailSender;
    }

    @Async
    public void sendVerifyEmail(String to, String username, String verifyUrl) {
        String subject = "验证您的 vibeDev 账号";
        String content = buildVerifyEmailTemplate(username, verifyUrl);
        send(to, subject, content);
    }

    @Async
    public void sendResetPasswordEmail(String to, String username, String resetUrl) {
        String subject = "重置您的 vibeDev 密码";
        String content = buildResetPasswordTemplate(username, resetUrl);
        send(to, subject, content);
    }

    @Async
    public void sendNotificationEmail(String to, String subject, String body) {
        send(to, subject, body);
    }

    private void send(String to, String subject, String content) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);
            mailSender.send(message);
            log.info("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private String buildVerifyEmailTemplate(String username, String verifyUrl) {
        return """
                <div style="max-width:600px;margin:0 auto;font-family:sans-serif;">
                    <h2>欢迎加入 vibeDev！</h2>
                    <p>Hi %s，</p>
                    <p>请点击下方按钮验证您的邮箱地址（链接 1 小时内有效）：</p>
                    <a href="%s" style="display:inline-block;padding:12px 24px;background:#6366f1;color:#fff;text-decoration:none;border-radius:6px;">
                        验证邮箱
                    </a>
                    <p style="margin-top:20px;color:#999;">如果按钮无法点击，请复制以下链接到浏览器：<br/>%s</p>
                </div>
                """.formatted(username, verifyUrl, verifyUrl);
    }

    private String buildResetPasswordTemplate(String username, String resetUrl) {
        return """
                <div style="max-width:600px;margin:0 auto;font-family:sans-serif;">
                    <h2>重置密码</h2>
                    <p>Hi %s，</p>
                    <p>请点击下方按钮重置您的密码（链接 30 分钟内有效）：</p>
                    <a href="%s" style="display:inline-block;padding:12px 24px;background:#6366f1;color:#fff;text-decoration:none;border-radius:6px;">
                        重置密码
                    </a>
                    <p style="margin:20px 0;color:#e74c3c;">如果这不是您的操作，请忽略此邮件。</p>
                    <p style="color:#999;">链接：%s</p>
                </div>
                """.formatted(username, resetUrl, resetUrl);
    }
}
