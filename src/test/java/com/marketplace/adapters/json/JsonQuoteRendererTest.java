package com.marketplace.adapters.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.domain.config.ConfigFixtures;
import com.marketplace.domain.quote.PaymentMethod;
import com.marketplace.domain.quote.QuoteHeader;
import com.marketplace.pricing.Carts;
import com.marketplace.pricing.CreditAvailability;
import com.marketplace.pricing.PricingEngine;
import com.marketplace.pricing.PricingInput;
import com.marketplace.support.Resources;
import java.time.Instant;
import java.util.ArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class JsonQuoteRendererTest {

    private static final Instant CREATED = Instant.parse("2026-10-16T15:00:00Z");

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsonQuoteRenderer renderer = new JsonQuoteRenderer(mapper);

    @Test
    @DisplayName("La cotización lleva sus datos de cabecera seguidos del desglose, en ese orden")
    void rendersHeaderAndBreakdown() {
        var config = new JsonConfigCodec().decode(Resources.read("seed/co-marca-a.v1.json"));
        var tenant = ConfigFixtures.CO_MARCA_A;
        var breakdown =
                new PricingEngine()
                        .price(
                                new PricingInput(
                                        config,
                                        tenant,
                                        Carts.cart("GASEOSA-1500", 3, "SNACK-PAPAS", 3),
                                        CreditAvailability.none(),
                                        CREATED));
        var header =
                new QuoteHeader(
                        "q_00000000000000000000000001",
                        "CO-MARCA-A",
                        "CLI-CONTADO",
                        "DIST-NORTE",
                        1,
                        tenant.currency(),
                        PaymentMethod.CASH,
                        CREATED,
                        CREATED.plus(tenant.quoteTtl()));

        JsonNode rendered = mapper.readTree(renderer.render(header, breakdown));
        JsonNode expected = mapper.readTree(Resources.read("api/quote-combo-fiesta.json"));

        assertThat(rendered).isEqualTo(expected);
        assertThat(fieldNames(rendered)).isEqualTo(fieldNames(expected));
    }

    private static ArrayList<String> fieldNames(JsonNode node) {
        var names = new ArrayList<String>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
