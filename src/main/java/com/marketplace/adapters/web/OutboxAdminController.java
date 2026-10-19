package com.marketplace.adapters.web;

import com.marketplace.application.outbox.RequeueEvent;
import com.marketplace.domain.outbox.EventNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/outbox")
@Tag(name = "Soporte")
class OutboxAdminController {

    private final RequeueEvent requeueEvent;

    OutboxAdminController(RequeueEvent requeueEvent) {
        this.requeueEvent = requeueEvent;
    }

    @Operation(summary = "Vuelve a encolar un evento fallido")
    @ApiResponse(responseCode = "202", description = "Evento pendiente otra vez")
    @ApiResponse(responseCode = "404", description = "El evento no existe")
    @ApiResponse(responseCode = "409", description = "El evento no está fallido")
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
