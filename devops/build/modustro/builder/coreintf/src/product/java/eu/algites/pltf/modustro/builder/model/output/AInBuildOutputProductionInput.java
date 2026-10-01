package eu.algites.pltf.modustro.builder.model.output;

/**
 * Logical inputs required by a built-in build-output production plan.
 */
public enum AInBuildOutputProductionInput {
    /**
     * Resolved dependency graph required for compilation.
     */
    COMPILE_DEPENDENCY_GRAPH,
    /**
     * Classpath required to generate native documentation.
     */
    DOCUMENTATION_CLASSPATH,
    /**
     * Technology-native package metadata.
     */
    PACKAGE_METADATA
}
