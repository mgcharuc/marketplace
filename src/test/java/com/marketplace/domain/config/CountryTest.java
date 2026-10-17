package com.marketplace.domain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.domain.money.CurrencyConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CountryTest {

    private static final CurrencyConfig COP = new CurrencyConfig("COP", 0, RoundingMode.HALF_UP);

    private static TaxClass taxClass(String code, String rate) {
        return new TaxClass(code, new BigDecimal(rate));
    }

    @Test
    @DisplayName("Encuentra la clase tributaria del país por su código")
    void findsTaxClassByCode() {
        var country =
                new Country(
                        "CO",
                        COP,
                        GiftTaxPolicy.ABSORBED_BY_SELLER,
                        List.of(taxClass("IVA19", "0.19"), taxClass("IVA5", "0.05")));

        assertThat(country.taxClass("IVA5")).hasValue(taxClass("IVA5", "0.05"));
        assertThat(country.taxClass("IVA0")).isEmpty();
    }

    @Test
    @DisplayName("Rechaza dos clases tributarias con el mismo código")
    void rejectsDuplicateTaxClass() {
        var duplicated = List.of(taxClass("IVA19", "0.19"), taxClass("IVA19", "0.16"));

        assertThatThrownBy(() -> new Country("CO", COP, GiftTaxPolicy.NONE, duplicated))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rechaza un código de país que no sea de dos letras mayúsculas")
    void rejectsInvalidCode() {
        assertThatThrownBy(() -> new Country("col", COP, GiftTaxPolicy.NONE, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Las clases tributarias del país no se pueden modificar desde afuera")
    void keepsTaxClassesImmutable() {
        var classes = new ArrayList<>(List.of(taxClass("IVA19", "0.19")));
        var country = new Country("CO", COP, GiftTaxPolicy.NONE, classes);

        classes.add(taxClass("IVA5", "0.05"));

        assertThat(country.taxClasses()).hasSize(1);
        assertThatThrownBy(() -> country.taxClasses().add(taxClass("IVA0", "0")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Rechaza datos nulos")
    void rejectsNulls() {
        assertThatThrownBy(() -> new Country("CO", null, GiftTaxPolicy.NONE, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Country("CO", COP, null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TaxClass("IVA19", null))
                .isInstanceOf(NullPointerException.class);
    }
}
