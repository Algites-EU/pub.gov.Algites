package eu.algites.pltf.modustro.builder.model.inheritance;

import java.util.Objects;

/**
 * Tri-state scalar used by hierarchical Modustro metadata.
 *
 * @param <T> scalar value type
 */
public final class AIcInheritedValue<T> {

    private final AInInheritedValueState state;
    private final T value;

    private AIcInheritedValue(AInInheritedValueState aState, T aValue) {
        state = Objects.requireNonNull(aState, "state");
        value = aValue;
    }

    /**
     * Creates an absent inheritance contribution.
 * @param <T> inherited value/item type
     * @return absent inheritance contribution
 */
    public static <T> AIcInheritedValue<T> absent() {
        return new AIcInheritedValue<>(AInInheritedValueState.ABSENT, null);
    }

    /**
     * Creates an explicit scalar clear contribution.
 * @param <T> inherited value/item type
     * @return clear inheritance contribution
 */
    public static <T> AIcInheritedValue<T> clear() {
        return new AIcInheritedValue<>(AInInheritedValueState.CLEAR, null);
    }

    /**
     * Creates an explicit inheritance contribution.
     *
 * @param <T> inherited value/item type
     * @param aValue value
     * @return inheritance contribution
 */
    public static <T> AIcInheritedValue<T> of(T aValue) {
        return new AIcInheritedValue<>(AInInheritedValueState.VALUE, Objects.requireNonNull(aValue, "value"));
    }

    /**
     * Returns the scalar inheritance state.
     * @return inheritance state
     */
    public AInInheritedValueState state() {
        return state;
    }

    /**
     * Returns the explicit scalar value.
     * @return explicit scalar value
     * @throws IllegalStateException if this contribution does not contain an explicit value
     */
    public T value() {
        if (state != AInInheritedValueState.VALUE) {
            throw new IllegalStateException("Inherited value state is " + state + ", not VALUE.");
        }
        return value;
    }

    /**
     * Returns the explicit scalar value when present.
     * @return explicit scalar value, or {@code null}
     */
    public T valueOrNull() {
        return state == AInInheritedValueState.VALUE ? value : null;
    }
}
