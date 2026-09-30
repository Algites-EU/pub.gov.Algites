package eu.algites.pltf.modustro.builder.output.java;

import eu.algites.pltf.modustro.builder.model.output.AIcBuildOutputProductionPlan;
import eu.algites.pltf.modustro.builder.model.output.AIiBuildOutputProducer;
import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputProductionKind;
import eu.algites.pltf.modustro.builder.model.source.AIcPreparedSourceSet;
import java.util.Set;

/**
 * Built-in Phase-3 producer for java_sources_jar.
 */
public final class AIcJavaSourcesJarBuildOutputProducer implements AIiBuildOutputProducer {

    @Override
    public String technologyKind() {
        return "java";
    }

    @Override
    public String buildOutputType() {
        return "java_sources_jar";
    }

    @Override
    public AIcBuildOutputProductionPlan createProductionPlan(AIcPreparedSourceSet aPreparedSourceSet) {
        return new AIcBuildOutputProductionPlan(
            technologyKind(),
            buildOutputType(),
            AInBuildOutputProductionKind.JAVA_SOURCES_JAR,
            aPreparedSourceSet,
            Set.of(),
            Set.of("source_native_processing")
        );
    }
}
