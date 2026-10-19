package com.marketplace.application.quote;

import com.marketplace.application.CustomerAccess;
import com.marketplace.application.port.ConfigRepository;
import com.marketplace.application.port.CreditRepository;
import com.marketplace.application.port.DistributorRepository;
import com.marketplace.application.port.IdGenerator;
import com.marketplace.application.port.QuoteRenderer;
import com.marketplace.application.port.QuoteRepository;
import com.marketplace.application.port.TimeSource;
import com.marketplace.domain.config.ConfigVersion;
import com.marketplace.domain.config.DistributorNotFoundException;
import com.marketplace.domain.config.Tenant;
import com.marketplace.domain.credit.CreditAccount;
import com.marketplace.domain.credit.CreditAccountNotFoundException;
import com.marketplace.domain.credit.CreditKey;
import com.marketplace.domain.quote.PaymentMethod;
import com.marketplace.domain.quote.Quote;
import com.marketplace.domain.quote.QuoteHeader;
import com.marketplace.pricing.Breakdown;
import com.marketplace.pricing.CreditAvailability;
import com.marketplace.pricing.PricingEngine;
import com.marketplace.pricing.PricingInput;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calcula el desglose del carrito con la versión vigente del tenant y lo guarda como una cotización
 * que vence según el tiempo de vigencia del tenant. Los SKU repetidos se suman en la línea de su
 * primera aparición y todos los errores del carrito se informan juntos. A crédito informa el cupo
 * del tendero con el distribuidor sin reservarlo.
 */
public class QuoteCart {

    private static final Logger log = LoggerFactory.getLogger(QuoteCart.class);

    private final CustomerAccess access;
    private final DistributorRepository distributors;
    private final ConfigRepository configs;
    private final CreditRepository credits;
    private final QuoteRepository quotes;
    private final QuoteRenderer renderer;
    private final PricingEngine engine;
    private final TimeSource time;
    private final IdGenerator ids;

    public QuoteCart(
            CustomerAccess access,
            DistributorRepository distributors,
            ConfigRepository configs,
            CreditRepository credits,
            QuoteRepository quotes,
            QuoteRenderer renderer,
            PricingEngine engine,
            TimeSource time,
            IdGenerator ids) {
        this.access = access;
        this.distributors = distributors;
        this.configs = configs;
        this.credits = credits;
        this.quotes = quotes;
        this.renderer = renderer;
        this.engine = engine;
        this.time = time;
        this.ids = ids;
    }

    public Quote handle(QuoteCartCommand command) {
        Tenant tenant = access.tenantOf(command.tenantId(), command.customerId());
        ConfigVersion config = configs.current(tenant.id());
        ValidCart valid =
                new CartValidator(config).validate(command.items(), command.paymentMethod());
        if (!distributors.exists(tenant.id(), command.distributorId())) {
            throw new DistributorNotFoundException();
        }
        CreditAvailability credit = credit(command, valid.paymentMethod(), tenant);
        Instant now = time.now();
        Breakdown breakdown =
                engine.price(new PricingInput(config, tenant, valid.cart(), credit, now));
        var header =
                new QuoteHeader(
                        ids.quoteId(),
                        tenant.id(),
                        command.customerId(),
                        command.distributorId(),
                        config.number(),
                        tenant.currency(),
                        valid.paymentMethod(),
                        now,
                        now.plus(tenant.quoteTtl()));
        var quote =
                new Quote(header, breakdown.totals().total(), renderer.render(header, breakdown));
        quotes.save(quote);
        log.info("Cotización {} creada", header.quoteId());
        return quote;
    }

    private CreditAvailability credit(
            QuoteCartCommand command, PaymentMethod method, Tenant tenant) {
        if (method == PaymentMethod.CASH) {
            return CreditAvailability.none();
        }
        var key = new CreditKey(command.tenantId(), command.customerId(), command.distributorId());
        CreditAccount account =
                credits.find(key, tenant.currency())
                        .orElseThrow(CreditAccountNotFoundException::new);
        return CreditAvailability.available(account.available());
    }
}
