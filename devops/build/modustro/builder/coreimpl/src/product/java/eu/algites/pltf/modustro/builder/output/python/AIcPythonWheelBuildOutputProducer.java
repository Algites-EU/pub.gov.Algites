package eu.algites.pltf.modustro.builder.output.python;

import eu.algites.pltf.modustro.builder.model.output.AIcBuildOutputProductionPlan;
import eu.algites.pltf.modustro.builder.model.output.AIiBuildOutputProducer;
import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputProductionInput;
import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputProductionKind;
import eu.algites.pltf.modustro.builder.model.source.AIcPreparedSourceSet;
import java.util.Set;

/**
 * Built-in Phase-3 producer for python_wheel.
 */
public final class AIcPythonWheelBuildOutputProducer implements AIiBuildOutputProducer {

    /**
     * Creates the built-in Python wheel producer.
     */
    public AIcPythonWheelBuildOutputProducer() {
    }

    /**
     * Returns the TechnologyKind identifier.
     * @return TechnologyKind identifier
     */
    @Override
    public String technologyKind() {
        return "python";
    }

    /**
     * Returns the BuildOutputType identifier.
     * @return BuildOutputType identifier
     */
    @Override
    public String buildOutputType() {
        return "python_wheel";
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
            AInBuildOutputProductionKind.PYTHON_WHEEL,
            aPreparedSourceSet,
            Set.of(AInBuildOutputProductionInput.PACKAGE_METADATA),
            Set.of("source_native_processing", "dependency_resolution")
        );
    }
}
