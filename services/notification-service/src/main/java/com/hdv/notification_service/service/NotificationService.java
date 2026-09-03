package com.hdv.notification_service.service;

import com.hdv.common.dto.ConfirmedItemDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final QRCodeService qrCodeService;
    private final EmailService emailService;

    public void processOrderConfirmed(String orderId, String email, String eventId,
                                       List<ConfirmedItemDto> items, long totalPrice) {
        log.info("Processing order.confirmed for order {} to email {}", orderId, email);

        if (email == null || email.isBlank()) {
            log.warn("Email is null or empty for order {}, skipping notification.", orderId);
            return;
        }

        try {
            // Build ticket summary cho QR code
            StringBuilder ticketSummary = new StringBuilder();
            if (items != null) {
                for (ConfirmedItemDto item : items) {
                    ticketSummary.append(item.getTicketTypeName())
                                 .append(" x")
                                 .append(item.getQuantity())
                                 .append("\n");
                }
            }

            // Generate QR Code with order verification info
            String qrData = "Order ID: " + orderId + "\nEvent ID: " + eventId + "\n" + ticketSummary;
            byte[] qrCode = qrCodeService.generateQRCodeImage(qrData, 300, 300);

            // Prepare dynamic variables for Thymeleaf
            Context context = new Context();
            context.setVariable("orderId", orderId);
            context.setVariable("eventId", eventId);
            context.setVariable("items", items);
            context.setVariable("totalPrice", totalPrice);

            // Send Email
            emailService.sendEmailWithQR(email, "Xác nhận đặt vé thành công", context, qrCode);

        } catch (Exception e) {
            log.error("Error processing notification for order {}: {}", orderId, e.getMessage(), e);
        }
    }
}
