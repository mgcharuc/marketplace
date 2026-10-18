package com.marketplace.bootstrap;

import com.marketplace.adapters.web.IdentityInterceptor;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

@Configuration(proxyBeanMethods = false)
class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IdentityInterceptor()).addPathPatterns("/v1/**");
    }

    // El cuerpo se lee sin convertir tipos: "3" no es un entero ni 123 es un texto.
    @Bean
    JsonMapperBuilderCustomizer strictJson() {
        return builder ->
                builder.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                        .withCoercionConfig(
                                LogicalType.Integer,
                                config -> {
                                    config.setCoercion(
                                            CoercionInputShape.String, CoercionAction.Fail);
                                    config.setCoercion(
                                            CoercionInputShape.EmptyString, CoercionAction.Fail);
                                    config.setCoercion(
                                            CoercionInputShape.Boolean, CoercionAction.Fail);
                                    config.setCoercion(
                                            CoercionInputShape.Float, CoercionAction.Fail);
                                })
                        .withCoercionConfig(
                                LogicalType.Textual,
                                config -> {
                                    config.setCoercion(
                                            CoercionInputShape.Integer, CoercionAction.Fail);
                                    config.setCoercion(
                                            CoercionInputShape.Float, CoercionAction.Fail);
                                    config.setCoercion(
                                            CoercionInputShape.Boolean, CoercionAction.Fail);
                                });
    }
}
