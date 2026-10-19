package com.marketplace.application.port;

import com.marketplace.domain.outbox.OutboxStatus;
import java.util.Objects;

public sealed interface RequeueResult {

    record Requeued() implements RequeueResult {}

    record NotFound() implements RequeueResult {}

    record NotFailed(OutboxStatus status) implements RequeueResult {

        public NotFailed {
            Objects.requireNonNull(status, "status");
        }
    }
}
