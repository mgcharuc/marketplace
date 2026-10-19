package com.marketplace.adapters.web;

import com.marketplace.application.outbox.RequeueEvent;
import com.marketplace.domain.outbox.EventNotFoundException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/outbox")
class OutboxAdminController {

    private final RequeueEvent requeueEvent;

    OutboxAdminController(RequeueEvent requeueEvent) {
        this.requeueEvent = requeueEvent;
    }

    @PostMapping("/{eventId}/retry")
    ResponseEntity<Void> retry(@PathVariable String eventId) {
        String id = eventId.trim();
        if (!IdFormat.isEventId(id)) {
            throw new EventNotFoundException();
        }
        requeueEvent.handle(UUID.fromString(id));
        return ResponseEntity.accepted().build();
    }
}
