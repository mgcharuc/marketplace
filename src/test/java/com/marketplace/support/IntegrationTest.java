package com.marketplace.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ContainerRuntimeCondition.class)
@SpringBootTest
@ExtendWith(DatabaseCleaningExtension.class)
@ActiveProfiles("test")
@Import({PostgresContainerConfiguration.class, TestDoublesConfiguration.class})
public @interface IntegrationTest {}
