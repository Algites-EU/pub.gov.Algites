package eu.algites.pltf.modustro.builder.model.technology;

/**
 * Validates cross-reference and invariant rules of a TechnologyKind definition.
 */
@FunctionalInterface
public interface AIiTechnologyKindDefinitionValidator {

    /**
     * Validates cross-reference and invariant rules of a TechnologyKind definition.
     *
     * @param aDefinition TechnologyKind definition to validate
     * @throws eu.algites.pltf.modustro.builder.model.AIxModelValidationException if the definition is invalid
     */
    void validate(AIcTechnologyKindDefinition aDefinition);
}
