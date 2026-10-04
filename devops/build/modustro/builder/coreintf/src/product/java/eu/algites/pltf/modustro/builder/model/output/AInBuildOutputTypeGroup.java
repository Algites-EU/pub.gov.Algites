package eu.algites.pltf.modustro.builder.model.output;
import java.util.List;
/** Selectors expand to real outputs and never identify produced files. */
public enum AInBuildOutputTypeGroup {
 NATIVE_OUTPUTS("native_outputs", List.of("native_product_sources", "native_product_binaries", "native_product_documentation", "native_develop_sources", "native_develop_binaries", "native_develop_documentation")),
 NATIVE_PRODUCT_OUTPUTS("native_product_outputs", List.of("native_product_sources", "native_product_binaries", "native_product_documentation")),
 NATIVE_DEVELOP_OUTPUTS("native_develop_outputs", List.of("native_develop_sources", "native_develop_binaries", "native_develop_documentation")),
 NATIVE_SOURCES("native_sources", List.of("native_product_sources", "native_develop_sources")),
 NATIVE_BINARIES("native_binaries", List.of("native_product_binaries", "native_develop_binaries")),
 NATIVE_DOCUMENTATION("native_documentation", List.of("native_product_documentation", "native_develop_documentation"));
 private final String descriptorName; private final List<String> outputs;
 AInBuildOutputTypeGroup(String name,List<String> members){descriptorName=name;outputs=members;}
 public String descriptorName(){return descriptorName;} public List<String> outputs(){return outputs;}
}
