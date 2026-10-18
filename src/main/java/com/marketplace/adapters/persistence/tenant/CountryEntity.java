package com.marketplace.adapters.persistence.tenant;

import com.marketplace.adapters.persistence.AssignedIdEntity;
import com.marketplace.domain.config.Country;
import com.marketplace.domain.config.GiftTaxPolicy;
import com.marketplace.domain.config.TaxClass;
import com.marketplace.domain.money.CurrencyConfig;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "country")
class CountryEntity extends AssignedIdEntity<String> {

    private static final int MIN_RATE_SCALE = 2;

    @Id private String code;

    @Column(name = "currency_code")
    private String currencyCode;

    @Column(name = "currency_scale")
    private int currencyScale;

    @Column(name = "rounding_mode")
    private String roundingMode;

    @Column(name = "gift_tax_policy")
    private String giftTaxPolicy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "country_tax_class", joinColumns = @JoinColumn(name = "country_code"))
    @OrderBy("code")
    private List<TaxClassValue> taxClasses = new ArrayList<>();

    protected CountryEntity() {}

    CountryEntity(Country country) {
        this.code = country.code();
        this.currencyCode = country.currency().code();
        this.currencyScale = country.currency().scale();
        this.roundingMode = country.currency().roundingMode().name();
        this.giftTaxPolicy = country.giftTaxPolicy().name();
        country.taxClasses()
                .forEach(
                        taxClass ->
                                taxClasses.add(
                                        new TaxClassValue(taxClass.code(), taxClass.rate())));
    }

    @Override
    public String getId() {
        return code;
    }

    Country toDomain() {
        var currency =
                new CurrencyConfig(currencyCode, currencyScale, RoundingMode.valueOf(roundingMode));
        var classes =
                taxClasses.stream()
                        .map(taxClass -> new TaxClass(taxClass.code(), rate(taxClass.rate())))
                        .toList();
        return new Country(code, currency, GiftTaxPolicy.valueOf(giftTaxPolicy), classes);
    }

    // La columna guarda cuatro decimales y la tasa se escribe como en la configuración: 0.19, 0.00.
    private static BigDecimal rate(BigDecimal stored) {
        BigDecimal stripped = stored.stripTrailingZeros();
        return stripped.setScale(Math.max(MIN_RATE_SCALE, stripped.scale()));
    }
}
