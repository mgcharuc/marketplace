package com.marketplace.adapters.persistence.outbox;

final class RelayLimits {

    // set_config con true vale solo hasta el fin de la transacción, igual que SET LOCAL.
    static final String IN_TRANSACTION =
            """
            SELECT set_config('lock_timeout', '5s', true)
                   || set_config('statement_timeout', '30s', true)
            """;

    private RelayLimits() {}
}
