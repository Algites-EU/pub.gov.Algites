package eu.algites.pltf.modustro.builder.resource;

import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import eu.algites.pltf.modustro.builder.model.resource.AIcResourceEndpointDefinition;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceEndpointAction;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceStability;
import java.net.URI;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests built-in ResourceKind definitions and endpoint validation.
 */
public final class AItcBuiltinResourceKindDefinitionsTest {

    /**
     * Creates the test fixture.
     */
    public AItcBuiltinResourceKindDefinitionsTest() {
    }

    /**
     * Verifies the built-in stability policies used by Phase 5.
     *
     * @throws Exception when URI construction or model validation unexpectedly fails
     */
    @Test
    public void AIcValidatesBuiltinStabilityPolicies() throws Exception {
        AIcResourceEndpointValidator locValidator = AIcResourceEndpointValidator.builtin();
        locValidator.validate(
            new AIcResourceEndpointDefinition(
                "java", "native_build_output", "public", AInResourceEndpointAction.UPLOAD,
                "algites-java-native-build-output-public-snapshot-upload",
                new URI("https://example.invalid/maven/"), null, true, AInResourceStability.SNAPSHOT, null
            )
        );
        locValidator.validate(
            new AIcResourceEndpointDefinition(
                "modustro", "schema_site", "public", AInResourceEndpointAction.UPLOAD,
                "algites-modustro-schema-site-public-upload",
                new URI("https://example.invalid/schema/"), null, true, null, null
            )
        );

        Assert.expectThrows(
            AIxModelValidationException.class,
            () -> locValidator.validate(
                new AIcResourceEndpointDefinition(
                    "modustro", "schema_site", "public", AInResourceEndpointAction.UPLOAD,
                    "algites-modustro-schema-site-public-snapshot-upload",
                    new URI("https://example.invalid/schema/"), null, true, AInResourceStability.SNAPSHOT, null
                )
            )
        );
    }
}
