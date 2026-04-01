package com.photoexhibition.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_plan_order")
@Data
public class UserPlanOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 64)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "vip_plan_id", nullable = false)
    private Long vipPlanId;

    @Column(name = "amount_fen", nullable = false)
    private Integer amountFen = 0;

    @Column(nullable = false, length = 20)
    private String status = "CREATED";

    @Column(length = 30)
    private String source = "MANUAL";

    @Column(name = "payment_provider_type", length = 30)
    private String paymentProviderType;

    @Column(name = "external_trade_no", length = 128)
    private String externalTradeNo;

    @Column(name = "gateway_status", length = 40)
    private String gatewayStatus;

    @Column(name = "callback_payload_json", columnDefinition = "TEXT")
    private String callbackPayloadJson;

    @Column(name = "payment_notified_at")
    private LocalDateTime paymentNotifiedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "refund_status", length = 30)
    private String refundStatus;

    @Column(name = "refund_amount_fen")
    private Integer refundAmountFen;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "auto_renew_enabled", nullable = false)
    private Boolean autoRenewEnabled = false;

    @Column(name = "next_renewal_at")
    private LocalDateTime nextRenewalAt;

    @Column(name = "renewal_source_order_id")
    private Long renewalSourceOrderId;

    @Column(name = "expire_at")
    private LocalDateTime expireAt;

    @Column(name = "remark", length = 1000)
    private String remark;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
