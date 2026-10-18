package com.marketplace.application.seed;

import com.marketplace.application.port.ConfigRepository;
import com.marketplace.application.port.CreditRepository;
import com.marketplace.application.port.CustomerRepository;
import com.marketplace.application.port.DistributorRepository;
import com.marketplace.application.port.SeedSource;
import com.marketplace.application.port.TenantRepository;
import com.marketplace.application.port.TenantSeed;
import com.marketplace.application.port.TimeSource;
import com.marketplace.application.port.UnitOfWork;
import com.marketplace.domain.config.ConfigVersion;
import com.marketplace.domain.config.TenantConfigHistory;
import com.marketplace.domain.credit.CreditKey;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Carga los tenants de las semillas que todavía no existen y no toca los que ya están en la base.
 */
public class LoadSeeds {

    private static final Logger log = LoggerFactory.getLogger(LoadSeeds.class);
    private static final int NO_VERSION = 0;

    private final SeedSource seeds;
    private final TenantRepository tenants;
    private final DistributorRepository distributors;
    private final CustomerRepository customers;
    private final CreditRepository credits;
    private final ConfigRepository configs;
    private final UnitOfWork unitOfWork;
    private final TimeSource time;

    public LoadSeeds(
            SeedSource seeds,
            TenantRepository tenants,
            DistributorRepository distributors,
            CustomerRepository customers,
            CreditRepository credits,
            ConfigRepository configs,
            UnitOfWork unitOfWork,
            TimeSource time) {
        this.seeds = seeds;
        this.tenants = tenants;
        this.distributors = distributors;
        this.customers = customers;
        this.credits = credits;
        this.configs = configs;
        this.unitOfWork = unitOfWork;
        this.time = time;
    }

    public void handle() {
        Map<String, ConfigVersion> versions =
                seeds.versions().stream()
                        .collect(Collectors.toMap(ConfigVersion::tenantId, Function.identity()));
        for (TenantSeed seed : seeds.tenants()) {
            String tenantId = seed.tenant().id();
            boolean loaded =
                    unitOfWork.execute(
                            () ->
                                    tenants.find(tenantId).isEmpty()
                                            && load(seed, versions.get(tenantId)));
            if (loaded) {
                log.info("Semillas cargadas para el tenant {}", tenantId);
            }
        }
    }

    private boolean load(TenantSeed seed, ConfigVersion version) {
        String tenantId = seed.tenant().id();
        tenants.add(seed.tenant());
        seed.distributors().forEach(distributor -> distributors.add(tenantId, distributor));
        for (var customer : seed.customers()) {
            customers.add(tenantId, customer);
            customer.creditAccounts()
                    .forEach(
                            account ->
                                    credits.open(
                                            new CreditKey(
                                                    tenantId,
                                                    customer.id(),
                                                    account.distributorId()),
                                            account.available()));
        }
        var history = new TenantConfigHistory(seed.tenant(), NO_VERSION);
        configs.publish(history.publish(version), time.now());
        return true;
    }
}
