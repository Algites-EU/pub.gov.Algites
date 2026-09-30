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

    @Override
    public String technologyKind() {
        return "java";
    }

    @Override
    public String buildOutputType() {
        return "java_javadoc_jar";
    }

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
