package com.marketplace.adapters.persistence.credit;

import com.marketplace.adapters.persistence.AssignedIdEntity;
import com.marketplace.domain.credit.CreditMovement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "credit_movement")
class CreditMovementEntity extends AssignedIdEntity<String> {

    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "distributor_id")
    private String distributorId;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at")
    private Instant createdAt;

    protected CreditMovementEntity() {}

    CreditMovementEntity(CreditMovement movement) {
        this.orderId = movement.orderId();
        this.tenantId = movement.account().tenantId();
        this.customerId = movement.account().customerId();
        this.distributorId = movement.account().distributorId();
        this.amount = movement.amount().amount();
        this.createdAt = movement.createdAt();
    }

    @Override
    public String getId() {
        return orderId;
    }
}
