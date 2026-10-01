package eu.algites.pltf.modustro.builder.model.output;

/**
 * Built-in Phase-3 production primitives understood by the current build adapters.
 */
public enum AInBuildOutputProductionKind {
    /**
     * Java classes JAR production.
     */
    JAVA_CLASSES_JAR,
    /**
     * Java sources JAR production.
     */
    JAVA_SOURCES_JAR,
    /**
     * Java Javadoc JAR production.
     */
    JAVA_JAVADOC_JAR,
    /**
     * Python wheel production.
     */
    PYTHON_WHEEL,
    /**
     * Python source-distribution production.
     */
    PYTHON_SDIST
}
