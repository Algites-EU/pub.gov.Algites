package eu.algites.pltf.modustro.builder.output.java;

import eu.algites.pltf.modustro.builder.model.output.AIcBuildOutputProductionPlan;
import eu.algites.pltf.modustro.builder.model.output.AIiBuildOutputProducer;
import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputProductionInput;
import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputProductionKind;
import eu.algites.pltf.modustro.builder.model.source.AIcPreparedSourceSet;
import java.util.Set;

/**
 * Built-in Phase-3 producer for java_javadoc_jar.
 */
public final class AIcJavaJavadocJarBuildOutputProducer implements AIiBuildOutputProducer {

    /**
     * Creates the built-in Java Javadoc JAR producer.
     */
    public AIcJavaJavadocJarBuildOutputProducer() {
    }

    /**
     * Returns the TechnologyKind identifier.
     * @return TechnologyKind identifier
     */
    @Override
    public String technologyKind() {
        return "java";
    }

    /**
     * Returns the BuildOutputType identifier.
     * @return BuildOutputType identifier
     */
    @Override
    public String buildOutputType() {
        return "java_javadoc_jar";
    }

    /**
     * Creates the portable production plan for the supplied prepared sources.
     *
     * @param aPreparedSourceSet prepared source set
     * @return build-output production plan
     */
    @Override
    public AIcBuildOutputProductionPlan createProductionPlan(AIcPreparedSourceSet aPreparedSourceSet) {
        return new AIcBuildOutputProductionPlan(
            technologyKind(),
            buildOutputType(),
            AInBuildOutputProductionKind.JAVA_JAVADOC_JAR,
            aPreparedSourceSet,
            Set.of(AInBuildOutputProductionInput.DOCUMENTATION_CLASSPATH),
            Set.of("generation_of_native_documentation")
        );
    }
}
