package eu.algites.pltf.modustro.builder.structureddata.jackson;

import eu.algites.pltf.modustro.builder.model.resource.AIcgdResourceEndpoint_1;
import eu.algites.pltf.modustro.builder.model.resource.AIngResourceEndpointAction_1;
import eu.algites.pltf.modustro.builder.model.resource.AIngResourceEndpointVisibility_1;
import eu.algites.pltf.modustro.builder.model.resource.AIngResourceStability_1;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests YAML, JSON and XML structured-data mapping into generated ResourceEndpoint DTOs.
 */
public final class AItcJacksonStructuredDataLoaderTest {

    /**
     * Creates the test fixture.
     */
    public AItcJacksonStructuredDataLoaderTest() {
    }

    /**
     * Verifies YAML mapping preserves canonical property and enum wire values.
     *
     * @throws Exception when the temporary source cannot be written or loaded
     */
    @Test
    public void AIcLoadsYamlGeneratedDto() throws Exception {
        Path locFile = Files.createTempFile("algites-resource-endpoint-", ".yml");
        try {
            Files.writeString(locFile, """
                TechnologyKind: java
                ResourceKind: native_binary_output
                Visibility: public
                Action: download
                Id: algites-java-native-build-output-public-snapshot-download
                Url: https://example.invalid/maven/
                Enabled: true
                Stability: snapshot
                """);
            AIcgdResourceEndpoint_1 locEndpoint = AIcJacksonStructuredDataLoader.yaml().load(
                locFile,
                AIcgdResourceEndpoint_1.class
            );
            Assert.assertEquals(locEndpoint.visibility(), AIngResourceEndpointVisibility_1.PUBLIC);
            Assert.assertEquals(locEndpoint.action(), AIngResourceEndpointAction_1.DOWNLOAD);
            Assert.assertEquals(locEndpoint.stability(), AIngResourceStability_1.SNAPSHOT);
            Assert.assertEquals(locEndpoint.url(), "https://example.invalid/maven/");
        } finally {
            Files.deleteIfExists(locFile);
        }
    }

    /**
     * Verifies JSON mapping into the same generated DTO contract.
     *
     * @throws Exception when the temporary source cannot be written or loaded
     */
    @Test
    public void AIcLoadsJsonGeneratedDto() throws Exception {
        Path locFile = Files.createTempFile("algites-resource-endpoint-", ".json");
        try {
            Files.writeString(locFile, """
                {
                  "TechnologyKind": "modustro",
                  "ResourceKind": "schema_site",
                  "Visibility": "public",
                  "Action": "upload",
                  "Id": "algites-modustro-schema-site-public-upload",
                  "Url": "https://example.invalid/schema/",
                  "Enabled": true
                }
                """);
            AIcgdResourceEndpoint_1 locEndpoint = AIcJacksonStructuredDataLoader.json().load(
                locFile,
                AIcgdResourceEndpoint_1.class
            );
            Assert.assertEquals(locEndpoint.visibility(), AIngResourceEndpointVisibility_1.PUBLIC);
            Assert.assertEquals(locEndpoint.action(), AIngResourceEndpointAction_1.UPLOAD);
            Assert.assertNull(locEndpoint.stability());
        } finally {
            Files.deleteIfExists(locFile);
        }
    }

    /**
     * Verifies XML mapping into the generated DTO contract.
     *
     * @throws Exception when the temporary source cannot be written or loaded
     */
    @Test
    public void AIcLoadsXmlGeneratedDto() throws Exception {
        Path locFile = Files.createTempFile("algites-resource-endpoint-", ".xml");
        try {
            Files.writeString(locFile, """
                <ResourceModel>
                  <TechnologyKind>java</TechnologyKind>
                  <ResourceKind>native_binary_output</ResourceKind>
                  <Visibility>private</Visibility>
                  <Action>manage</Action>
                  <Id>algites-java-native-build-output-private-release-manage</Id>
                  <Url>https://example.invalid/manage/</Url>
                  <Enabled>true</Enabled>
                  <Stability>release</Stability>
                </ResourceModel>
                """);
            AIcgdResourceEndpoint_1 locEndpoint = AIcJacksonStructuredDataLoader.xml().load(
                locFile,
                AIcgdResourceEndpoint_1.class
            );
            Assert.assertEquals(locEndpoint.visibility(), AIngResourceEndpointVisibility_1.PRIVATE);
            Assert.assertEquals(locEndpoint.action(), AIngResourceEndpointAction_1.MANAGE);
            Assert.assertEquals(locEndpoint.stability(), AIngResourceStability_1.RELEASE);
        } finally {
            Files.deleteIfExists(locFile);
        }
    }
}
