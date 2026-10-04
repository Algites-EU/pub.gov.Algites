package eu.algites.pltf.modustro.builder.gradleinit;
import org.testng.annotations.Test;import static org.testng.Assert.*;import java.io.*;import java.util.*;
public class AItcPublicationDescriptorTest {
 @Test public void orderedCompleteTree()throws Exception{
  File fixture=new File(getClass().getResource("/publication-tree.yml").toURI());
  var values=AIcArtifactDirectoryMetadataResolverKt.AIcReadSimpleYamlScalars(fixture);
  var policies=AIcArtifactDirectoryMetadataResolverKt.AIcOutputPublishingFromConfig(values,fixture);
  assertTrue(policies.get("native_develop_binaries").getSnapshot().AIcEffectivePublishingEnabled());
  assertFalse(policies.get("native_develop_sources").getSnapshot().AIcEffectivePublishingEnabled());
  assertTrue(policies.get("native_product_binaries").getSnapshot().AIcEffectivePublishingEnabled());
  var endpoint=policies.get("native_product_binaries").getSnapshot().getEndpointPublications().get("primary");
  assertEquals(endpoint.getPublications().size(),2);
  var extensions=(List<Map<String,Object>>)endpoint.getPublications().get(0).get("ExtendedPublications");
  assertEquals(((List<?>)extensions.get(0).get("ExtendedPublications")).size(),1);
  assertEquals(endpoint.AIcEffectivePublishingRetryCount(),2);
 }
}
