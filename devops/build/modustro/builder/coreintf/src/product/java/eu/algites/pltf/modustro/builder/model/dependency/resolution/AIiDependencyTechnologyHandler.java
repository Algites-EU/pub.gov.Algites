package eu.algites.pltf.modustro.builder.model.dependency.resolution;

import eu.algites.pltf.modustro.builder.model.dependency.AIcDependencyConstraintDefinition;
import eu.algites.pltf.modustro.builder.model.dependency.AIcDependencyDefinition;
import eu.algites.pltf.modustro.builder.model.technology.AIcTechnologyKindDefinition;
import java.util.List;

/**
 * Translates one DependencyKind x TechnologyKind pair into a technology-native logical plan.
 */
public interface AIiDependencyTechnologyHandler {

    String dependencyKind();

    String technologyKind();

    AIcDependencyTechnologyResolutionPlan createResolutionPlan(
        List<AIcDependencyDefinition> aDependencies,
        List<AIcDependencyConstraintDefinition> aConstraints,
        AIcTechnologyKindDefinition aTechnologyDefinition
    );
}
