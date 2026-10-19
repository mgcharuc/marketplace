package com.marketplace.adapters.persistence.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.application.port.DeliveryLog;
import com.marketplace.application.port.UnitOfWork;
import com.marketplace.domain.outbox.Destination;
import com.marketplace.support.FixedTimeSource;
import com.marketplace.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class JpaDeliveryLogIT {

    private static final UUID EVENT = UUID.fromString("00000000-0000-7000-8000-000000000001");

    @Autowired DeliveryLog deliveries;
    @Autowired UnitOfWork unitOfWork;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("La entrega se registra por evento y destino")
    void recordsDeliveryPerDestination() {
        deliveries.record(EVENT, Destination.ERP, FixedTimeSource.START);

        assertThat(recorded()).containsExactly(EVENT + " ERP");
    }

    @Test
    @DisplayName("Registrar dos veces la misma entrega deja una sola fila")
    void recordingTwiceKeepsOneRow() {
        deliveries.record(EVENT, Destination.ERP, FixedTimeSource.START);
        deliveries.record(EVENT, Destination.ERP, FixedTimeSource.START.plusSeconds(5));

        assertThat(recorded()).containsExactly(EVENT + " ERP");
    }

    @Test
    @DisplayName("La entrega queda registrada aunque la transacción que la pidió se revierta")
    void recordSurvivesOuterRollback() {
        assertThatThrownBy(
                        () ->
                                unitOfWork.run(
                                        () -> {
                                            deliveries.record(
                                                    EVENT, Destination.PUSH, FixedTimeSource.START);
                                            throw new IllegalStateException("caída");
                                        }))
                .hasMessage("caída");

        assertThat(recorded()).containsExactly(EVENT + " PUSH");
    }

    @Test
    @DisplayName("La entrega registrada para un destino no cuenta para el otro")
    void findsDeliveryOnlyForItsDestination() {
        deliveries.record(EVENT, Destination.PUSH, FixedTimeSource.START);

        assertThat(deliveries.exists(EVENT, Destination.PUSH)).isTrue();
        assertThat(deliveries.exists(EVENT, Destination.ERP)).isFalse();
    }

    private List<String> recorded() {
        return jdbc.queryForList(
                "SELECT event_id::text || ' ' || destination FROM delivered_event", String.class);
    }
}
