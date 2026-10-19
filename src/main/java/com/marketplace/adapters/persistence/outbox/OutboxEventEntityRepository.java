package com.marketplace.adapters.persistence.outbox;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxEventEntityRepository extends JpaRepository<OutboxEventEntity, UUID> {}
