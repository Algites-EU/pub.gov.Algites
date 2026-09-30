package eu.algites.pltf.modustro.builder.model.technology;

/**
 * Validates cross-reference and invariant rules of a TechnologyKind definition.
 */
@FunctionalInterface
public interface AIiTechnologyKindDefinitionValidator {
    void validate(AIcTechnologyKindDefinition aDefinition);
}
