package eu.algites.pltf.modustro.builder.model.inheritance;

/**
 * Distinguishes an omitted scalar, an explicit null reset, and an explicit value.
 */
public enum AInInheritedValueState {
    /**
     * No contribution is declared at the current hierarchy level.
     */
    ABSENT,
    /**
     * The inherited scalar is explicitly cleared.
     */
    CLEAR,
    /**
     * An explicit scalar value is declared.
     */
    VALUE
}
