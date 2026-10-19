package com.marketplace.adapters.persistence.order;

import com.marketplace.adapters.persistence.AssignedIdEntity;
import com.marketplace.domain.order.Order;
import com.marketplace.domain.order.OrderHeader;
import com.marketplace.domain.order.OrderStatus;
import com.marketplace.domain.quote.QuoteHeader;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
class OrderEntity extends AssignedIdEntity<String> {

    @Id private String id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "distributor_id")
    private String distributorId;

    @Column(name = "order_seq")
    private long orderSeq;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "quote_id")
    private String quoteId;

    private String status;

    @Column(precision = 19, scale = 4)
    private BigDecimal total;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected OrderEntity() {}

    OrderEntity(Order order) {
        OrderHeader header = order.header();
        QuoteHeader quote = header.quote();
        this.id = header.orderId();
        this.tenantId = quote.tenantId();
        this.customerId = quote.customerId();
        this.distributorId = quote.distributorId();
        this.orderSeq = order.sequence();
        this.orderNumber = header.orderNumber();
        this.quoteId = quote.quoteId();
        this.status = OrderStatus.CONFIRMED.name();
        this.total = header.total().amount();
        this.responseBody = order.body();
        this.confirmedAt = header.confirmedAt();
    }

    @Override
    public String getId() {
        return id;
    }
}
