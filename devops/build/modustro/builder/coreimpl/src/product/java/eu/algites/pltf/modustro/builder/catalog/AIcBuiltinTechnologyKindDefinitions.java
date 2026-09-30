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

    public static AIcTechnologyKindDefinition javaDefinition() {
        return new AIcTechnologyKindDefinition(
            "java",
            List.of(
                capability("source_native_processing"),
                capability("dependency_resolution"),
                capability("generation_of_native_documentation")
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

    public static AIcTechnologyKindDefinition pythonDefinition() {
        return new AIcTechnologyKindDefinition(
            "python",
            List.of(
                capability("source_native_processing"),
                capability("dependency_resolution"),
                capability("generation_of_native_documentation")
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

    public static AIcTechnologyKindDefinition modustroDefinition() {
        return new AIcTechnologyKindDefinition(
            "modustro",
            List.of(
                new AIcCapabilityDefinition("publication_of_global_schemas", Set.of(AInModelScope.REPOSITORY, AInModelScope.ARTIFACT_SET, AInModelScope.ARTIFACT), null),
                new AIcCapabilityDefinition("publication_of_docs_site", Set.of(AInModelScope.REPOSITORY), null),
                new AIcCapabilityDefinition("docs_site_content", Set.of(AInModelScope.REPOSITORY, AInModelScope.ARTIFACT_SET, AInModelScope.ARTIFACT), null)
            ),
            List.of(
                output("docs_site", true, false),
                output("schema_site", true, false)
            ),
            Set.of(),
            Set.of()
        );
    }

    private static AIcCapabilityDefinition capability(String aId) {
        return new AIcCapabilityDefinition(aId, Set.of(AInModelScope.REPOSITORY, AInModelScope.ARTIFACT_SET, AInModelScope.ARTIFACT), null);
    }

    private static AIcBuildOutputTypeDefinition output(String aId, boolean aCanBeProduced, boolean aCanBeUsedInDependency) {
        return new AIcBuildOutputTypeDefinition(aId, aCanBeProduced, aCanBeUsedInDependency, Set.of(), null);
    }
}
