package eu.algites.pltf.modustro.builder.model.inheritance;

/**
 * Resolves the stable identity key of a keyed inherited item.
 *
 * @param <T> item type
 * @param <K> identity-key type
 */
@FunctionalInterface
public interface AIiItemIdentity<T, K> {
    K identityOf(T aItem);
}
