package eu.algites.pltf.modustro.builder.resource;

import eu.algites.pltf.modustro.builder.model.resource.AIcResourceEndpointCatalog;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceEndpointAction;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceStability;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests the normalized metadata bridge used by execution adapters.
 */
public final class AItcResourceEndpointMetadataBridgeTest {

    /**
     * Creates the test fixture.
     */
    public AItcResourceEndpointMetadataBridgeTest() {
    }

    /**
     * Verifies normalized resolver output is converted into the effective Modustro model.
     */
    @Test
    public void AIcResolvesNormalizedMetadataMap() {
        Map<String, Object> locItem = Map.of(
            "id", "algites-java-native-build-output-public-release-download",
            "url", "https://example.invalid/releases/",
            "enabled", true,
            "stability", "release"
        );
        Map<String, Object> locMetadata = Map.of(
            "java.native_binary_output.public.download",
            List.of(locItem)
        );

        AIcResourceEndpointCatalog locCatalog = AIcResourceEndpointMetadataBridge.builtin().resolve(locMetadata);
        Assert.assertEquals(
            locCatalog.select(
                "java",
                "native_binary_output",
                "public",
                AInResourceEndpointAction.DOWNLOAD,
                AInResourceStability.RELEASE,
                true
            ).size(),
            1
        );
    }
}
