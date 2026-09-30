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

    @Override
    public String technologyKind() {
        return "python";
    }

    @Override
    public String buildOutputType() {
        return "python_wheel";
    }

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
