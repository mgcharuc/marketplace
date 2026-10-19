package com.marketplace.adapters.web;

import com.marketplace.application.order.ConfirmOrder;
import com.marketplace.application.order.ConfirmOrderCommand;
import com.marketplace.application.order.GetOrder;
import com.marketplace.application.order.GetOrderHistory;
import com.marketplace.application.order.OrderConfirmation;
import com.marketplace.domain.order.OrderKey;
import com.marketplace.domain.order.OrderNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/orders")
@Tag(name = "Órdenes")
class OrderController {

    private static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final ConfirmOrder confirmOrder;
    private final GetOrder getOrder;
    private final GetOrderHistory getOrderHistory;
    private final RequestValidator validator;

    OrderController(
            ConfirmOrder confirmOrder,
            GetOrder getOrder,
            GetOrderHistory getOrderHistory,
            RequestValidator validator) {
        this.confirmOrder = confirmOrder;
        this.getOrder = getOrder;
        this.getOrderHistory = getOrderHistory;
        this.validator = validator;
    }

    @Operation(summary = "Lista las órdenes del tendero, de la más reciente a la más antigua")
    @ApiResponse(responseCode = "200", description = "Página del historial")
    @ApiResponse(responseCode = "400", description = "Cabeceras o paginación inválidas")
    @ApiResponse(responseCode = "404", description = "El tenant o el tendero no existen")
    @GetMapping
    OrderHistoryResponse history(
            @RequestAttribute(RequestIdentity.ATTRIBUTE) RequestIdentity identity,
            @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0"))
                    @RequestParam(required = false)
                    String page,
            @Parameter(
                            schema =
                                    @Schema(
                                            type = "integer",
                                            minimum = "1",
                                            maximum = "100",
                                            defaultValue = "20"))
                    @RequestParam(required = false)
                    String size) {
        HistoryRequest valid = validator.require(HistoryRequest.parse(page, size));
        var result =
                getOrderHistory.handle(identity.tenantId(), identity.customerId(), valid.toQuery());
        return OrderHistoryResponse.of(result.map(OrderSummaryResponse::of));
    }

    @Operation(summary = "Confirma una cotización vigente y crea la orden")
    @ApiResponse(
            responseCode = "201",
            description = "Orden creada. Un reintento con la misma clave recibe la misma respuesta",
            headers = {
                @Header(name = "Location", description = "/v1/orders/{orderId}"),
                @Header(name = "Idempotent-Replayed", description = "true en un reintento")
            })
    @ApiResponse(responseCode = "400", description = "Cabeceras, clave o cuerpo inválidos")
    @ApiResponse(
            responseCode = "404",
            description = "El tenant, el tendero o la cotización no existen")
    @ApiResponse(responseCode = "409", description = "La cotización venció o ya se confirmó")
    @ApiResponse(
            responseCode = "422",
            description = "Cupo insuficiente, sin cuenta de crédito o clave usada con otro cuerpo")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> confirm(
            @RequestAttribute(RequestIdentity.ATTRIBUTE) RequestIdentity identity,
            @RequestAttribute(IdempotencyKeyInterceptor.ATTRIBUTE) String idempotencyKey,
            @RequestBody OrderRequest request) {
        OrderRequest valid = validator.require(request.normalized());
        OrderConfirmation confirmation =
                confirmOrder.handle(
                        new ConfirmOrderCommand(
                                identity.tenantId(),
                                identity.customerId(),
                                valid.quoteId(),
                                idempotencyKey,
                                RequestHash.of(valid)));
        var response =
                ResponseEntity.created(URI.create("/v1/orders/" + confirmation.orderId()))
                        .contentType(MediaType.APPLICATION_JSON);
        if (confirmation.replayed()) {
            response.header(REPLAYED_HEADER, "true");
        }
        return response.body(confirmation.body());
    }

    @Operation(summary = "Devuelve una orden tal como se respondió al confirmarla")
    @ApiResponse(responseCode = "200", description = "Orden")
    @ApiResponse(responseCode = "400", description = "Cabeceras inválidas")
    @ApiResponse(
            responseCode = "404",
            description = "El tenant, el tendero o la orden no existen, o es de otro tendero")
    @GetMapping("/{orderId}")
    ResponseEntity<String> get(
            @RequestAttribute(RequestIdentity.ATTRIBUTE) RequestIdentity identity,
            @PathVariable String orderId) {
        String id = orderId.trim();
        if (!IdFormat.isOrderId(id)) {
            throw new OrderNotFoundException();
        }
        String body = getOrder.handle(new OrderKey(identity.tenantId(), identity.customerId(), id));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
