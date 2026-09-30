package eu.algites.pltf.modustro.builder.model.inheritance;

/**
 * Merges two definitions of the same keyed item.
 *
 * @param <T> item type
 */
@FunctionalInterface
public interface AIiItemMerger<T> {
    T merge(T aInheritedItem, T aLocalItem);
}
