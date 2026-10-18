package com.marketplace.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasReturnType.Predicates.rawReturnType;
import static com.tngtech.archunit.lang.conditions.ArchConditions.be;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noCodeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Set;
import java.util.stream.Stream;

@AnalyzeClasses(packages = "com.marketplace", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "com.marketplace.domain..";
    private static final String PRICING = "com.marketplace.pricing..";
    private static final String[] MONEY_PACKAGES = {
        DOMAIN,
        PRICING,
        "com.marketplace.adapters.web..",
        "com.marketplace.adapters.json..",
        "com.marketplace.adapters.persistence.."
    };
    private static final String EXACT_DECIMALS =
            "los montos, tasas y porcentajes son decimales exactos";
    private static final Set<String> FLOATING_POINT =
            Set.of("double", "float", Double.class.getName(), Float.class.getName());

    private static final DescribedPredicate<JavaClass> FLOATING_POINT_TYPE =
            DescribedPredicate.describe(
                    "floating point",
                    type -> FLOATING_POINT.contains(type.getBaseComponentType().getName()));

    private static final DescribedPredicate<JavaField> FLOATING_POINT_FIELD =
            DescribedPredicate.describe(
                    "a field of floating point type",
                    field -> involvesFloatingPoint(field.getType()));

    private static final DescribedPredicate<JavaCodeUnit> FLOATING_POINT_SIGNATURE =
            DescribedPredicate.describe(
                    "a signature with floating point types",
                    codeUnit ->
                            Stream.concat(
                                            Stream.of(codeUnit.getReturnType()),
                                            codeUnit.getParameterTypes().stream())
                                    .anyMatch(ArchitectureTest::involvesFloatingPoint));

    private static boolean involvesFloatingPoint(JavaType type) {
        return type.getAllInvolvedRawTypes().stream().anyMatch(FLOATING_POINT_TYPE);
    }

    @ArchTest
    static final ArchRule domainDependsOnlyOnItselfAndTheJdk =
            classes()
                    .that()
                    .resideInAPackage(DOMAIN)
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(DOMAIN, "java..")
                    .allowEmptyShould(true)
                    .because("el dominio no debe depender de frameworks ni de otras capas");

    @ArchTest
    static final ArchRule pricingDependsOnlyOnDomainAndTheJdk =
            classes()
                    .that()
                    .resideInAPackage(PRICING)
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(PRICING, DOMAIN, "java..")
                    .allowEmptyShould(true)
                    .because("el motor de precios es Java puro y solo conoce el dominio");

    @ArchTest
    static final ArchRule noFloatingPointFields =
            noFields()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAnyPackage(MONEY_PACKAGES)
                    .should(be(FLOATING_POINT_FIELD))
                    .allowEmptyShould(true)
                    .because(EXACT_DECIMALS);

    @ArchTest
    static final ArchRule noFloatingPointSignatures =
            noCodeUnits()
                    .that()
                    .areDeclaredInClassesThat()
                    .resideInAnyPackage(MONEY_PACKAGES)
                    .should(be(FLOATING_POINT_SIGNATURE))
                    .allowEmptyShould(true)
                    .because(EXACT_DECIMALS);

    @ArchTest
    static final ArchRule noFloatingPointCalculations =
            noClasses()
                    .that()
                    .resideInAnyPackage(DOMAIN, PRICING)
                    .should()
                    .callMethodWhere(target(rawReturnType(FLOATING_POINT_TYPE)))
                    .allowEmptyShould(true)
                    .because(EXACT_DECIMALS);

    @ArchTest
    static final ArchRule noBigDecimalFromFloatingPoint =
            noClasses()
                    .should()
                    .callConstructor(BigDecimal.class, double.class)
                    .orShould()
                    .callConstructor(BigDecimal.class, double.class, MathContext.class)
                    .orShould()
                    .callMethod(BigDecimal.class, "valueOf", double.class)
                    .because(
                            "un BigDecimal creado desde un double arrastra su error de representación");
}
