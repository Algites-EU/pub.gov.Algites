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
        return List.of(nativeProductBinaries(), nativeProductSources(), nativeProductDocumentation(), nativeDevelopSources(), nativeDevelopBinaries(), nativeDevelopDocumentation(), modustroDocsSite(), schemaSite());
    }

    /**
     * Returns the native binary-output ResourceKind.
     * @return native binary-output definition
     */
    public static AIcResourceKindDefinition nativeProductBinaries() {
        return new AIcResourceKindDefinition(
            "native_product_binaries",
            Set.of("java", "python", "mps"),
            Set.of(AInResourceEndpointAction.DOWNLOAD, AInResourceEndpointAction.UPLOAD, AInResourceEndpointAction.MANAGE),
            AInResourceStabilityRequirement.REQUIRED
        );
    }

    /**
     * Returns the native source-output ResourceKind.
     * @return native source-output definition
     */
    public static AIcResourceKindDefinition nativeProductSources() {
        return new AIcResourceKindDefinition(
            "native_product_sources",
            Set.of("java", "python", "mps"),
            Set.of(AInResourceEndpointAction.DOWNLOAD, AInResourceEndpointAction.UPLOAD, AInResourceEndpointAction.MANAGE),
            AInResourceStabilityRequirement.REQUIRED
        );
    }

    /**
     * Returns the native documentation-output ResourceKind.
     * @return native documentation-output definition
     */
    public static AIcResourceKindDefinition nativeProductDocumentation() {
        return new AIcResourceKindDefinition(
            "native_product_documentation",
            Set.of("java", "python", "mps"),
            Set.of(AInResourceEndpointAction.DOWNLOAD, AInResourceEndpointAction.UPLOAD, AInResourceEndpointAction.MANAGE),
            AInResourceStabilityRequirement.REQUIRED
        );
    }

    public static AIcResourceKindDefinition nativeDevelopSources() {
        return new AIcResourceKindDefinition("native_develop_sources", Set.of("java", "python", "mps"),
            Set.of(AInResourceEndpointAction.DOWNLOAD, AInResourceEndpointAction.UPLOAD, AInResourceEndpointAction.MANAGE), AInResourceStabilityRequirement.REQUIRED);
    }
    public static AIcResourceKindDefinition nativeDevelopBinaries() {
        return new AIcResourceKindDefinition("native_develop_binaries", Set.of("java", "python", "mps"),
            Set.of(AInResourceEndpointAction.DOWNLOAD, AInResourceEndpointAction.UPLOAD, AInResourceEndpointAction.MANAGE), AInResourceStabilityRequirement.REQUIRED);
    }
    public static AIcResourceKindDefinition nativeDevelopDocumentation() {
        return new AIcResourceKindDefinition("native_develop_documentation", Set.of("java", "python", "mps"),
            Set.of(AInResourceEndpointAction.DOWNLOAD, AInResourceEndpointAction.UPLOAD, AInResourceEndpointAction.MANAGE), AInResourceStabilityRequirement.REQUIRED);
    }
    /**
     * Returns the Modustro documentation-site ResourceKind.
     * @return Modustro documentation-site definition
     */
    public static AIcResourceKindDefinition modustroDocsSite() {
        return new AIcResourceKindDefinition(
            "modustro_docs_site",
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
