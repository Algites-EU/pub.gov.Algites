package eu.algites.pltf.modustro.builder.structureddata.jackson;

import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Tests format-neutral YAML, JSON and XML mapping without coupling the loader to one governance model. */
public final class AItcJacksonStructuredDataLoaderTest {

    /** Simple format-neutral fixture. */
    public static final class AIcFixture {
        public String Name;
        public boolean Enabled;
        public AIcFixture() { }
    }

    @Test
    public void AIcLoadsYaml() throws Exception {
        Path locFile = Files.createTempFile("modustro-structured-data-", ".yml");
        try {
            Files.writeString(locFile, "Name: yaml\nEnabled: true\n");
            AIcFixture locValue = AIcJacksonStructuredDataLoader.yaml().load(locFile, AIcFixture.class);
            Assert.assertEquals(locValue.Name, "yaml");
            Assert.assertTrue(locValue.Enabled);
        } finally {
            Files.deleteIfExists(locFile);
        }
    }

    @Test
    public void AIcLoadsJson() throws Exception {
        Path locFile = Files.createTempFile("modustro-structured-data-", ".json");
        try {
            Files.writeString(locFile, "{\"Name\":\"json\",\"Enabled\":true}\n");
            AIcFixture locValue = AIcJacksonStructuredDataLoader.json().load(locFile, AIcFixture.class);
            Assert.assertEquals(locValue.Name, "json");
            Assert.assertTrue(locValue.Enabled);
        } finally {
            Files.deleteIfExists(locFile);
        }
    }

    @Test
    public void AIcLoadsXml() throws Exception {
        Path locFile = Files.createTempFile("modustro-structured-data-", ".xml");
        try {
            Files.writeString(locFile, "<Fixture><Name>xml</Name><Enabled>true</Enabled></Fixture>\n");
            AIcFixture locValue = AIcJacksonStructuredDataLoader.xml().load(locFile, AIcFixture.class);
            Assert.assertEquals(locValue.Name, "xml");
            Assert.assertTrue(locValue.Enabled);
        } finally {
            Files.deleteIfExists(locFile);
        }
    }
}
