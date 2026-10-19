package com.marketplace.adapters.persistence.order;

import java.math.BigDecimal;
import java.time.Instant;

interface OrderSummaryRow {

    String getId();

    String getOrderNumber();

    String getDistributorId();

    Instant getConfirmedAt();

    BigDecimal getTotal();

    String getStatus();
}
