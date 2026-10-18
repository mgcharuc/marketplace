package com.marketplace.application.quote;

import com.marketplace.domain.quote.PaymentMethod;
import com.marketplace.pricing.Cart;

record ValidCart(Cart cart, PaymentMethod paymentMethod) {}
