package com.hdv.payment_service.controller;

import com.hdv.payment_service.model.Payment;
import com.hdv.payment_service.repository.PaymentRepository;
import com.hdv.payment_service.service.PaymentService;
import com.hdv.payment_service.service.VNPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/payments/vnpay")
@RequiredArgsConstructor
@Slf4j
public class VNPayController {

    private final VNPayService vnpayService;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @GetMapping("/create-url")
    public ResponseEntity<Map<String, String>> createPaymentUrl(@RequestParam String orderId, HttpServletRequest request) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for orderId: " + orderId));

        String paymentUrl = vnpayService.createPaymentUrl(payment, request);
        return ResponseEntity.ok(Map.of("paymentUrl", paymentUrl, "orderId", orderId));
    }

    @GetMapping("/ipn")
    public ResponseEntity<Map<String, String>> handleVNPayIpn(@RequestParam Map<String, String> params) {
        log.info("Received VNPAY IPN Callback: {}", params);
        Map<String, String> response = new HashMap<>();

        try {
            String vnp_SecureHash = params.remove("vnp_SecureHash");
            params.remove("vnp_SecureHashType");

            // Verify signature
            if (!vnpayService.verifySignature(params, vnp_SecureHash)) {
                log.error("Invalid VNPAY Checksum Signature!");
                response.put("RspCode", "97");
                response.put("Message", "Invalid Checksum");
                return ResponseEntity.ok(response);
            }

            String orderIdStr = params.get("vnp_TxnRef");
            String responseCode = params.get("vnp_ResponseCode");
            String transactionNo = params.get("vnp_TransactionNo");
            String bankCode = params.get("vnp_BankCode");

            Payment payment = paymentRepository.findByOrderId(orderIdStr)
                    .orElseThrow(() -> new RuntimeException("Payment not found for orderId: " + orderIdStr));

            if ("00".equals(responseCode)) {
                paymentService.handlePaymentSuccess(payment.getOrderId());
                response.put("RspCode", "00");
                response.put("Message", "Confirm Success");
            } else {
                paymentService.handlePaymentFailed(payment.getOrderId(), "VNPAY Error Code: " + responseCode);
                response.put("RspCode", "00");
                response.put("Message", "Confirm Success");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing VNPAY IPN", e);
            response.put("RspCode", "99");
            response.put("Message", "Unknown Error");
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/return")
    public ResponseEntity<Map<String, String>> handleVNPayReturn(@RequestParam Map<String, String> params) {
        log.info("Received VNPAY Return URL Callback: {}", params);
        String responseCode = params.get("vnp_ResponseCode");
        String orderIdStr = params.get("vnp_TxnRef");
        
        if (orderIdStr != null && !orderIdStr.isBlank()) {
            try {
                if ("00".equals(responseCode)) {
                    paymentService.handlePaymentSuccess(orderIdStr);
                    log.info("Processed payment success via return callback for orderId: {}", orderIdStr);
                } else {
                    paymentService.handlePaymentFailed(orderIdStr, "VNPAY Return Error Code: " + responseCode);
                    log.warn("Processed payment failure via return callback for orderId: {}", orderIdStr);
                }
            } catch (Exception e) {
                log.warn("Error processing payment in return callback: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
                "orderId", orderIdStr != null ? orderIdStr : "unknown",
                "status", "00".equals(responseCode) ? "SUCCESS" : "FAILED",
                "message", "00".equals(responseCode) ? "Payment confirmed successfully" : "Payment failed"
        ));
    }
}
