package eu.algites.pltf.modustro.builder.model.dependency.resolution;

import eu.algites.pltf.modustro.builder.model.dependency.AIcDependencyConstraintDefinition;
import eu.algites.pltf.modustro.builder.model.dependency.AIcDependencyDefinition;
import eu.algites.pltf.modustro.builder.model.technology.AIcTechnologyKindDefinition;
import java.util.List;

/**
 * Translates one DependencyKind x TechnologyKind pair into a technology-native logical plan.
 */
public interface AIiDependencyTechnologyHandler {

    /**
     * Returns the DependencyKind translated by this handler.
     *
     * @return DependencyKind identifier
     */
    String dependencyKind();

    /**
     * Returns the target TechnologyKind.
     *
     * @return TechnologyKind identifier
     */
    String technologyKind();

    /**
     * Translates portable dependencies and constraints into a technology-native logical plan.
     *
     * @param aDependencies effective dependencies
     * @param aConstraints effective dependency constraints
     * @param aTechnologyDefinition target TechnologyKind definition
     * @return technology-specific logical resolution plan
     */
    AIcDependencyTechnologyResolutionPlan createResolutionPlan(
        List<AIcDependencyDefinition> aDependencies,
        List<AIcDependencyConstraintDefinition> aConstraints,
        AIcTechnologyKindDefinition aTechnologyDefinition
    );
}
