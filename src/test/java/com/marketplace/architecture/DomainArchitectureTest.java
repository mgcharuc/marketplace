package com.marketplace.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.marketplace", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainArchitectureTest {

    @ArchTest
    static final ArchRule domainDependsOnlyOnItselfAndTheJdk =
            classes()
                    .that()
                    .resideInAPackage("com.marketplace.domain..")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage("com.marketplace.domain..", "java..")
                    .allowEmptyShould(true)
                    .because("el dominio no debe depender de frameworks ni de otras capas");
}
