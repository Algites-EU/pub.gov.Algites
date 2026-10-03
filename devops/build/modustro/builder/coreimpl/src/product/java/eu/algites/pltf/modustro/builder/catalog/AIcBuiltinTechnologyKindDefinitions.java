package eu.algites.pltf.modustro.builder.catalog;

import eu.algites.pltf.modustro.builder.model.AInModelScope;
import eu.algites.pltf.modustro.builder.model.capability.AIcCapabilityDefinition;
import eu.algites.pltf.modustro.builder.model.output.AIcBuildOutputTypeDefinition;
import eu.algites.pltf.modustro.builder.model.technology.AIcTechnologyKindDefinition;
import java.util.List;
import java.util.Set;

/**
 * Built-in Modustro Builder technology definitions. They describe contracts only and do not execute build actions.
 */
public final class AIcBuiltinTechnologyKindDefinitions {

    private AIcBuiltinTechnologyKindDefinitions() {
    }

    /**
     * Creates the built-in Java TechnologyKind definition.
     * @return Java TechnologyKind definition
     */
    public static AIcTechnologyKindDefinition javaDefinition() {
        return new AIcTechnologyKindDefinition(
            "java",
            List.of(
                capability("source_native_processing"),
                capability("dependency_resolution"),
                capability("generation_of_native_documentation", "urn:algites:modustro:builder:capability-configuration:java:generation-of-native-documentation:1")
            ),
            List.of(
                output("java_classes_jar", true, true),
                output("java_sources_jar", true, true),
                output("java_javadoc_jar", true, true)
            ),
            Set.of("java_classes_jar", "java_sources_jar"),
            Set.of("java_classes_jar")
        );
    }

    /**
     * Creates the built-in Python TechnologyKind definition.
     * @return Python TechnologyKind definition
     */
    public static AIcTechnologyKindDefinition pythonDefinition() {
        return new AIcTechnologyKindDefinition(
            "python",
            List.of(
                capability("source_native_processing"),
                capability("dependency_resolution"),
                capability("generation_of_native_documentation", "urn:algites:modustro:builder:capability-configuration:python:generation-of-native-documentation:1")
            ),
            List.of(
                output("python_wheel", true, true),
                output("python_sdist", true, true),
                new AIcBuildOutputTypeDefinition(
                    "python_distribution",
                    false,
                    true,
                    Set.of("python_wheel", "python_sdist"),
                    null
                )
            ),
            Set.of("python_wheel", "python_sdist"),
            Set.of("python_distribution")
        );
    }

    /**
     * Creates the built-in Modustro TechnologyKind definition.
     * @return Modustro TechnologyKind definition
     */
    public static AIcTechnologyKindDefinition modustroDefinition() {
        return new AIcTechnologyKindDefinition(
            "modustro",
            List.of(
                new AIcCapabilityDefinition("publication_of_global_schemas", Set.of(AInModelScope.REPOSITORY, AInModelScope.ARTIFACT_SET, AInModelScope.ARTIFACT), "urn:algites:modustro:builder:capability-configuration:modustro:publication-of-global-schemas:1"),
                new AIcCapabilityDefinition("publication_of_docs_site", Set.of(AInModelScope.REPOSITORY), "urn:algites:modustro:builder:capability-configuration:modustro:publication-of-docs-site:1"),
                new AIcCapabilityDefinition("docs_site_content", Set.of(AInModelScope.REPOSITORY, AInModelScope.ARTIFACT_SET, AInModelScope.ARTIFACT), "urn:algites:modustro:builder:capability-configuration:modustro:docs-site-content:1")
            ),
            List.of(
                output("modustro_docs_site", true, false),
                output("schema_site", true, false)
            ),
            Set.of(),
            Set.of()
        );
    }

    private static AIcCapabilityDefinition capability(String aId) {
        return capability(aId, null);
    }

    private static AIcCapabilityDefinition capability(String aId, String aConfigurationSchemaId) {
        return new AIcCapabilityDefinition(
            aId,
            Set.of(AInModelScope.REPOSITORY, AInModelScope.ARTIFACT_SET, AInModelScope.ARTIFACT),
            aConfigurationSchemaId
        );
    }

    private static AIcBuildOutputTypeDefinition output(String aId, boolean aCanBeProduced, boolean aCanBeUsedInDependency) {
        return new AIcBuildOutputTypeDefinition(aId, aCanBeProduced, aCanBeUsedInDependency, Set.of(), null);
    }
}
