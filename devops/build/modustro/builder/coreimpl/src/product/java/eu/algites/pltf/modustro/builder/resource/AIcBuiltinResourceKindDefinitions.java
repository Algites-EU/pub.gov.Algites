package eu.algites.pltf.modustro.builder.resource;

import eu.algites.pltf.modustro.builder.model.resource.AIcResourceKindDefinition;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceEndpointAction;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceStabilityRequirement;
import java.util.List;
import java.util.Set;

/**
 * Built-in Modustro Builder ResourceKind definitions.
 */
public final class AIcBuiltinResourceKindDefinitions {

    private AIcBuiltinResourceKindDefinitions() {
    }

    /**
     * Returns all built-in ResourceKind definitions.
     * @return immutable ResourceKind-definition list
     */
    public static List<AIcResourceKindDefinition> all() {
        return List.of(nativeBuildOutput(), docsSite(), schemaSite());
    }

    /**
     * Returns the native build-output ResourceKind.
     * @return native build-output definition
     */
    public static AIcResourceKindDefinition nativeBuildOutput() {
        return new AIcResourceKindDefinition(
            "native_build_output",
            Set.of("java", "python", "mps"),
            Set.of(AInResourceEndpointAction.DOWNLOAD, AInResourceEndpointAction.UPLOAD, AInResourceEndpointAction.MANAGE),
            AInResourceStabilityRequirement.REQUIRED
        );
    }

    /**
     * Returns the documentation-site ResourceKind.
     * @return documentation-site definition
     */
    public static AIcResourceKindDefinition docsSite() {
        return new AIcResourceKindDefinition(
            "docs_site",
            Set.of("modustro"),
            Set.of(AInResourceEndpointAction.DOWNLOAD, AInResourceEndpointAction.UPLOAD, AInResourceEndpointAction.MANAGE),
            AInResourceStabilityRequirement.REQUIRED
        );
    }

    /**
     * Returns the global schema-site ResourceKind.
     * @return schema-site definition
     */
    public static AIcResourceKindDefinition schemaSite() {
        return new AIcResourceKindDefinition(
            "schema_site",
            Set.of("modustro"),
            Set.of(AInResourceEndpointAction.DOWNLOAD, AInResourceEndpointAction.UPLOAD, AInResourceEndpointAction.MANAGE),
            AInResourceStabilityRequirement.FORBIDDEN
        );
    }
}
