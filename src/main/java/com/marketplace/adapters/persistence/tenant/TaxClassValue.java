package com.marketplace.adapters.persistence.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

@Embeddable
record TaxClassValue(
        @Column(name = "code") String code,
        @Column(name = "rate", precision = 5, scale = 4) BigDecimal rate) {}
