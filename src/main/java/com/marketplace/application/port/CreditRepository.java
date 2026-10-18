package com.marketplace.application.port;

import com.marketplace.domain.credit.CreditAccount;
import com.marketplace.domain.credit.CreditKey;
import java.math.BigDecimal;
import java.util.Optional;

public interface CreditRepository {

    Optional<CreditAccount> find(CreditKey key);

    /** Abre la cuenta con el cupo completo disponible. */
    void open(CreditKey key, BigDecimal creditLimit);

    /**
     * Descuenta el monto del cupo disponible en una sola operación y devuelve el saldo que queda.
     * Si la cuenta no existe o el cupo no alcanza, no cambia nada y devuelve vacío.
     */
    Optional<BigDecimal> consume(CreditKey key, BigDecimal amount);
}
