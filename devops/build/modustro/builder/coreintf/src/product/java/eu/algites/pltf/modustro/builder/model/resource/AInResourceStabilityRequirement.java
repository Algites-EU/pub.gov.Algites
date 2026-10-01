package eu.algites.pltf.modustro.builder.model.resource;

/**
 * Declares whether endpoints for a ResourceKind use a stability discriminator.
 */
public enum AInResourceStabilityRequirement {
    /**
     * Every endpoint of the ResourceKind must declare a stability.
     */
    REQUIRED,
    /**
     * Endpoints may declare a stability when the deployment model needs one.
     */
    OPTIONAL,
    /**
     * Endpoints must not declare a stability.
     */
    FORBIDDEN
}
