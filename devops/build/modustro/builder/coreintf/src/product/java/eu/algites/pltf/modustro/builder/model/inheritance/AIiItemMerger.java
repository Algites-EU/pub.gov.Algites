package eu.algites.pltf.modustro.builder.model.inheritance;

/**
 * Merges two definitions of the same keyed item.
 *
 * @param <T> item type
 */
@FunctionalInterface
public interface AIiItemMerger<T> {

    /**
     * Merges inherited and local definitions of the same item identity.
     *
     * @param aInheritedItem inherited item
     * @param aLocalItem local item
     * @return merged item
     */
    T merge(T aInheritedItem, T aLocalItem);
}
