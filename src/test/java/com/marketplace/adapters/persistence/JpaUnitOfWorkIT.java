package com.marketplace.adapters.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.application.port.CreditRepository;
import com.marketplace.application.port.UnitOfWork;
import com.marketplace.domain.credit.CreditKey;
import com.marketplace.support.IntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class JpaUnitOfWorkIT {

    private static final CreditKey HIGH_NORTH =
            new CreditKey("CO-MARCA-A", "CLI-CREDITO-ALTO", "DIST-NORTE");
    private static final CreditKey LOW_NORTH =
            new CreditKey("CO-MARCA-A", "CLI-CREDITO-BAJO", "DIST-NORTE");

    @Autowired UnitOfWork unitOfWork;
    @Autowired CreditRepository credits;

    @Test
    @DisplayName("Lo que se hace dentro de la unidad de trabajo queda guardado al terminar")
    void commitsWork() {
        var after = unitOfWork.execute(() -> credits.consume(HIGH_NORTH, new BigDecimal("1000")));

        assertThat(after).hasValue(new BigDecimal("999000"));
        assertThat(available(HIGH_NORTH)).isEqualTo("999000");
    }

    @Test
    @DisplayName("Si algo falla dentro de la unidad de trabajo no queda guardado nada")
    void rollsBackEverythingOnFailure() {
        assertThatThrownBy(
                        () ->
                                unitOfWork.run(
                                        () -> {
                                            credits.consume(HIGH_NORTH, new BigDecimal("1000"));
                                            credits.consume(LOW_NORTH, new BigDecimal("1000"));
                                            throw new IllegalStateException("fallo inyectado");
                                        }))
                .hasMessage("fallo inyectado");

        assertThat(available(HIGH_NORTH)).isEqualTo("1000000");
        assertThat(available(LOW_NORTH)).isEqualTo("50000");
    }

    private BigDecimal available(CreditKey key) {
        return credits.find(key).orElseThrow().available().amount();
    }
}
