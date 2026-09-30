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

    public static <T> AIcInheritedValue<T> absent() {
        return new AIcInheritedValue<>(AInInheritedValueState.ABSENT, null);
    }

    public static <T> AIcInheritedValue<T> clear() {
        return new AIcInheritedValue<>(AInInheritedValueState.CLEAR, null);
    }

    public static <T> AIcInheritedValue<T> of(T aValue) {
        return new AIcInheritedValue<>(AInInheritedValueState.VALUE, Objects.requireNonNull(aValue, "value"));
    }

    public AInInheritedValueState state() {
        return state;
    }

    public T value() {
        if (state != AInInheritedValueState.VALUE) {
            throw new IllegalStateException("Inherited value state is " + state + ", not VALUE.");
        }
        return value;
    }

    public T valueOrNull() {
        return state == AInInheritedValueState.VALUE ? value : null;
    }
}
