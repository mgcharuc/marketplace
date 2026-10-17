package com.marketplace.pricing;

import com.marketplace.domain.config.Stage;

interface PricingStage {

    Stage stage();

    void apply(PricingContext context);
}
