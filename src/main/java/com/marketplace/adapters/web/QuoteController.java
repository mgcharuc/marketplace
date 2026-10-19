package com.marketplace.adapters.web;

import com.marketplace.application.quote.GetQuote;
import com.marketplace.application.quote.QuoteCart;
import com.marketplace.domain.quote.Quote;
import com.marketplace.domain.quote.QuoteKey;
import com.marketplace.domain.quote.QuoteNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/quotes")
@Tag(name = "Cotizaciones")
class QuoteController {

    private final QuoteCart quoteCart;
    private final GetQuote getQuote;
    private final RequestValidator validator;

    QuoteController(QuoteCart quoteCart, GetQuote getQuote, RequestValidator validator) {
        this.quoteCart = quoteCart;
        this.getQuote = getQuote;
        this.validator = validator;
    }

    @Operation(summary = "Cotiza un carrito y guarda el desglose")
    @ApiResponse(
            responseCode = "201",
            description = "Cotización creada",
            headers = @Header(name = "Location", description = "/v1/quotes/{quoteId}"))
    @ApiResponse(responseCode = "400", description = "Cabeceras o cuerpo con forma inválida")
    @ApiResponse(responseCode = "404", description = "El tenant o el tendero no existen")
    @ApiResponse(
            responseCode = "422",
            description = "Carrito inválido, distribuidor de otro tenant o sin cuenta de crédito")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> quote(
            @RequestAttribute(RequestIdentity.ATTRIBUTE) RequestIdentity identity,
            @RequestBody QuoteRequest request) {
        QuoteRequest valid = validator.require(request.normalized());
        MDC.put("distributorId", valid.distributorId());
        Quote quote = quoteCart.handle(valid.toCommand(identity));
        return ResponseEntity.created(URI.create("/v1/quotes/" + quote.header().quoteId()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(quote.snapshot());
    }

    @Operation(summary = "Devuelve una cotización tal como se respondió al crearla")
    @ApiResponse(responseCode = "200", description = "Cotización")
    @ApiResponse(responseCode = "400", description = "Cabeceras inválidas")
    @ApiResponse(
            responseCode = "404",
            description = "El tenant, el tendero o la cotización no existen, o es de otro tendero")
    @GetMapping("/{quoteId}")
    ResponseEntity<String> get(
            @RequestAttribute(RequestIdentity.ATTRIBUTE) RequestIdentity identity,
            @PathVariable String quoteId) {
        String id = quoteId.trim();
        if (!IdFormat.isQuoteId(id)) {
            throw new QuoteNotFoundException();
        }
        Quote quote = getQuote.handle(new QuoteKey(identity.tenantId(), identity.customerId(), id));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(quote.snapshot());
    }
}
