package eu.algites.pltf.modustro.builder.gradleinit;
import org.testng.annotations.Test;import static org.testng.Assert.*;import java.io.*;import java.util.*;
public class AItcPublicationDescriptorTest {
 @Test public void orderedCompleteTree()throws Exception{
  File fixture=new File(getClass().getResource("/publication-tree.yml").toURI());
  var values=AIcArtifactDirectoryMetadataResolverKt.AIcReadSimpleYamlScalars(fixture);
  var policies=AIcArtifactDirectoryMetadataResolverKt.AIcOutputPublicationsFromConfig(values,fixture);
  assertTrue(policies.get("native_develop_binaries").getSnapshot().AIcEffectivePublicationEnabled());
  assertFalse(policies.get("native_develop_sources").getSnapshot().AIcEffectivePublicationEnabled());
  assertTrue(policies.get("native_product_binaries").getSnapshot().AIcEffectivePublicationEnabled());
  var endpoint=policies.get("native_product_binaries").getSnapshot().getPublicationEndpoints().get("primary");
  assertEquals(endpoint.getPublications().size(),2);
  var extensions=(List<Map<String,Object>>)endpoint.getPublications().get(0).get("PostPublicationActions");
  assertEquals(((List<?>)extensions.get(0).get("PostPublicationActions")).size(),1);
  assertEquals(endpoint.AIcEffectivePublicationRetryCount(),2);
 }
}
