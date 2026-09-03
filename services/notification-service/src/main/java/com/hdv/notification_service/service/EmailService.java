package com.hdv.notification_service.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public void sendEmailWithQR(String to, String subject, Context context, byte[] qrCodeBytes) {
        log.info("Sending confirmation email to {}", to);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // true indicates multipart message for inline images
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            
            // Process HTML template
            String htmlContent = templateEngine.process("ticket-confirmation", context);
            helper.setText(htmlContent, true);

            // Add the inline QR code inline
            if (qrCodeBytes != null && qrCodeBytes.length > 0) {
                helper.addInline("qrcode", new ByteArrayResource(qrCodeBytes), "image/png");
            }

            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}. Error: {}", to, e.getMessage(), e);
            // According to spec, log error, but do not throw to prevent infinite Kafka looping
        } catch (Exception e) {
            log.error("Unexpected error sending email: {}", e.getMessage(), e);
        }
    }
}
