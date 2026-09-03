package com.hdv.payment_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "vnpay")
@Data
public class VNPayProperties {
    private String tmnCode = "CGXZLS0Z";
    private String hashSecret = "XNBCJFAKAZQSGZSJFYNXGKJNMGDLSUAP";
    private String url = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private String returnUrl = "http://localhost:8888/api/v1/payments/vnpay/return";
    private String version = "2.1.0";
    private String command = "pay";
    private String orderType = "other";
}
