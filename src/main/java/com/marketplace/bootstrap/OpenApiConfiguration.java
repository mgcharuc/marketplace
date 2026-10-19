package com.marketplace.bootstrap;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    private static final String JSON = "application/json";
    private static final String ANY = "*/*";
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String PROBLEM = "Problem";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Marketplace B2B")
                                .version("v1")
                                .description(
                                        "Cotización y confirmación de pedidos de tenderos a"
                                                + " distribuidores. Los montos van como texto en"
                                                + " la escala de la moneda."));
    }

    // Los controladores devuelven el JSON guardado como texto, así que los ejemplos de respuesta
    // vienen de archivos.
    @Bean
    OpenApiCustomizer headersAndResponses() {
        Object quote = example("openapi/quote.json");
        Object order = example("openapi/order.json");
        Map<String, Object> examples =
                Map.of(
                        "POST /v1/quotes", quote,
                        "GET /v1/quotes/{quoteId}", quote,
                        "POST /v1/orders", order,
                        "GET /v1/orders/{orderId}", order);
        return api -> {
            api.schema(PROBLEM, problemSchema());
            for (Map.Entry<String, PathItem> path : api.getPaths().entrySet()) {
                for (Map.Entry<HttpMethod, Operation> entry :
                        path.getValue().readOperationsMap().entrySet()) {
                    String key = entry.getKey() + " " + path.getKey();
                    document(path.getKey(), entry.getKey(), entry.getValue(), examples.get(key));
                }
            }
        };
    }

    private static void document(
            String path, HttpMethod method, Operation operation, Object example) {
        if (!path.startsWith(ApiPaths.SUPPORT)) {
            operation.addParametersItem(header("X-Tenant-Id", true, "CO-MARCA-A"));
            operation.addParametersItem(header("X-Customer-Id", true, "CLI-CONTADO"));
        }
        if (method == HttpMethod.POST && path.equals(ApiPaths.ORDERS)) {
            operation.addParametersItem(header("Idempotency-Key", true, "pedido-001"));
        }
        operation.addParametersItem(header("X-Correlation-Id", false, null));

        ApiResponses responses = operation.getResponses();
        if (operation.getRequestBody() != null) {
            responses.addApiResponse("415", new ApiResponse().description("El cuerpo no es JSON"));
        }
        responses.addApiResponse("500", new ApiResponse().description("Error inesperado"));
        responses.forEach((code, response) -> describe(code, response, example));
    }

    private static void describe(String code, ApiResponse response, Object example) {
        if (code.startsWith("2")) {
            if (example != null) {
                response.setContent(json(new ObjectSchema(), example));
            } else if (response.getContent() != null && response.getContent().containsKey(ANY)) {
                response.setContent(json(response.getContent().get(ANY).getSchema(), null));
            }
        } else {
            response.setContent(
                    new Content()
                            .addMediaType(
                                    PROBLEM_JSON,
                                    new MediaType()
                                            .schema(
                                                    new Schema<>()
                                                            .$ref(
                                                                    "#/components/schemas/"
                                                                            + PROBLEM))));
        }
    }

    private static Content json(Schema<?> schema, Object example) {
        return new Content().addMediaType(JSON, new MediaType().schema(schema).example(example));
    }

    private static HeaderParameter header(String name, boolean required, String example) {
        var header = new HeaderParameter();
        header.name(name).required(required).schema(new StringSchema()).example(example);
        return header;
    }

    private static Schema<?> problemSchema() {
        var error =
                new ObjectSchema()
                        .addProperty("index", new IntegerSchema())
                        .addProperty("field", new StringSchema().example("items"))
                        .addProperty("sku", new StringSchema())
                        .addProperty("code", new StringSchema().example("EMPTY_CART"));
        return new ObjectSchema()
                .description("Error en formato problem+json. Cada código agrega sus propios datos.")
                .addProperty(
                        "type",
                        new StringSchema()
                                .example("https://marketplace.example/problems/order-not-found"))
                .addProperty("title", new StringSchema().example("Order not found"))
                .addProperty("status", new IntegerSchema().example(404))
                .addProperty("detail", new StringSchema().example("La orden no existe."))
                .addProperty("instance", new StringSchema())
                .addProperty("code", new StringSchema().example("ORDER_NOT_FOUND"))
                .addProperty("correlationId", new StringSchema())
                .addProperty("errors", new ArraySchema().items(error))
                .addProperty("orderId", new StringSchema())
                .addProperty("expiresAt", new StringSchema())
                .addProperty("available", new StringSchema())
                .addProperty("required", new StringSchema())
                .addProperty("eventStatus", new StringSchema());
    }

    private static Object example(String resource) {
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            return Json.mapper().readTree(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
